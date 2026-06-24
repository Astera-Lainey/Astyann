package afb.astyann.aiorchestrator.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class OllamaProvider implements AIProvider {

    private final OllamaChatModel chatModel;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String modelName;

    public OllamaProvider(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String complete(String prompt, ProviderConfig config) {
        log.debug("Calling Ollama model={}", modelName);

        List<Message> messages = new ArrayList<>();
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(config.getSystemPrompt()));
        }
        messages.add(new UserMessage(prompt));

        return chatModel.call(new Prompt(messages))
                .getResult()
                .getOutput()
                .getText();
    }

    @Override
    public String getProviderName() {
        return "ollama/" + modelName;
    }
}
