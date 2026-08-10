package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfApiConfig;
import afb.astyann.codegeneration.domain.pcsf.PcsfApiEndpoint;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfProject;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts this service's derivation against the fixture shared with DocumentService.
 *
 * <p>{@code backend/shared-test-fixtures/api-contract-derivation.json} is read by a matching test
 * on the document side. The generator works in controller-relative paths, so each endpoint's path
 * is joined back onto the module's {@code requestMapping} before comparing — which also proves
 * that split reassembles into the absolute path the contract document publishes.
 */
class SharedApiDerivationContractTest {

    private static final Path FIXTURE =
            Path.of("..", "shared-test-fixtures", "api-contract-derivation.json");

    private final ProjectionBuilder builder = new ProjectionBuilder();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode fixture() throws Exception {
        assertThat(FIXTURE).as("shared fixture must exist — it is the contract between the two services")
                .exists();
        return mapper.readTree(Files.readString(FIXTURE));
    }

    private static <T> FieldValue<T> fv(T value) {
        FieldValue<T> f = new FieldValue<>();
        f.setValue(value);
        return f;
    }

    private Pcsf pcsfFrom(JsonNode spec, boolean withDeclaredEndpoints) {
        PcsfProject project = new PcsfProject();
        project.setName(fv("Inventory Management"));

        PcsfModule module = new PcsfModule();
        module.setId(spec.get("moduleId").asText());
        module.setName(fv(spec.get("moduleName").asText()));
        if (spec.has("crudOperations")) {
            List<String> ops = new ArrayList<>();
            spec.get("crudOperations").forEach(o -> ops.add(o.asText()));
            module.setCrudOperations(fv(ops));
        }

        PcsfAttribute name = new PcsfAttribute();
        name.setId("attr_1");
        name.setName(fv("name"));
        name.setJavaType(fv("String"));

        PcsfEntity entity = new PcsfEntity();
        entity.setId("entity_1");
        entity.setName(fv(spec.get("entityName").asText()));
        entity.setPrimaryModuleId(spec.get("moduleId").asText());
        entity.setAttributes(new ArrayList<>(List.of(name)));

        PcsfApiConfig api = new PcsfApiConfig();
        api.setVersionPrefix(spec.get("versionPrefix").asText());

        List<PcsfApiEndpoint> endpoints = new ArrayList<>();
        if (withDeclaredEndpoints) {
            for (JsonNode node : spec.get("endpoints")) {
                endpoints.add(PcsfApiEndpoint.builder()
                        .moduleId(spec.get("moduleId").asText())
                        .httpMethod(node.get("httpMethod").asText())
                        .path(node.get("path").asText())
                        .operationId(node.get("operationId").asText())
                        .paginated(node.path("paginated").asBoolean(false))
                        .requiresAuth(true)
                        .requiredRoles(new ArrayList<>())
                        .build());
            }
        }

        Pcsf pcsf = new Pcsf();
        pcsf.setProject(project);
        pcsf.setModules(new ArrayList<>(List.of(module)));
        pcsf.setEntities(new ArrayList<>(List.of(entity)));
        pcsf.setApiConfig(api);
        pcsf.setEndpoints(endpoints);
        return pcsf;
    }

    private static List<String> expected(JsonNode spec) {
        List<String> out = new ArrayList<>();
        spec.get("expectedOperations").forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Rebuilds the absolute path the controller will actually serve. */
    private List<String> actual(Pcsf pcsf) {
        BackendModule module = builder.buildBackendProjection(pcsf).getModules().get(0);
        return module.getEndpoints().stream()
                .map(e -> e.getHttpMethod() + " " + module.getRequestMapping() + e.getPath())
                .toList();
    }

    @Test
    void theGeneratorEmitsTheSameOperationsTheContractDocumentDescribes() throws Exception {
        JsonNode spec = fixture().get("declared");

        assertThat(actual(pcsfFrom(spec, true))).containsExactlyElementsOf(expected(spec));
    }

    @Test
    void theCrudFallbackAlsoMatchesTheContractDocument() throws Exception {
        JsonNode spec = fixture().get("crudFallback");

        assertThat(actual(pcsfFrom(spec, false))).containsExactlyElementsOf(expected(spec));
    }
}
