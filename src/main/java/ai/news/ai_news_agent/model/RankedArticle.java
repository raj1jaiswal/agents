package ai.news.ai_news_agent.model;

public record RankedArticle(
        int rank,
        String title,
        String url,
        String source,
        String summary) {
}