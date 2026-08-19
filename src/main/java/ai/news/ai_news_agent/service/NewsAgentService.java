package ai.news.ai_news_agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.news.ai_news_agent.model.Article;
import ai.news.ai_news_agent.model.DailyDigest;
import ai.news.ai_news_agent.model.RankedArticle;
import ai.news.ai_news_agent.repository.ArticleRepository;
import ai.news.ai_news_agent.repository.DailyDigestRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsAgentService {

    private static final Logger log = LoggerFactory.getLogger(NewsAgentService.class);

    // Seed queries cast a wide net across both AI and general IT news
    private static final List<String> SEED_QUERIES = List.of(
            "most significant AI news today",
            "most significant IT and technology news today");

    private static final int CANDIDATES_PER_QUERY = 25;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ArticleRepository articleRepository;
    private final DailyDigestRepository dailyDigestRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final GmailService gmailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NewsAgentService(EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ArticleRepository articleRepository,
            DailyDigestRepository dailyDigestRepository,
            ChatLanguageModel chatLanguageModel,
            GmailService gmailService) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.articleRepository = articleRepository;
        this.dailyDigestRepository = dailyDigestRepository;
        this.chatLanguageModel = chatLanguageModel;
        this.gmailService = gmailService;
    }

    public List<RankedArticle> generateDailyTop20() {
        List<Article> candidates = retrieveCandidates();
        log.info("Retrieved {} candidate articles for ranking", candidates.size());

        String prompt = buildRankingPrompt(candidates);
        String rawResponse = chatLanguageModel.generate(prompt);

        List<RankedArticle> ranked = parseRankedArticles(rawResponse);

        saveDigest(ranked);

        try {
            gmailService.sendDigestEmail(ranked);
        } catch (Exception e) {
            log.error("Failed to send digest email", e);
        }

        return ranked;
    }

    // --- Step 1: retrieve a candidate pool from Chroma across seed queries ---
    private List<Article> retrieveCandidates() {
        Set<Long> seenArticleIds = new LinkedHashSet<>();
        List<Article> candidates = new ArrayList<>();

        for (String seedQuery : SEED_QUERIES) {
            Embedding queryEmbedding = embeddingModel.embed(seedQuery).content();

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(CANDIDATES_PER_QUERY)
                    .minScore(0.0)
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                String articleIdStr = match.embedded().metadata().getString("articleId");
                if (articleIdStr == null)
                    continue;

                Long articleId = Long.valueOf(articleIdStr);
                if (seenArticleIds.add(articleId)) {
                    articleRepository.findById(articleId).ifPresent(candidates::add);
                }
            }
        }

        return candidates;
    }

    // --- Step 2: build the ranking prompt ---
    private String buildRankingPrompt(List<Article> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a news editor agent. Below is a numbered list of candidate news articles
                about AI and IT/technology. Some may cover the same event from different sources.

                Your task:
                1. Select and rank the top 20 articles by global significance and impact.
                2. Merge or drop near-duplicate stories covering the same underlying event.
                3. Write a short one-sentence summary (max 20 words) for each selected article.
                4. Return ONLY a JSON array, no other text, no markdown fences, in this exact shape:
                [
                  {"rank": 1, "title": "...", "url": "...", "source": "...", "summary": "..."}
                ]

                Candidate articles:
                """);

        int i = 1;
        for (Article a : candidates) {
            String excerpt = truncate(safe(a.getRawContent()), 200);
            sb.append(i++).append(". [").append(a.getSource()).append("] ")
                    .append(a.getTitle()).append(" — ").append(excerpt)
                    .append(" (url: ").append(a.getUrl()).append(")\n");
        }

        return sb.toString();
    }

    // --- Step 3: parse the LLM's JSON response, tolerating markdown fences ---
    private List<RankedArticle> parseRankedArticles(String rawResponse) {
        String cleaned = stripMarkdownFences(rawResponse.trim());

        try {
            return objectMapper.readValue(cleaned, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, RankedArticle.class));
        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON output, retrying once. Raw: {}", rawResponse);
            // one retry: sometimes trimming further or extracting the [...] block helps
            String extracted = extractJsonArray(cleaned);
            try {
                return objectMapper.readValue(extracted, objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, RankedArticle.class));
            } catch (Exception e2) {
                log.error("Failed to parse LLM JSON output after retry", e2);
                return List.of();
            }
        }
    }

    private String stripMarkdownFences(String s) {
        return s.replaceAll("(?s)```json", "").replaceAll("(?s)```", "").trim();
    }

    private String extractJsonArray(String s) {
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    // --- Step 4: persist the digest ---
    private void saveDigest(List<RankedArticle> ranked) {
        try {
            String json = objectMapper.writeValueAsString(ranked);
            DailyDigest digest = new DailyDigest(LocalDate.now(), json);
            dailyDigestRepository.save(digest);
        } catch (Exception e) {
            log.error("Failed to save daily digest", e);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}