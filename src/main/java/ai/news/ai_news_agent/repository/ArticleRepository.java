package ai.news.ai_news_agent.repository;

import ai.news.ai_news_agent.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByUrl(String url);

    List<Article> findByProcessedFalse();

    long countBySource(String source);
}