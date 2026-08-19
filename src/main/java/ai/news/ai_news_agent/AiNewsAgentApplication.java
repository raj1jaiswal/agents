package ai.news.ai_news_agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiNewsAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiNewsAgentApplication.class, args);
	}

}
