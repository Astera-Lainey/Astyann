package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfApiConfig;
import afb.astyann.codegeneration.domain.pcsf.PcsfApiEndpoint;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfProject;
import afb.astyann.codegeneration.domain.projection.FrontendModule;
import afb.astyann.codegeneration.domain.projection.FrontendProjection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generated Angular is only compiled when {@code codegen.validate.frontend.enabled} is on, so a
 * mismatch between what the generated service declares and what the generated component calls
 * reaches the user as a {@code tsc} failure rather than a test failure. These check the two
 * against each other directly.
 *
 * <p>The specific break: making the service's custom-action parameter conditional on the endpoint
 * declaring a path variable, while the list component went on passing {@code id} unconditionally —
 * {@code TS2554: Expected 0 arguments, but got 1} for every collection-level action.
 */
class FrontendCustomActionTest {

    private final ProjectionBuilder builder = new ProjectionBuilder();
    private final MustacheEngine mustache = new MustacheEngine();

    private static <T> FieldValue<T> fv(T value) {
        FieldValue<T> f = new FieldValue<>();
        f.setValue(value);
        return f;
    }

    private static PcsfApiEndpoint ep(String method, String path, String operationId) {
        return PcsfApiEndpoint.builder()
                .moduleId("MOD-01").httpMethod(method).path(path).operationId(operationId)
                .requiresAuth(true).requiredRoles(new ArrayList<>()).build();
    }

    private Pcsf pcsf(List<PcsfApiEndpoint> endpoints) {
        PcsfProject project = new PcsfProject();
        project.setName(fv("Inventory"));

        PcsfModule module = new PcsfModule();
        module.setId("MOD-01");
        module.setName(fv("Reporting Hub"));

        PcsfAttribute name = new PcsfAttribute();
        name.setId("attr_1");
        name.setName(fv("label"));
        name.setJavaType(fv("String"));

        PcsfEntity entity = new PcsfEntity();
        entity.setId("entity_1");
        entity.setName(fv("Report"));
        entity.setPrimaryModuleId("MOD-01");
        entity.setAttributes(new ArrayList<>(List.of(name)));

        PcsfApiConfig api = new PcsfApiConfig();
        api.setVersionPrefix("/api/v1");

        Pcsf pcsf = new Pcsf();
        pcsf.setProject(project);
        pcsf.setModules(new ArrayList<>(List.of(module)));
        pcsf.setEntities(new ArrayList<>(List.of(entity)));
        pcsf.setApiConfig(api);
        pcsf.setEndpoints(new ArrayList<>(endpoints));
        return pcsf;
    }

    /** Endpoints sharing no resource root — the shape that collapsed the module name to "v1". */
    private static final List<PcsfApiEndpoint> HETEROGENEOUS = List.of(
            ep("GET",  "/api/v1/dashboard",        "viewDashboard"),
            ep("GET",  "/api/v1/reports/stock",    "generateStockReport"),
            ep("POST", "/api/v1/reports/{id}/pin", "pinReport"));

    private Map<String, Object> model(Pcsf pcsf) {
        FrontendProjection projection = builder.buildFrontendProjection(pcsf);
        FrontendModule module = projection.getModules().get(0);
        Map<String, Object> model = new HashMap<>();
        model.put("project", projection.getProjectInfo());
        model.put("entities", projection.getEntities());
        model.put("modules", projection.getModules());
        model.put("navigation", projection.getNavigation());
        model.put("module", module);
        model.put("entity", projection.getEntities().get(0));
        return model;
    }

    @Test
    void everyServiceCallInTheListComponentMatchesTheServicesArity() {
        Map<String, Object> model = model(pcsf(HETEROGENEOUS));
        String service = mustache.render("frontend/service.ts.mustache", model);
        String component = mustache.render("frontend/list.component.ts.mustache", model);

        // Collect each generated service method and whether it declares a parameter.
        Map<String, Boolean> takesArgument = new HashMap<>();
        Matcher declared = Pattern.compile("(?m)^\\s{2}(\\w+)\\(([^)]*)\\)\\s*:\\s*Observable").matcher(service);
        while (declared.find()) {
            takesArgument.put(declared.group(1), !declared.group(2).isBlank());
        }
        assertThat(takesArgument).isNotEmpty();

        Matcher called = Pattern.compile("this\\.service\\.(\\w+)\\(([^)]*)\\)").matcher(component);
        int checked = 0;
        while (called.find()) {
            String method = called.group(1);
            boolean passesArgument = !called.group(2).isBlank();
            assertThat(takesArgument).containsKey(method);
            assertThat(passesArgument)
                    .as("component calls %s(%s) but the service declares it with%s a parameter",
                            method, called.group(2), takesArgument.get(method) ? "" : "out")
                    .isEqualTo(takesArgument.get(method));
            checked++;
        }
        assertThat(checked).as("no service calls found to check").isGreaterThan(0);
    }

    @Test
    void aCollectionLevelActionIsCalledWithNoArgument() {
        String component = mustache.render("frontend/list.component.ts.mustache", model(pcsf(HETEROGENEOUS)));

        assertThat(component).contains("this.service.viewDashboard()")
                .contains("this.service.generateStockReport()")
                // ...while an action whose path declares a variable still receives it.
                .contains("this.service.pinReport(id)");
    }

    @Test
    void theActionUsesTheVerbTheContractDeclares() {
        String service = mustache.render("frontend/service.ts.mustache", model(pcsf(List.of(
                ep("PATCH", "/api/v1/reports/{id}/archive", "archiveReport")))));

        assertThat(service).contains("this.http.patch<");
    }

    @Test
    void aModuleWhoseEndpointsShareNoResourceRootKeepsItsOwnName() {
        // Deriving the component prefix from the request mapping's last segment produced "v1" here,
        // giving features/v1/v1-list.component.ts for a module called Reporting Hub.
        FrontendModule module = builder.buildFrontendProjection(pcsf(HETEROGENEOUS)).getModules().get(0);

        assertThat(module.getComponentPrefix()).isEqualTo("reporting-hub");
        assertThat(module.getServiceFileName()).isEqualTo("reporting-hub");
        assertThat(module.getServiceName()).isEqualTo("ReportingHubService");
    }

    @Test
    void theConventionPathKeepsTheNamesItAlwaysHad() {
        // No declared endpoints — the module name still drives the prefix, unchanged from before.
        FrontendModule module = builder.buildFrontendProjection(pcsf(List.of())).getModules().get(0);

        assertThat(module.getComponentPrefix()).isEqualTo("reporting-hub");
    }
}
