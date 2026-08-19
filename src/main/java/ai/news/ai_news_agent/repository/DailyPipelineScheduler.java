package ai.news.ai_news_agent.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.news.ai_news_agent.RssFetchService.RssFetchService;
import ai.news.ai_news_agent.service.EmbeddingIngestionService;
import ai.news.ai_news_agent.service.NewsAgentService;

@Component
public class DailyPipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyPipelineScheduler.class);

    private final RssFetchService rssFetchService;
    private final EmbeddingIngestionService embeddingIngestionService;
    private final NewsAgentService newsAgentService;

    public DailyPipelineScheduler(RssFetchService rssFetchService,
                                   EmbeddingIngestionService embeddingIngestionService,
                                   NewsAgentService newsAgentService) {
        this.rssFetchService = rssFetchService;
        this.embeddingIngestionService = embeddingIngestionService;
        this.newsAgentService = newsAgentService;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")
    public void runDailyPipeline() {
        log.info("=== Daily pipeline started ===");
        runPipeline();
        log.info("=== Daily pipeline finished ===");
    }

    /**
     * Shared logic so both the scheduled job and the manual test
     * endpoint run the exact same flow.
     */
    public void runPipeline() {
        try {
            int newArticles = rssFetchService.fetchAllAndSave();
            log.info("Ingestion stage: {} new articles", newArticles);
        } catch (Exception e) {
            log.error("Ingestion stage failed", e);
        }

        try {
            int embedded = embeddingIngestionService.embedUnprocessedArticles();
            log.info("Embedding stage: {} articles embedded", embedded);
        } catch (Exception e) {
            log.error("Embedding stage failed", e);
        }

        try {
            newsAgentService.generateDailyTop20();
            log.info("Agent ranking stage: digest generated");
        } catch (Exception e) {
            log.error("Agent ranking stage failed", e);
        }
    }
}