package afb.astyann.requirementservice.domain.pcsf;

import afb.astyann.requirementservice.domain.pcsf.enums.FieldSource;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
import afb.astyann.requirementservice.domain.pcsf.enums.RiskLevel;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonDeserialize(using = FieldValue.FieldValueDeserializer.class)
public class FieldValue<T> {
    private T value;
    private FieldSource source;
    private FieldStatus status;
    private Double confidence;
    private RiskLevel riskLevel;

    /**
     * Handles both the full object form {"value":"X","source":"...","status":"..."}
     * and the plain-value form "X" that the LLM returns in inference responses.
     */
    public static class FieldValueDeserializer extends StdDeserializer<FieldValue<?>> {

        public FieldValueDeserializer() { super(FieldValue.class); }

        @Override
        public FieldValue<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            if (node.isObject() && node.has("value")) {
                // Full FieldValue object — deserialize each field explicitly
                Object value = extractValue(node.get("value"));

                FieldSource source = null;
                if (node.has("source") && !node.get("source").isNull()) {
                    try { source = FieldSource.valueOf(node.get("source").asText()); }
                    catch (IllegalArgumentException ignored) {}
                }

                FieldStatus status = null;
                if (node.has("status") && !node.get("status").isNull()) {
                    try { status = FieldStatus.valueOf(node.get("status").asText()); }
                    catch (IllegalArgumentException ignored) {}
                }

                Double confidence = null;
                if (node.has("confidence") && !node.get("confidence").isNull()) {
                    confidence = node.get("confidence").asDouble();
                }

                RiskLevel riskLevel = null;
                if (node.has("riskLevel") && !node.get("riskLevel").isNull()) {
                    try { riskLevel = RiskLevel.valueOf(node.get("riskLevel").asText()); }
                    catch (IllegalArgumentException ignored) {}
                }

                return FieldValue.builder()
                        .value(value).source(source).status(status)
                        .confidence(confidence).riskLevel(riskLevel)
                        .build();
            }

            // Plain value form (LLM returns "name": "User" instead of "name": {"value": "User"})
            return FieldValue.builder()
                    .value(extractValue(node))
                    .source(FieldSource.AI_INFERRED)
                    .status(FieldStatus.CONFIRMED)
                    .build();
        }

        private Object extractValue(JsonNode node) {
            if (node == null || node.isNull()) return null;
            if (node.isTextual())             return node.asText();
            if (node.isBoolean())             return node.asBoolean();
            if (node.isNumber())              return node.numberValue();
            if (node.isArray()) {
                List<Object> list = new ArrayList<>();
                node.forEach(item -> list.add(
                        item.isTextual()  ? item.asText()    :
                        item.isBoolean()  ? item.asBoolean() :
                        item.isNumber()   ? item.numberValue() :
                        item.toString()
                ));
                return list;
            }
            return node.toString();
        }
    }
}
