package ai.news.ai_news_agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class GmailConfig {

    private static final Logger log = LoggerFactory.getLogger(GmailConfig.class);

    @Bean
    @ConditionalOnProperty(name = "gmail.enabled", havingValue = "true")
    public GmailCredentials gmailCredentials(
            @Value("${gmail.username}") String username,
            @Value("${gmail.keychain-service}") String keychainService) {

        try {
            String appPassword = readFromKeychain(keychainService, username);
            return new GmailCredentials(username, appPassword);
        } catch (Exception e) {
            log.warn("Could not read Gmail app password from macOS Keychain (service='{}'); "
                    + "digest emails will be skipped. Add it with: "
                    + "security add-generic-password -a {} -s {} -w '<app-password>'",
                    keychainService, username, keychainService, e);
            return null;
        }
    }

    private String readFromKeychain(String service, String account) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "security", "find-generic-password", "-a", account, "-s", service, "-w")
                .redirectErrorStream(false)
                .start();

        String password;
        try (InputStream in = process.getInputStream()) {
            password = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || password.isEmpty()) {
            throw new IllegalStateException(
                    "macOS Keychain lookup failed for service '" + service + "', account '" + account
                            + "' (exit code " + exitCode + ")");
        }
        return password;
    }
}
