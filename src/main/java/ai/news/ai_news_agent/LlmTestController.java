package ai.news.ai_news_agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LlmTestController {

    @GetMapping("/test-llm")
    public String testLlm() {
        // Initialize the Ollama model. You can change "llama3" to the model you have
        // installed.
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3:latest")
                .build();

        return model.generate("Say 'Hello, World!' in a single sentence.");
    }
}
