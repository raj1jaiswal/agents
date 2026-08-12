package ai.news.ai_news_agent.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.news.ai_news_agent.model.Article;
import ai.news.ai_news_agent.repository.ArticleRepository;

import java.util.List;

@Service
public class EmbeddingIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIngestionService.class);

    private final ArticleRepository articleRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public EmbeddingIngestionService(ArticleRepository articleRepository,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.articleRepository = articleRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * Embeds all unprocessed articles and stores the vectors in Chroma.
     * Returns the count of articles successfully embedded.
     */
    public int embedUnprocessedArticles() {
        List<Article> pending = articleRepository.findByProcessedFalse();
        int embeddedCount = 0;

        for (Article article : pending) {
            try {
                embedAndStore(article);
                article.setProcessed(true);
                articleRepository.save(article);
                embeddedCount++;
            } catch (Exception e) {
                // Don't let one bad article stop the whole batch
                log.warn("Failed to embed article id={} url={}: {}",
                        article.getId(), article.getUrl(), e.getMessage());
            }
        }

        log.info("Embedded {} of {} pending articles", embeddedCount, pending.size());
        return embeddedCount;
    }

    private void embedAndStore(Article article) {
        String text = article.getTitle() + "\n\n" + safe(article.getRawContent());

        Metadata metadata = new Metadata();
        metadata.put("articleId", String.valueOf(article.getId()));
        metadata.put("source", article.getSource());
        metadata.put("url", article.getUrl());
        metadata.put("publishedAt", String.valueOf(article.getPublishedAt()));

        TextSegment segment = TextSegment.from(text, metadata);
        Embedding embedding = embeddingModel.embed(segment).content();

        embeddingStore.add(embedding, segment);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}