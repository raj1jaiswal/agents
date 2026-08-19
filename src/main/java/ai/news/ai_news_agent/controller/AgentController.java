package ai.news.ai_news_agent.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.news.ai_news_agent.model.RankedArticle;
import ai.news.ai_news_agent.service.NewsAgentService;

import java.util.List;

@RestController
public class AgentController {

    private final NewsAgentService newsAgentService;

    public AgentController(NewsAgentService newsAgentService) {
        this.newsAgentService = newsAgentService;
    }

    @PostMapping("/api/agent/run")
    public List<RankedArticle> runAgent() {
        return newsAgentService.generateDailyTop20();
    }
}