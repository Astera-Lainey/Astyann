package afb.astyann.documentservice.service;

import afb.astyann.documentservice.dto.pcsf.PcsfView;
import afb.astyann.documentservice.service.ApiContractDeriver.DerivedEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts this service's derivation against the fixture shared with CodeGeneration.
 *
 * <p>{@code backend/shared-test-fixtures/api-contract-derivation.json} is read by a matching test
 * on the generator side. Two hand-maintained expectation lists would drift the same way the two
 * implementations did; one file that both must satisfy is what actually holds them together.
 */
class SharedApiDerivationContractTest {

    private static final Path FIXTURE =
            Path.of("..", "shared-test-fixtures", "api-contract-derivation.json");

    private final ApiContractDeriver deriver = new ApiContractDeriver();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode fixture() throws Exception {
        assertThat(FIXTURE).as("shared fixture must exist — it is the contract between the two services")
                .exists();
        return mapper.readTree(Files.readString(FIXTURE));
    }

    private static <T> PcsfView.FieldValue<T> fv(T value) {
        PcsfView.FieldValue<T> f = new PcsfView.FieldValue<>();
        f.setValue(value);
        return f;
    }

    private PcsfView viewFrom(JsonNode spec, boolean withDeclaredEndpoints) {
        PcsfView.Module module = new PcsfView.Module();
        module.setId(spec.get("moduleId").asText());
        module.setName(fv(spec.get("moduleName").asText()));
        if (spec.has("crudOperations")) {
            List<String> ops = new ArrayList<>();
            spec.get("crudOperations").forEach(o -> ops.add(o.asText()));
            module.setCrudOperations(fv(ops));
        }

        PcsfView.Entity entity = new PcsfView.Entity();
        entity.setId("entity_1");
        entity.setName(fv(spec.get("entityName").asText()));
        entity.setPrimaryModuleId(spec.get("moduleId").asText());

        PcsfView.ApiConfig api = new PcsfView.ApiConfig();
        api.setVersionPrefix(spec.get("versionPrefix").asText());

        List<PcsfView.Endpoint> endpoints = new ArrayList<>();
        if (withDeclaredEndpoints) {
            for (JsonNode node : spec.get("endpoints")) {
                PcsfView.Endpoint ep = new PcsfView.Endpoint();
                ep.setModuleId(spec.get("moduleId").asText());
                ep.setHttpMethod(node.get("httpMethod").asText());
                ep.setPath(node.get("path").asText());
                ep.setOperationId(node.get("operationId").asText());
                ep.setPaginated(node.path("paginated").asBoolean(false));
                ep.setRequiresAuth(true);
                endpoints.add(ep);
            }
        }

        PcsfView view = new PcsfView();
        view.setModules(new ArrayList<>(List.of(module)));
        view.setEntities(new ArrayList<>(List.of(entity)));
        view.setApiConfig(api);
        view.setEndpoints(endpoints);
        return view;
    }

    private static List<String> expected(JsonNode spec) {
        List<String> out = new ArrayList<>();
        spec.get("expectedOperations").forEach(n -> out.add(n.asText()));
        return out;
    }

    private static List<String> actual(List<DerivedEndpoint> derived) {
        return derived.stream().map(e -> e.httpMethod() + " " + e.path()).toList();
    }

    @Test
    void theDocumentDescribesTheSameOperationsTheGeneratorWillEmit() throws Exception {
        JsonNode spec = fixture().get("declared");

        assertThat(actual(deriver.derive(viewFrom(spec, true))))
                .containsExactlyElementsOf(expected(spec));
    }

    @Test
    void theCrudFallbackAlsoMatchesTheGenerator() throws Exception {
        JsonNode spec = fixture().get("crudFallback");

        assertThat(actual(deriver.derive(viewFrom(spec, false))))
                .containsExactlyElementsOf(expected(spec));
    }
}
