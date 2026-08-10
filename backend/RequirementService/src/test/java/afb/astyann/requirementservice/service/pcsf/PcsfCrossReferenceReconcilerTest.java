package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.domain.pcsf.FieldValue;
import afb.astyann.requirementservice.domain.pcsf.Pcsf;
import afb.astyann.requirementservice.domain.pcsf.PcsfApiConfig;
import afb.astyann.requirementservice.domain.pcsf.PcsfApiEndpoint;
import afb.astyann.requirementservice.domain.pcsf.PcsfEntity;
import afb.astyann.requirementservice.domain.pcsf.PcsfModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures here are the real shape observed in the stored PCSF of an approved project: modules
 * carrying ids like {@code MOD-01} while the endpoint pass emitted {@code module_1..module_5}, and
 * entity references running up to {@code entity_17} against four actual entities. Nothing resolved,
 * so every consumer downstream fell back to its own conventions.
 */
class PcsfCrossReferenceReconcilerTest {

    private final PcsfCrossReferenceReconciler reconciler = new PcsfCrossReferenceReconciler();

    private static FieldValue<String> fv(String value) {
        FieldValue<String> f = new FieldValue<>();
        f.setValue(value);
        return f;
    }

    private static PcsfModule module(String id, String name) {
        PcsfModule m = new PcsfModule();
        m.setId(id);
        m.setName(fv(name));
        return m;
    }

    private static PcsfEntity entity(String id, String name, String primaryModuleId) {
        PcsfEntity e = new PcsfEntity();
        e.setId(id);
        e.setName(fv(name));
        e.setPrimaryModuleId(primaryModuleId);
        return e;
    }

    private static PcsfApiEndpoint endpoint(String moduleId, String method, String path) {
        return PcsfApiEndpoint.builder()
                .moduleId(moduleId).httpMethod(method).path(path).build();
    }

    /** Mirrors the observed project: 3 modules, 4 entities, endpoints referencing neither. */
    private static Pcsf realWorldPcsf(List<PcsfApiEndpoint> endpoints) {
        Pcsf pcsf = new Pcsf();
        pcsf.setModules(new ArrayList<>(List.of(
                module("MOD-01", "Product Management"),
                module("MOD-02", "Stock Movements"),
                module("MOD-03", "User Management"))));
        pcsf.setEntities(new ArrayList<>(List.of(
                entity("entity_1", "User", null),
                entity("entity_2", "Category", null),
                entity("entity_3", "Product", null),
                entity("entity_4", "StockMovement", null))));
        PcsfApiConfig api = new PcsfApiConfig();
        api.setVersionPrefix("/api/v1");
        pcsf.setApiConfig(api);
        pcsf.setEndpoints(new ArrayList<>(endpoints));
        return pcsf;
    }

    @Test
    void resolvesTheModuleFromThePathWhenTheDeclaredModuleIdDoesNotExist() {
        Pcsf pcsf = realWorldPcsf(List.of(
                endpoint("module_1", "POST", "/api/v1/products"),
                endpoint("module_1", "GET", "/api/v1/products/{productId}"),
                endpoint("module_2", "POST", "/api/v1/stock-movements/entries"),
                endpoint("module_3", "GET", "/api/v1/users/{userId}")));

        reconciler.reconcileEndpoints(pcsf);

        assertThat(pcsf.getEndpoints()).extracting(PcsfApiEndpoint::getModuleId)
                .containsExactly("MOD-01", "MOD-01", "MOD-02", "MOD-03");
    }

    @Test
    void leavesAnEndpointUnassignedRatherThanGuessingWhenNoModuleMatches() {
        // The observed PCSF declared /categories and /auth endpoints for modules it never defined.
        // Attaching those to the closest-looking module would put the wrong operations on the
        // wrong controller, which is worse than leaving the gap visible.
        Pcsf pcsf = realWorldPcsf(List.of(
                endpoint("module_4", "POST", "/api/v1/categories"),
                endpoint("module_5", "POST", "/api/v1/auth/login")));

        reconciler.reconcileEndpoints(pcsf);

        assertThat(pcsf.getEndpoints()).extracting(PcsfApiEndpoint::getModuleId)
                .containsOnlyNulls();
    }

    @Test
    void aModuleIdThatAlreadyResolvesIsLeftUntouched() {
        Pcsf pcsf = realWorldPcsf(List.of(endpoint("MOD-02", "GET", "/api/v1/products")));

        assertThat(reconciler.reconcileEndpoints(pcsf)).isZero();
        // Not "corrected" to MOD-01 from the path — an explicit, resolvable declaration wins.
        assertThat(pcsf.getEndpoints().get(0).getModuleId()).isEqualTo("MOD-02");
    }

    @Test
    void clearsEntityReferencesThatPointAtNothing() {
        PcsfApiEndpoint ep = endpoint("MOD-01", "POST", "/api/v1/products");
        ep.setRequestBodyEntityId("entity_7");   // never existed
        ep.setResponseEntityId("entity_3");      // Product — real
        Pcsf pcsf = realWorldPcsf(List.of(ep));

        reconciler.reconcileEndpoints(pcsf);

        assertThat(ep.getRequestBodyEntityId()).isNull();
        assertThat(ep.getResponseEntityId()).isEqualTo("entity_3");
    }

    @Test
    void resolvesThroughAnEntityNameWhenThePathNamesTheEntityNotTheModule() {
        // /stock-movements matches the module by name here, but a path like /categories can only
        // resolve once an entity declares which module owns it.
        Pcsf pcsf = realWorldPcsf(List.of(endpoint("module_9", "GET", "/api/v1/categories")));
        pcsf.getEntities().set(1, entity("entity_2", "Category", "MOD-01"));

        reconciler.reconcileEndpoints(pcsf);

        assertThat(pcsf.getEndpoints().get(0).getModuleId()).isEqualTo("MOD-01");
    }

    @Test
    void handlesAPcsfWithNoEndpointsAtAll() {
        Pcsf pcsf = realWorldPcsf(List.of());
        assertThat(reconciler.reconcileEndpoints(pcsf)).isZero();
        assertThat(reconciler.reconcileEndpoints(null)).isZero();
    }

    @Test
    void doesNotLetAShorterModuleNameClaimALongerOnesPath() {
        Pcsf pcsf = new Pcsf();
        pcsf.setModules(new ArrayList<>(List.of(
                module("MOD-A", "Stock"),
                module("MOD-B", "Stock Movements"))));
        pcsf.setEntities(new ArrayList<>());
        PcsfApiConfig api = new PcsfApiConfig();
        api.setVersionPrefix("/api/v1");
        pcsf.setApiConfig(api);
        pcsf.setEndpoints(new ArrayList<>(List.of(endpoint("x", "GET", "/api/v1/stock-movements"))));

        reconciler.reconcileEndpoints(pcsf);

        assertThat(pcsf.getEndpoints().get(0).getModuleId()).isEqualTo("MOD-B");
    }
}
