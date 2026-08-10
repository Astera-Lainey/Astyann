package afb.astyann.documentservice.service;

import afb.astyann.documentservice.dto.pcsf.PcsfView;
import afb.astyann.documentservice.service.ApiContractDeriver.DerivedEndpoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The API contract document describes the API the code generator will emit, so what this derives
 * has to match {@code ProjectionBuilder.buildModules} in the CodeGeneration service operation for
 * operation. The fixture is the declared shape observed in an approved project's stored PCSF, and
 * the expectations here are deliberately the same ones asserted by
 * {@code DeclaredEndpointProjectionTest} on the generator side.
 */
class ApiContractDeriverTest {

    private final ApiContractDeriver deriver = new ApiContractDeriver();

    private static <T> PcsfView.FieldValue<T> fv(T value) {
        PcsfView.FieldValue<T> f = new PcsfView.FieldValue<>();
        f.setValue(value);
        return f;
    }

    private static PcsfView.Endpoint ep(String moduleId, String method, String path, String operationId,
                                        String summary) {
        PcsfView.Endpoint e = new PcsfView.Endpoint();
        e.setModuleId(moduleId);
        e.setHttpMethod(method);
        e.setPath(path);
        e.setOperationId(operationId);
        e.setSummary(summary);
        e.setRequiresAuth(true);
        return e;
    }

    private static PcsfView pcsf(List<PcsfView.Endpoint> endpoints, List<String> crudOps,
                                 List<String> useCaseNames) {
        PcsfView.Module module = new PcsfView.Module();
        module.setId("MOD-01");
        module.setName(fv("Product Management"));
        if (crudOps != null) module.setCrudOperations(fv(crudOps));
        List<PcsfView.UseCase> useCases = new ArrayList<>();
        for (String name : useCaseNames) {
            PcsfView.UseCase uc = new PcsfView.UseCase();
            uc.setName(fv(name));
            useCases.add(uc);
        }
        module.setUseCases(useCases);

        PcsfView.Entity product = new PcsfView.Entity();
        product.setId("entity_3");
        product.setName(fv("Product"));
        product.setPrimaryModuleId("MOD-01");

        PcsfView.ApiConfig api = new PcsfView.ApiConfig();
        api.setVersionPrefix("/api/v1");

        PcsfView view = new PcsfView();
        view.setModules(new ArrayList<>(List.of(module)));
        view.setEntities(new ArrayList<>(List.of(product)));
        view.setApiConfig(api);
        view.setEndpoints(new ArrayList<>(endpoints));
        return view;
    }

    private static final List<PcsfView.Endpoint> OBSERVED = List.of(
            ep("MOD-01", "POST",  "/api/v1/products",                     "createProduct",       "Add a new product"),
            ep("MOD-01", "GET",   "/api/v1/products",                     "listProducts",        "Paginated product list"),
            ep("MOD-01", "GET",   "/api/v1/products/{productId}",         "getProductById",      "Product details"),
            ep("MOD-01", "PUT",   "/api/v1/products/{productId}",         "updateProduct",       "Update attributes"),
            ep("MOD-01", "PATCH", "/api/v1/products/{productId}/archive", "archiveProduct",      "Archive a product"),
            ep("MOD-01", "GET",   "/api/v1/products/export",              "exportProductsExcel", "Export to Excel"));

    @Test
    void derivesExactlyTheDeclaredOperationsWithAbsolutePaths() {
        List<DerivedEndpoint> derived = deriver.derive(pcsf(OBSERVED, null, List.of()));

        assertThat(derived).extracting(DerivedEndpoint::httpMethod, DerivedEndpoint::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST",  "/api/v1/products"),
                        org.assertj.core.groups.Tuple.tuple("GET",   "/api/v1/products"),
                        org.assertj.core.groups.Tuple.tuple("GET",   "/api/v1/products/{productId}"),
                        org.assertj.core.groups.Tuple.tuple("PUT",   "/api/v1/products/{productId}"),
                        org.assertj.core.groups.Tuple.tuple("PATCH", "/api/v1/products/{productId}/archive"),
                        org.assertj.core.groups.Tuple.tuple("GET",   "/api/v1/products/export"));
    }

