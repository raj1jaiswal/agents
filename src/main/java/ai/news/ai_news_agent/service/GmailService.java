package ai.news.ai_news_agent.service;

import ai.news.ai_news_agent.config.GmailCredentials;
import ai.news.ai_news_agent.model.RankedArticle;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);

    private final GmailCredentials credentials;
    private final String recipient;
    private final String senderName;
    private final String smtpHost;
    private final int smtpPort;

    public GmailService(Optional<GmailCredentials> credentials,
            @Value("${gmail.recipient}") String recipient,
            @Value("${gmail.sender-name}") String senderName,
            @Value("${gmail.smtp-host:smtp.gmail.com}") String smtpHost,
            @Value("${gmail.smtp-port:587}") int smtpPort) {
        this.credentials = credentials.orElse(null);
        this.recipient = recipient;
        this.senderName = senderName;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
    }

    public void sendDigestEmail(List<RankedArticle> ranked) {
        if (credentials == null) {
            log.info("Gmail credentials not available (disabled or not found in Keychain), skipping digest email");
            return;
        }

        try {
            Session session = buildSession();
            MimeMessage mimeMessage = buildMimeMessage(session, ranked);
            Transport.send(mimeMessage);
            log.info("Digest email sent to {}", recipient);
        } catch (Exception e) {
            log.error("Failed to send digest email via SMTP", e);
        }
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(credentials.username(), credentials.appPassword());
            }
        });
    }

    private MimeMessage buildMimeMessage(Session session, List<RankedArticle> ranked) throws Exception {
        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(credentials.username(), senderName));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(recipient));
        email.setSubject("AI News Digest — " + LocalDate.now());
        email.setContent(buildHtmlBody(ranked), "text/html; charset=utf-8");
        return email;
    }

    private String buildHtmlBody(List<RankedArticle> ranked) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>AI News Digest — ").append(LocalDate.now()).append("</h2>");
        if (ranked.isEmpty()) {
            sb.append("<p>No articles were ranked in this run.</p>");
            return sb.toString();
        }
        sb.append("<ol>");
        for (RankedArticle article : ranked) {
            sb.append("<li style=\"margin-bottom:12px;\">")
                    .append("<a href=\"").append(article.url()).append("\">")
                    .append(escapeHtml(article.title())).append("</a>")
                    .append(" <em>(").append(escapeHtml(article.source())).append(")</em><br/>")
                    .append(escapeHtml(article.summary()))
                    .append("</li>");
        }
        sb.append("</ol>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
