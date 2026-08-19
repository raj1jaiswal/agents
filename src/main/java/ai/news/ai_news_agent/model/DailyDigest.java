package ai.news.ai_news_agent.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "daily_digest")
public class DailyDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate digestDate;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rankedArticlesJson; // JSON array of RankedArticle

    @Column(nullable = false)
    private Instant createdAt;

    protected DailyDigest() {
    }

    public DailyDigest(LocalDate digestDate, String rankedArticlesJson) {
        this.digestDate = digestDate;
        this.rankedArticlesJson = rankedArticlesJson;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDigestDate() {
        return digestDate;
    }

    public String getRankedArticlesJson() {
        return rankedArticlesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}