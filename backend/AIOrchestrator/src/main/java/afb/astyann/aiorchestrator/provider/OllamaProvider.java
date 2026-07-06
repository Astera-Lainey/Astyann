package afb.astyann.aiorchestrator.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class OllamaProvider implements AIProvider {

    private final OllamaChatModel chatModel;

    @Value("${spring.ai.ollama.chat.options.model:qwen2.5-coder:7b}")
    private String modelName;

    public OllamaProvider(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String complete(String prompt, ProviderConfig config) {
        String effectiveModel = (config.getModelOverride() != null && !config.getModelOverride().isBlank())
                ? config.getModelOverride()
                : modelName;
        log.debug("Calling Ollama model={}", effectiveModel);

        List<Message> messages = new ArrayList<>();
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(config.getSystemPrompt()));
        }
        messages.add(new UserMessage(prompt));

        int maxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096;
        Prompt p = effectiveModel.equals(modelName)
                ? new Prompt(messages)
                : new Prompt(messages, OllamaChatOptions.builder()
                        .model(effectiveModel)
                        .numPredict(maxTokens)
                        .build());

        var generation = chatModel.call(p).getResult();
        if (generation == null || generation.getOutput() == null) return null;
        return generation.getOutput().getText();
    }

    @Override
    public String getProviderName() {
        return "ollama/" + modelName;
    }
}