    @Test
    void documentsNoDeleteWhenTheContractDeclaresNone() {
        // The generator emits no DELETE for this module, so the contract must not claim one.
        assertThat(deriver.derive(pcsf(OBSERVED, null, List.of())))
                .extracting(DerivedEndpoint::httpMethod).doesNotContain("DELETE");
    }

    @Test
    void apiCodesAreSequentialAndStable() {
        assertThat(deriver.derive(pcsf(OBSERVED, null, List.of())))
                .extracting(DerivedEndpoint::apiCode)
                .containsExactly("API-01", "API-02", "API-03", "API-04", "API-05", "API-06");
    }

    @Test
    void everyRowIsGroupedUnderItsModule() {
        assertThat(deriver.derive(pcsf(OBSERVED, null, List.of())))
                .extracting(DerivedEndpoint::group)
                .containsOnly("Product Management");
    }

    @Test
    void fallsBackToTheGeneratorsCrudConventionWhenNothingIsDeclared() {
        // Must match ProjectionBuilder's fallback exactly, or the document describes an API that
        // will not be built.
        List<DerivedEndpoint> derived = deriver.derive(pcsf(List.of(), null, List.of()));

        assertThat(derived).extracting(DerivedEndpoint::httpMethod, DerivedEndpoint::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST",   "/api/v1/product-management"),
                        org.assertj.core.groups.Tuple.tuple("GET",    "/api/v1/product-management"),
                        org.assertj.core.groups.Tuple.tuple("GET",    "/api/v1/product-management/{id}"),
                        org.assertj.core.groups.Tuple.tuple("PUT",    "/api/v1/product-management/{id}"),
                        org.assertj.core.groups.Tuple.tuple("DELETE", "/api/v1/product-management/{id}"));
    }

    @Test
    void theCrudFallbackHonoursTheModulesDeclaredOperations() {
        List<DerivedEndpoint> derived = deriver.derive(pcsf(List.of(), List.of("CREATE", "READ"), List.of()));

        assertThat(derived).extracting(DerivedEndpoint::httpMethod)
                .containsExactly("POST", "GET", "GET");
    }

    @Test
    void theCrudFallbackAddsAUseCaseActionButNotForCrudNamedUseCases() {
        List<DerivedEndpoint> derived = deriver.derive(
                pcsf(List.of(), List.of("READ"), List.of("Archive Product", "Create Product")));

        assertThat(derived).extracting(DerivedEndpoint::path)
                .contains("/api/v1/product-management/{id}/archiveProduct")
                // "Create Product" starts with a CRUD verb, so the generator does not add an action
                // endpoint for it and neither does the document.
                .doesNotContain("/api/v1/product-management/{id}/createProduct");
    }

    @Test
    void endpointsWhoseModuleWasNeverDeclaredAreLeftOutEntirely() {
        // The reconciler leaves these unassigned; the generator ignores them, so the contract must
        // not list operations no controller will expose.
        PcsfView view = pcsf(List.of(ep(null, "POST", "/api/v1/categories", "createCategory", "New category")),
                List.of("READ"), List.of());

        assertThat(deriver.derive(view)).extracting(DerivedEndpoint::path)
                .doesNotContain("/api/v1/categories");
    }

    @Test
    void unsupportedVerbsAreNotDocumented() {
        PcsfView view = pcsf(List.of(
                ep("MOD-01", "GET",   "/api/v1/products", "listProducts", "List"),
                ep("MOD-01", "TRACE", "/api/v1/products", "traceProducts", "Trace")), null, List.of());

        assertThat(deriver.derive(view)).extracting(DerivedEndpoint::httpMethod).containsExactly("GET");
    }

    @Test
    void anEmptyOrNullPcsfYieldsNothingRatherThanThrowing() {
        assertThat(deriver.derive(null)).isEmpty();
        assertThat(deriver.derive(new PcsfView())).isEmpty();
    }
}
