package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfApiConfig;
import afb.astyann.codegeneration.domain.pcsf.PcsfApiEndpoint;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfProject;
import afb.astyann.codegeneration.domain.projection.BackendEndpoint;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generated controllers used to ignore {@code pcsf.endpoints} entirely and emit a fixed CRUD
 * surface instead, so a PCSF declaring {@code PATCH /products/{productId}/archive} and no delete at
 * all still produced {@code DELETE /products/{id}} and nothing else. These pin the declared
 * contract as the thing that drives the controller.
 *
 * <p>The fixture is the real declared shape from an approved project — including the parts that
 * make it awkward: entity-named path variables, sub-resource actions, an export, and a PATCH.
 */
class DeclaredEndpointProjectionTest {

    private final ProjectionBuilder builder = new ProjectionBuilder();

    private static <T> FieldValue<T> fv(T value) {
        FieldValue<T> f = new FieldValue<>();
        f.setValue(value);
        return f;
    }

    private static PcsfApiEndpoint ep(String moduleId, String method, String path, String operationId) {
        return PcsfApiEndpoint.builder()
                .moduleId(moduleId).httpMethod(method).path(path).operationId(operationId)
                .requiresAuth(true).requiredRoles(new ArrayList<>()).build();
    }

    /** One module ("Product Management" → Product) with the declared endpoints observed in the DB. */
    private static Pcsf productPcsf(List<PcsfApiEndpoint> endpoints) {
        PcsfProject project = new PcsfProject();
        project.setName(fv("Inventory Management"));

        PcsfModule module = new PcsfModule();
        module.setId("MOD-01");
        module.setName(fv("Product Management"));

        PcsfAttribute name = new PcsfAttribute();
        name.setId("attr_1");
        name.setName(fv("name"));
        name.setJavaType(fv("String"));

        PcsfEntity product = new PcsfEntity();
        product.setId("entity_3");
        product.setName(fv("Product"));
        product.setPrimaryModuleId("MOD-01");
        product.setAttributes(new ArrayList<>(List.of(name)));

        PcsfApiConfig api = new PcsfApiConfig();
        api.setVersionPrefix("/api/v1");

        Pcsf pcsf = new Pcsf();
        pcsf.setProject(project);
        pcsf.setModules(new ArrayList<>(List.of(module)));
        pcsf.setEntities(new ArrayList<>(List.of(product)));
        pcsf.setApiConfig(api);
        pcsf.setEndpoints(new ArrayList<>(endpoints));
        return pcsf;
    }

    private BackendModule firstModule(Pcsf pcsf) {
        BackendProjection projection = builder.buildBackendProjection(pcsf);
        assertThat(projection.getModules()).isNotEmpty();
        return projection.getModules().get(0);
    }

    private static final List<PcsfApiEndpoint> OBSERVED = List.of(
            ep("MOD-01", "POST",   "/api/v1/products",                        "createProduct"),
            ep("MOD-01", "GET",    "/api/v1/products",                        "listProducts"),
            ep("MOD-01", "GET",    "/api/v1/products/{productId}",            "getProductById"),
            ep("MOD-01", "PUT",    "/api/v1/products/{productId}",            "updateProduct"),
            ep("MOD-01", "PATCH",  "/api/v1/products/{productId}/archive",    "archiveProduct"),
            ep("MOD-01", "GET",    "/api/v1/products/export",                 "exportProductsExcel"));

    @Test
    void theControllerMountsExactlyTheDeclaredOperations() {
        BackendModule module = firstModule(productPcsf(OBSERVED));

        assertThat(module.getRequestMapping()).isEqualTo("/api/v1/products");
        assertThat(module.getEndpoints())
                .extracting(BackendEndpoint::getHttpMethod, BackendEndpoint::getPath,
                            BackendEndpoint::getMethodName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST",  "",                       "createProduct"),
                        org.assertj.core.groups.Tuple.tuple("GET",   "",                       "listProducts"),
                        org.assertj.core.groups.Tuple.tuple("GET",   "/{productId}",           "getProductById"),
                        org.assertj.core.groups.Tuple.tuple("PUT",   "/{productId}",           "updateProduct"),
                        org.assertj.core.groups.Tuple.tuple("PATCH", "/{productId}/archive",   "archiveProduct"),
                        org.assertj.core.groups.Tuple.tuple("GET",   "/export",                "exportProductsExcel"));
    }

    @Test
    void noDeleteIsInventedWhenTheContractDeclaresNone() {
        // The old convention emitted DELETE /{id} for every module whose crudOperations included
        // DELETE, regardless of what the contract said. This project archives instead.
        BackendModule module = firstModule(productPcsf(OBSERVED));

        assertThat(module.getEndpoints()).extracting(BackendEndpoint::getHttpMethod)
                .doesNotContain("DELETE");
    }

    @Test
    void pathVariablesKeepTheNameTheContractGaveThem() {
        BackendModule module = firstModule(productPcsf(OBSERVED));

        BackendEndpoint byId = module.getEndpoints().stream()
                .filter(e -> "getProductById".equals(e.getMethodName())).findFirst().orElseThrow();
        assertThat(byId.getPathVariables()).containsExactly("productId");
        assertThat(byId.getIdVariable()).isEqualTo("productId");
        assertThat(byId.isHasPathVariable()).isTrue();
    }

