package afb.astyann.documentservice.service;

import afb.astyann.documentservice.service.ApiContractDeriver.DerivedEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Replaces the endpoint tables the model wrote with the operations the code generator will emit.
 *
 * <p>Identity is derived; prose is not. The model is genuinely good at describing what an
 * operation does, what it returns and which requirement it satisfies, and none of that is
 * recoverable from the PCSF — so authored text is carried across onto the derived row whenever the
 * two agree on verb and path, and only invented from the PCSF's own summary when they do not.
 * What the model no longer gets to decide is <em>which operations exist</em>.
 *
 * <p>Rows the model wrote that match no derived endpoint are dropped: they describe an API that
 * will not be generated, and leaving them in is what made the document unusable as a contract.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiContractOverlay {

    /** Descriptive fields worth keeping from the model when its row matches a derived one. */
    private static final List<String> DETAIL_PROSE_FIELDS = List.of(
            "pathParameters", "queryParameters", "bodyParameters", "requiredHeaders",
            "requestSchema", "responseSchema", "statusCodes", "frCovered");

    private static final List<String> TRACE_PROSE_FIELDS = List.of("frCovered", "ucCovered", "usCovered");

    /**
     * @param data      the model's JSON, mutated in place
     * @param endpoints the derived operations; nothing is changed when this is empty, so a PCSF
     *                  that could not be read leaves the document exactly as the model wrote it
     */
    public void apply(JsonNode data, List<DerivedEndpoint> endpoints) {
        if (!(data instanceof ObjectNode root) || endpoints == null || endpoints.isEmpty()) return;

        Map<String, ObjectNode> authoredEndpoints = indexByMethodAndPath(root.get("endpoint"));
        Map<String, ObjectNode> authoredDetails   = indexByMethodAndPath(root.get("endpointDetail"));
        Map<String, ObjectNode> authoredTraces    = indexByMethodAndPath(root.get("trace"));

        ArrayNode endpointRows = root.arrayNode();
        ArrayNode detailRows   = root.arrayNode();
        ArrayNode traceRows    = root.arrayNode();

        for (DerivedEndpoint ep : endpoints) {
            ObjectNode authored = authoredEndpoints.get(ep.key());

            ObjectNode row = endpointRows.addObject();
            row.put("apiCode", ep.apiCode());
            row.put("method", ep.httpMethod());
            row.put("path", ep.path());
            row.put("description", describe(ep, authored));

            ObjectNode detail = detailRows.addObject();
            detail.put("apiCode", ep.apiCode());
            detail.put("method", ep.httpMethod());
            detail.put("path", ep.path());
            ObjectNode authoredDetail = authoredDetails.get(ep.key());
            for (String field : DETAIL_PROSE_FIELDS) {
                detail.put(field, text(authoredDetail, field));
            }
            // Derived, not authored — these follow from the declaration itself.
            detail.put("security", ep.requiresAuth()
                    ? (ep.roles().isEmpty() ? "Authenticated" : "Roles: " + String.join(", ", ep.roles()))
                    : "Public");
            detail.put("idempotent", idempotent(ep.httpMethod()) ? "Yes" : "No");

            ObjectNode trace = traceRows.addObject();
            trace.put("apiCode", ep.apiCode());
            trace.put("method", ep.httpMethod());
            trace.put("path", ep.path());
            ObjectNode authoredTrace = authoredTraces.get(ep.key());
            for (String field : TRACE_PROSE_FIELDS) {
                trace.put(field, text(authoredTrace, field));
            }
        }

        int dropped = authoredEndpoints.size() - countMatched(authoredEndpoints, endpoints);
        if (dropped > 0) {
            log.info("API contract: dropped {} authored endpoint row(s) describing operations the "
                     + "generator does not emit", dropped);
        }

        root.set("endpoint", endpointRows);
        root.set("endpointDetail", detailRows);
        root.set("trace", traceRows);
        root.set("endpointGroup", groupsOf(root, endpoints));

        ObjectNode coverage = root.hasNonNull("coverageStats") && root.get("coverageStats").isObject()
                ? (ObjectNode) root.get("coverageStats")
                : root.putObject("coverageStats");
        coverage.put("totalEndpoints", String.valueOf(endpoints.size()));

        log.info("API contract: rendered {} derived endpoint(s) across {} group(s)",
                endpoints.size(), groupNames(endpoints).size());
    }

    private ArrayNode groupsOf(ObjectNode root, List<DerivedEndpoint> endpoints) {
        ArrayNode groups = root.arrayNode();
        for (String name : groupNames(endpoints)) {
            groups.addObject().put("name", name);
        }
        return groups;
    }

    private LinkedHashSet<String> groupNames(List<DerivedEndpoint> endpoints) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (DerivedEndpoint ep : endpoints) {
            if (ep.group() != null && !ep.group().isBlank()) names.add(ep.group());
        }
        return names;
    }

    private String describe(DerivedEndpoint ep, ObjectNode authored) {
        String authoredText = text(authored, "description");
        if (!authoredText.isBlank()) return authoredText;
        if (ep.summary() != null && !ep.summary().isBlank()) return ep.summary();
        return ep.operationId() == null ? "" : ep.operationId();
    }

    /** GET and DELETE are idempotent by definition; PUT is declared so by HTTP; POST and PATCH are not. */
    private boolean idempotent(String method) {
        return "GET".equals(method) || "DELETE".equals(method) || "PUT".equals(method);
    }

    private int countMatched(Map<String, ObjectNode> authored, List<DerivedEndpoint> endpoints) {
        int matched = 0;
        for (DerivedEndpoint ep : endpoints) {
            if (authored.containsKey(ep.key())) matched++;
        }
        return matched;
    }

    private Map<String, ObjectNode> indexByMethodAndPath(JsonNode array) {
        Map<String, ObjectNode> index = new LinkedHashMap<>();
        if (array == null || !array.isArray()) return index;
        for (JsonNode node : array) {
            if (!(node instanceof ObjectNode row)) continue;
            String method = text(row, "method").trim().toUpperCase(java.util.Locale.ROOT);
            String path = text(row, "path").trim();
            if (method.isEmpty() || path.isEmpty()) continue;
            index.putIfAbsent(method + " " + path, row);
        }
        return index;
    }

    private String text(ObjectNode node, String field) {
        if (node == null) return "";
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    /** Exposed for logging/tests: the keys the model produced that no derived endpoint matched. */
    public List<String> unmatchedAuthoredKeys(JsonNode data, List<DerivedEndpoint> endpoints) {
        Map<String, ObjectNode> authored = indexByMethodAndPath(data == null ? null : data.get("endpoint"));
        List<String> derivedKeys = new ArrayList<>();
        for (DerivedEndpoint ep : endpoints) derivedKeys.add(ep.key());
        List<String> unmatched = new ArrayList<>(authored.keySet());
        unmatched.removeAll(derivedKeys);
        return unmatched;
    }
}
