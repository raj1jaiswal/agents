package ai.news.ai_news_agent.repository;

import ai.news.ai_news_agent.model.DailyDigest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DailyDigestRepository extends JpaRepository<DailyDigest, Long> {
    Optional<DailyDigest> findTopByOrderByCreatedAtDesc();
}