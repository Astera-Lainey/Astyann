package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfBusinessRule;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfRelationship;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure derivation from the PCSF — no AI involved. These are the facts the per-module prompt
 * cannot see on its own, so getting them wrong silently degrades every generated module.
 */
class ModuleDependencyResolverTest {

    private final ModuleDependencyResolver resolver = new ModuleDependencyResolver();

    private static FieldValue<String> fv(String v) {
        return FieldValue.<String>builder().value(v).build();
    }

    private static BackendModule module(String entity, String service, String mapping) {
        return BackendModule.builder()
                .entityClassName(entity).serviceName(service).requestMapping(mapping).build();
    }

    /** Products (entity Product, module m1) one-to-many StockMovement (module m2). */
    private Pcsf twoModulePcsf() {
        return Pcsf.builder()
                .modules(List.of(
                        PcsfModule.builder().id("m1").name(fv("Products")).build(),
                        PcsfModule.builder().id("m2").name(fv("Stock Movements")).build()))
                .entities(List.of(
                        PcsfEntity.builder().id("e1").name(fv("Product")).primaryModuleId("m1").build(),
                        PcsfEntity.builder().id("e2").name(fv("StockMovement")).primaryModuleId("m2").build()))
                .relationships(List.of(PcsfRelationship.builder()
                        .id("r1").fromEntityId("e1").toEntityId("e2")
                        .cardinality(fv("one-to-many")).owningEntityId("e2").build()))
                .build();
    }

    private BackendProjection twoModuleProjection() {
        return BackendProjection.builder()
                .modules(List.of(
                        module("Product", "ProductService", "/api/v1/products"),
                        module("StockMovement", "StockMovementService", "/api/v1/stock-movements")))
                .build();
    }

    @Test
    void findsTheCollaboratingModuleAcrossARelationship() {
        var collaborators = resolver.collaboratorsFor(twoModulePcsf(), twoModuleProjection(),
                module("Product", "ProductService", "/api/v1/products"));

        assertThat(collaborators).hasSize(1);
        var c = collaborators.get(0);
        assertThat(c.entityClassName()).isEqualTo("StockMovement");
        assertThat(c.serviceName()).isEqualTo("StockMovementService");
        assertThat(c.moduleName()).isEqualTo("Stock Movements");
        assertThat(c.cardinality()).isEqualTo("one-to-many");
        assertThat(c.outgoing()).isTrue();
        // e2 owns the foreign key, so Product does not.
        assertThat(c.owningSide()).isFalse();
    }

    @Test
    void findsTheRelationshipFromTheOtherEndToo() {
        // Relationships are declared once, so the inverse side must still see its collaborator.
        var collaborators = resolver.collaboratorsFor(twoModulePcsf(), twoModuleProjection(),
                module("StockMovement", "StockMovementService", "/api/v1/stock-movements"));

        assertThat(collaborators).hasSize(1);
        assertThat(collaborators.get(0).serviceName()).isEqualTo("ProductService");
        assertThat(collaborators.get(0).outgoing()).isFalse();
        assertThat(collaborators.get(0).owningSide()).isTrue();
    }

    @Test
    void ignoresSelfReferencesAndSameModuleRelationships() {
        Pcsf pcsf = Pcsf.builder()
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Products")).build()))
                .entities(List.of(
                        PcsfEntity.builder().id("e1").name(fv("Product")).primaryModuleId("m1").build(),
                        PcsfEntity.builder().id("e2").name(fv("ProductVariant")).primaryModuleId("m1").build()))
                .relationships(List.of(
                        // A category tree: Product -> Product needs no second service.
                        PcsfRelationship.builder().id("r1").fromEntityId("e1").toEntityId("e1").build(),
                        // Same module owns both entities, so one service covers them.
                        PcsfRelationship.builder().id("r2").fromEntityId("e1").toEntityId("e2").build()))
                .build();
        BackendProjection projection = BackendProjection.builder()
                .modules(List.of(module("Product", "ProductService", "/api/v1/products")))
                .build();

        assertThat(resolver.collaboratorsFor(pcsf, projection,
                module("Product", "ProductService", "/api/v1/products"))).isEmpty();
    }

    @Test
    void findsRulesThatSpanTwoModules() {
        // moduleId names Products, affectedEntityId names StockMovement — filterBusinessRules
        // hands this rule to both modules independently, with neither told about the other.
        Pcsf pcsf = twoModulePcsf();
        pcsf.setBusinessRules(List.of(PcsfBusinessRule.builder()
                .id("br1").moduleId("m1").affectedEntityId("StockMovement")
                .description(fv("Reserving stock must record a movement"))
                .build()));

        var shared = resolver.sharedRulesFor(pcsf, twoModuleProjection(),
                module("Product", "ProductService", "/api/v1/products"));

        assertThat(shared).hasSize(1);
        assertThat(shared.get(0).description()).isEqualTo("Reserving stock must record a movement");
        assertThat(shared.get(0).otherModule()).isEqualTo("Stock Movements");
        assertThat(shared.get(0).otherService()).isEqualTo("StockMovementService");

        // ...and the far side sees the same rule pointing back.
        var farSide = resolver.sharedRulesFor(pcsf, twoModuleProjection(),
                module("StockMovement", "StockMovementService", "/api/v1/stock-movements"));
        assertThat(farSide).hasSize(1);
        assertThat(farSide.get(0).otherService()).isEqualTo("ProductService");
    }

