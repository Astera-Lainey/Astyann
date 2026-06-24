package afb.astyann.aiorchestrator.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Slf4j
public class AnthropicProvider implements AIProvider {

    private static final String MESSAGES_ENDPOINT = "/v1/messages";
    private static final String ANTHROPIC_VERSION  = "2023-06-01";

    @Value("${ai.provider.anthropic.api-key:}")
    private String apiKey;

    @Value("${ai.provider.anthropic.model:claude-sonnet-4-6}")
    private String modelName;

    @Value("${ai.provider.anthropic.base-url:https://api.anthropic.com}")
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper objectMapper;

    public AnthropicProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String prompt, ProviderConfig config) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Anthropic API key is not configured (ANTHROPIC_API_KEY).");
        }
        try {
            String body = buildRequestBody(prompt, config);
            log.debug("Calling Anthropic API model={}", modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + MESSAGES_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Anthropic API error status={} body={}", response.statusCode(), response.body());
                throw new RuntimeException("Anthropic API returned status " + response.statusCode());
            }

            return parseResponse(response.body());

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to call Anthropic API: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic/" + modelName;
    }

    private String buildRequestBody(String prompt, ProviderConfig config) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("max_tokens", config.getMaxTokens());

        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            body.put("system", config.getSystemPrompt());
        }

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        return objectMapper.writeValueAsString(body);
    }

    private String parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("content").get(0).path("text").asText();
    }
}
