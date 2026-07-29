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

    @Value("${spring.ai.ollama.chat.options.model:minimax-m3:cloud}")
    private String modelName;

    /** Output-token ceiling ({@code numPredict}) used when the caller did not set one. */
    @Value("${ai.ollama.num-predict:16384}")
    private int defaultNumPredict;

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

        int maxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : defaultNumPredict;
        Prompt p = new Prompt(messages, OllamaChatOptions.builder()
                .model(effectiveModel)
                .numPredict(maxTokens)
                .build());

        var generation = chatModel.call(p).getResult();
        if (generation == null || generation.getOutput() == null) {
            log.warn("Ollama model={} returned no generation (numPredict={})", effectiveModel, maxTokens);
            return null;
        }
        String text = generation.getOutput().getText();
        String finishReason = generation.getMetadata() != null
                ? generation.getMetadata().getFinishReason() : null;
        if (text == null || text.isBlank()) {
            // Empty output usually means the token budget was consumed before a final answer was
            // emitted — common with reasoning models. finishReason ("length" vs "stop") tells which:
            //  - "length": raise ai.ollama.num-predict, OR use a lighter/non-reasoning model.
            //  - "stop" with empty text: the model produced only reasoning; try a code-focused model.
            log.warn("Ollama model={} returned EMPTY text (finishReason={}, numPredict={}). "
                    + "Raise ai.ollama.num-predict or switch to a code-focused model.",
                    effectiveModel, finishReason, maxTokens);
        } else {
            log.debug("Ollama model={} produced {} chars (finishReason={}, numPredict={})",
                    effectiveModel, text.length(), finishReason, maxTokens);
        }
        return text;
    }

    @Override
    public String getProviderName() {
        return "ollama/" + modelName;
    }
}
