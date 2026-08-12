package ai.news.ai_news_agent.RssFetchService;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import ai.news.ai_news_agent.model.Article;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class RssFetchService {

    private static final Logger log = LoggerFactory.getLogger(RssFetchService.class);

    // Add / edit feed URLs here as you find better ones
    private static final List<FeedSource> FEEDS = List.of(
            new FeedSource("TechCrunch", "https://techcrunch.com/feed/"),
            new FeedSource("The Verge", "https://www.theverge.com/rss/index.xml"),
            new FeedSource("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"),
            new FeedSource("Hacker News", "https://hnrss.org/frontpage"),
            new FeedSource("Wired", "https://www.wired.com/feed/rss"));

    private final ai.news.ai_news_agent.repository.ArticleRepository articleRepository;

    public RssFetchService(ai.news.ai_news_agent.repository.ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * Fetches all configured RSS feeds, dedupes against existing URLs,
     * and persists new articles. Returns the count of newly saved articles.
     */
    public int fetchAllAndSave() {
        int savedCount = 0;

        for (FeedSource feedSource : FEEDS) {
            try {
                savedCount += fetchAndSaveOne(feedSource);
            } catch (Exception e) {
                // One broken feed should never kill the whole ingestion run
                log.warn("Failed to fetch feed [{}]: {}", feedSource.name(), e.getMessage());
            }
        }

        return savedCount;
    }

    private int fetchAndSaveOne(FeedSource feedSource) throws Exception {
        int saved = 0;

        try (XmlReader reader = new XmlReader(new URL(feedSource.url()))) {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(reader);

            for (SyndEntry entry : feed.getEntries()) {
                String link = entry.getLink();
                if (link == null || link.isBlank()) {
                    continue;
                }
                if (articleRepository.existsByUrl(link)) {
                    continue; // dedup
                }

                String title = entry.getTitle() != null ? entry.getTitle() : "(untitled)";
                String content = entry.getDescription() != null
                        ? entry.getDescription().getValue()
                        : "";
                Instant publishedAt = entry.getPublishedDate() != null
                        ? toInstant(entry.getPublishedDate())
                        : Instant.now();

                Article article = new Article(title, link, feedSource.name(), publishedAt, content);
                articleRepository.save(article);
                saved++;
            }
        }

        log.info("Fetched feed [{}]: {} new articles saved", feedSource.name(), saved);
        return saved;
    }

    private Instant toInstant(Date date) {
        return date.toInstant();
    }

    private record FeedSource(String name, String url) {
    }
}