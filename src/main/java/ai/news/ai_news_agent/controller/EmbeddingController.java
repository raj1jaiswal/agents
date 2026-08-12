package ai.news.ai_news_agent.controller;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.news.ai_news_agent.service.EmbeddingIngestionService;

import java.util.List;
import java.util.Map;

@RestController
public class EmbeddingController {

    private final EmbeddingIngestionService embeddingIngestionService;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public EmbeddingController(EmbeddingIngestionService embeddingIngestionService,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingIngestionService = embeddingIngestionService;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    @PostMapping("/api/embed/run")
    public Map<String, Object> runEmbedding() {
        int count = embeddingIngestionService.embedUnprocessedArticles();
        return Map.of("status", "completed", "articlesEmbedded", count);
    }

    // Throwaway test endpoint to verify semantic search works, e.g.:
    // curl "localhost:8081/api/embed/search?query=AI regulation"
    @GetMapping("/api/embed/search")
    public List<Map<String, Object>> testSearch(@RequestParam String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.0)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);

        return result.matches().stream()
                .map(this::toResultMap)
                .toList();
    }

    private Map<String, Object> toResultMap(EmbeddingMatch<TextSegment> match) {
        return Map.of(
                "score", match.score(),
                "source", match.embedded().metadata().getString("source"),
                "url", match.embedded().metadata().getString("url"),
                "textPreview", match.embedded().text().substring(0, Math.min(120, match.embedded().text().length())));
    }
}