    @Test
    void onlyTheCanonicalOperationsGetAGeneratedBody() {
        // Everything else must reach the stub branch so logic injection implements it, rather than
        // silently receiving a findById/delete body that has nothing to do with the operation.
        BackendModule module = firstModule(productPcsf(OBSERVED));

        assertThat(module.getEndpoints()).filteredOn(BackendEndpoint::isCrud)
                .extracting(BackendEndpoint::getMethodName)
                .containsExactlyInAnyOrder("createProduct", "listProducts", "getProductById", "updateProduct");
        assertThat(module.getEndpoints()).filteredOn(e -> !e.isCrud())
                .extracting(BackendEndpoint::getMethodName)
                .containsExactlyInAnyOrder("archiveProduct", "exportProductsExcel");
    }

    @Test
    void onlyTheBareCollectionGetIsPaged() {
        PcsfApiEndpoint list = ep("MOD-01", "GET", "/api/v1/products", "listProducts");
        list.setPaginated(true);
        PcsfApiEndpoint export = ep("MOD-01", "GET", "/api/v1/products/export", "exportProductsExcel");
        export.setPaginated(true);   // declared paginated, but it is not the collection endpoint

        BackendModule module = firstModule(productPcsf(List.of(list, export)));

        assertThat(module.getEndpoints()).filteredOn(BackendEndpoint::isPaged)
                .extracting(BackendEndpoint::getMethodName).containsExactly("listProducts");
        assertThat(module.getEndpoints()).filteredOn(BackendEndpoint::isPaged)
                .extracting(BackendEndpoint::getReturnType)
                .containsExactly("Page<ProductResponseDto>");
    }

    @Test
    void theRequestMappingStopsBeforeAPathVariableEveryEndpointShares() {
        // Folding /{productId} into @RequestMapping would leave a class-level variable that no
        // method declares a @PathVariable for, and the generated controller would not start.
        BackendModule module = firstModule(productPcsf(List.of(
                ep("MOD-01", "GET",   "/api/v1/products/{productId}",         "getProductById"),
                ep("MOD-01", "PATCH", "/api/v1/products/{productId}/archive", "archiveProduct"))));

        assertThat(module.getRequestMapping()).isEqualTo("/api/v1/products");
        assertThat(module.getEndpoints()).extracting(BackendEndpoint::getPath)
                .containsExactly("/{productId}", "/{productId}/archive");
    }

    @Test
    void fallsBackToCrudConventionsWhenTheContractDeclaresNothingForTheModule() {
        // Older PCSFs — and any module INF-4 skipped — must keep generating exactly as before.
        BackendModule module = firstModule(productPcsf(List.of()));

        assertThat(module.getRequestMapping()).isEqualTo("/api/v1/product-management");
        assertThat(module.getEndpoints()).extracting(BackendEndpoint::getMethodName)
                .contains("createProduct", "getProductById", "updateProduct", "deleteProduct");
        assertThat(module.getEndpoints()).allSatisfy(e ->
                assertThat(e.getIdVariable()).isEqualTo("id"));
    }

    @Test
    void endpointsBelongingToNoModuleAreIgnoredRatherThanMisassigned() {
        // The reconciler leaves an endpoint unassigned when its module was never declared. It must
        // not then land on an unrelated controller.
        PcsfApiEndpoint orphan = ep(null, "POST", "/api/v1/categories", "createCategory");
        BackendModule module = firstModule(productPcsf(List.of(
                ep("MOD-01", "GET", "/api/v1/products", "listProducts"), orphan)));

        assertThat(module.getEndpoints()).extracting(BackendEndpoint::getMethodName)
                .containsExactly("listProducts");
    }

    @Test
    void duplicateOperationIdsDoNotProduceTwoIdenticalSignatures() {
        BackendModule module = firstModule(productPcsf(List.of(
                ep("MOD-01", "GET",  "/api/v1/products",        "listProducts"),
                ep("MOD-01", "POST", "/api/v1/products/search", "listProducts"))));

        assertThat(module.getEndpoints()).extracting(BackendEndpoint::getMethodName)
                .containsExactly("listProducts", "listProducts2");
    }

    @Test
    void anUnsupportedVerbIsDroppedRatherThanRenderedIntoBrokenSource() {
        BackendModule module = firstModule(productPcsf(List.of(
                ep("MOD-01", "GET",     "/api/v1/products", "listProducts"),
                ep("MOD-01", "TRACE",   "/api/v1/products", "traceProducts"),
                ep("MOD-01", "OPTIONS", "/api/v1/products", "optionsProducts"))));

        assertThat(module.getEndpoints()).extracting(BackendEndpoint::getMethodName)
                .containsExactly("listProducts");
    }

    @Test
    void aPublicEndpointGetsNoRoleRestriction() {
        PcsfApiEndpoint open = ep("MOD-01", "GET", "/api/v1/products", "listProducts");
        open.setRequiresAuth(false);

        BackendModule module = firstModule(productPcsf(List.of(open)));

        assertThat(module.getEndpoints().get(0).getRoles()).isEmpty();
    }
}
