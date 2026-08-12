package ai.news.ai_news_agent.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.news.ai_news_agent.RssFetchService.RssFetchService;

import java.util.Map;

@RestController
public class NewsIngestionController {

    private final RssFetchService rssFetchService;

    public NewsIngestionController(RssFetchService rssFetchService) {
        this.rssFetchService = rssFetchService;
    }

    @PostMapping("/api/ingest/run")
    public Map<String, Object> runIngestion() {
        int savedCount = rssFetchService.fetchAllAndSave();
        return Map.of(
                "status", "completed",
                "newArticlesSaved", savedCount);
    }
}
