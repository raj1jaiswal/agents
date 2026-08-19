package ai.news.ai_news_agent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ai.news.ai_news_agent.repository.DailyDigestRepository;
import ai.news.ai_news_agent.repository.DailyPipelineScheduler;

@RestController
public class DigestController {

    private final DailyDigestRepository dailyDigestRepository;
    private final DailyPipelineScheduler dailyPipelineScheduler;

    public DigestController(DailyDigestRepository dailyDigestRepository,
            DailyPipelineScheduler dailyPipelineScheduler) {
        this.dailyDigestRepository = dailyDigestRepository;
        this.dailyPipelineScheduler = dailyPipelineScheduler;
    }

    @GetMapping("/api/digest/latest")
    public ResponseEntity<String> getLatestDigest() {
        return dailyDigestRepository.findTopByOrderByCreatedAtDesc()
                .map(d -> ResponseEntity.ok(d.getRankedArticlesJson()))
                .orElse(ResponseEntity.noContent().build());
    }

    // Manual trigger so you don't have to wait for 7 AM IST to test
    @PostMapping("/api/pipeline/run-now")
    public String runNow() {
        dailyPipelineScheduler.runPipeline();
        return "Pipeline run completed";
    }
}