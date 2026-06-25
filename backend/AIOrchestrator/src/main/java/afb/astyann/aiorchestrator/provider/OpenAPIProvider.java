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
public class OpenAPIProvider implements AIProvider {

    private static final String COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    @Value("${ai.provider.openai.api-key:}")
    private String apiKey;

    @Value("${ai.provider.openai.model:gpt-4o}")
    private String modelName;

    @Value("${ai.provider.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper objectMapper;

    public OpenAPIProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String prompt, ProviderConfig config) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured (OPENAI_API_KEY).");
        }
        try {
            String body = buildRequestBody(prompt, config);
            log.debug("Calling OpenAI API model={}", modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + COMPLETIONS_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("OpenAI API error status={} body={}", response.statusCode(), response.body());
                throw new RuntimeException("OpenAI API returned status " + response.statusCode());
            }

            return parseResponse(response.body());

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to call OpenAI API: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getProviderName() {
        return "openai/" + modelName;
    }

    private String buildRequestBody(String prompt, ProviderConfig config) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());

        ArrayNode messages = body.putArray("messages");

        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", config.getSystemPrompt());
        }

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        return objectMapper.writeValueAsString(body);
    }

    private String parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").get(0).path("message").path("content").asText();
    }
}