    @Test
    void doesNotTreatASingleModuleRuleAsShared() {
        Pcsf pcsf = twoModulePcsf();
        pcsf.setBusinessRules(List.of(PcsfBusinessRule.builder()
                .id("br1").moduleId("m1").affectedEntityId("Product")
                .description(fv("Price must be positive")).build()));

        assertThat(resolver.sharedRulesFor(pcsf, twoModuleProjection(),
                module("Product", "ProductService", "/api/v1/products"))).isEmpty();
    }

    @Test
    void doesNotLeakRulesBelongingToTwoOtherModules() {
        Pcsf pcsf = Pcsf.builder()
                .modules(List.of(
                        PcsfModule.builder().id("m1").name(fv("Products")).build(),
                        PcsfModule.builder().id("m2").name(fv("Stock Movements")).build(),
                        PcsfModule.builder().id("m3").name(fv("Orders")).build()))
                .entities(List.of(
                        PcsfEntity.builder().id("e1").name(fv("Product")).primaryModuleId("m1").build(),
                        PcsfEntity.builder().id("e2").name(fv("StockMovement")).primaryModuleId("m2").build(),
                        PcsfEntity.builder().id("e3").name(fv("Order")).primaryModuleId("m3").build()))
                .businessRules(List.of(PcsfBusinessRule.builder()
                        .id("br1").moduleId("m2").affectedEntityId("Order")
                        .description(fv("Shipping an order consumes stock")).build()))
                .build();
        BackendProjection projection = BackendProjection.builder()
                .modules(List.of(
                        module("Product", "ProductService", "/api/v1/products"),
                        module("StockMovement", "StockMovementService", "/api/v1/stock-movements"),
                        module("Order", "OrderService", "/api/v1/orders")))
                .build();

        assertThat(resolver.sharedRulesFor(pcsf, projection,
                module("Product", "ProductService", "/api/v1/products"))).isEmpty();
        assertThat(resolver.sharedRulesFor(pcsf, projection,
                module("Order", "OrderService", "/api/v1/orders"))).hasSize(1);
    }

    /**
     * The other fixtures use an inventory vocabulary purely because that is what was to hand.
     * Nothing here may key off domain words — resolution is driven by the PCSF's ids and graph
     * shape alone, so an unrelated domain must behave identically.
     */
    @Test
    void resolvesAnUnrelatedDomainIdentically() {
        Pcsf clinic = Pcsf.builder()
                .modules(List.of(
                        PcsfModule.builder().id("m1").name(fv("Patients")).build(),
                        PcsfModule.builder().id("m2").name(fv("Appointments")).build()))
                .entities(List.of(
                        PcsfEntity.builder().id("e1").name(fv("Patient")).primaryModuleId("m1").build(),
                        PcsfEntity.builder().id("e2").name(fv("Appointment")).primaryModuleId("m2").build()))
                .relationships(List.of(PcsfRelationship.builder()
                        .id("r1").fromEntityId("e1").toEntityId("e2")
                        .cardinality(fv("one-to-many")).owningEntityId("e2").build()))
                .businessRules(List.of(PcsfBusinessRule.builder()
                        .id("br1").moduleId("m1").affectedEntityId("Appointment")
                        .description(fv("Discharging a patient cancels future appointments")).build()))
                .build();
        BackendProjection projection = BackendProjection.builder()
                .modules(List.of(
                        module("Patient", "PatientService", "/api/v1/patients"),
                        module("Appointment", "AppointmentService", "/api/v1/appointments")))
                .build();
        BackendModule patients = module("Patient", "PatientService", "/api/v1/patients");

        var collaborators = resolver.collaboratorsFor(clinic, projection, patients);
        assertThat(collaborators).hasSize(1);
        assertThat(collaborators.get(0).serviceName()).isEqualTo("AppointmentService");
        assertThat(collaborators.get(0).moduleName()).isEqualTo("Appointments");
        assertThat(collaborators.get(0).cardinality()).isEqualTo("one-to-many");

        var shared = resolver.sharedRulesFor(clinic, projection, patients);
        assertThat(shared).hasSize(1);
        assertThat(shared.get(0).otherService()).isEqualTo("AppointmentService");
        assertThat(shared.get(0).description()).isEqualTo("Discharging a patient cancels future appointments");
    }

    @Test
    void degradesToEmptyRatherThanThrowingOnASparsePcsf() {
        BackendModule product = module("Product", "ProductService", "/api/v1/products");
        BackendProjection projection = twoModuleProjection();

        assertThat(resolver.collaboratorsFor(null, projection, product)).isEmpty();
        assertThat(resolver.collaboratorsFor(Pcsf.builder().build(), projection, product)).isEmpty();
        assertThat(resolver.sharedRulesFor(Pcsf.builder().build(), projection, product)).isEmpty();
        // An entity the PCSF never declares must not blow up the whole injection pass.
        assertThat(resolver.collaboratorsFor(twoModulePcsf(), projection,
                module("Ghost", "GhostService", "/api/v1/ghosts"))).isEmpty();
    }
}
