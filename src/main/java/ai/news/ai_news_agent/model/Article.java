package ai.news.ai_news_agent.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "article", uniqueConstraints = @UniqueConstraint(columnNames = "url"))
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(nullable = false, unique = true, length = 2000)
    private String url;

    @Column(nullable = false)
    private String source;

    private Instant publishedAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rawContent;

    @Column(nullable = false)
    private Instant fetchedAt;

    @Column(nullable = false)
    private boolean processed = false;

    protected Article() {
        // JPA
    }

    public Article(String title, String url, String source, Instant publishedAt, String rawContent) {
        this.title = title;
        this.url = url;
        this.source = source;
        this.publishedAt = publishedAt;
        this.rawContent = rawContent;
        this.fetchedAt = Instant.now();
        this.processed = false;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getSource() {
        return source;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getRawContent() {
        return rawContent;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
}