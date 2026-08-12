package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfProject;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.BackendField;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A PCSF can declare an entity's primary key one way and the foreign keys pointing at it another —
 * the two come from different parts of the model and nothing reconciles them. The observed case had
 * every {@code id} attribute declared {@code Long} while {@code primaryKeyStrategy} said
 * {@code UUID}, producing entities with a {@code UUID} key and {@code Long} foreign keys.
 *
 * <p>That does not merely look wrong: {@code productRepository.findById(stock.getProductId())} does
 * not compile, and the AI fix loop cannot repair it because the mismatch spans two files. It halted
 * with "contradictory errors, fixes are undoing each other", which was the correct diagnosis.
 */
class ForeignKeyTypeAlignmentTest {

    private final ProjectionBuilder builder = new ProjectionBuilder();

    private static <T> FieldValue<T> fv(T value) {
        FieldValue<T> f = new FieldValue<>();
        f.setValue(value);
        return f;
    }

    private static PcsfAttribute attr(String name, String javaType) {
        PcsfAttribute a = new PcsfAttribute();
        a.setId("attr_" + name);
        a.setName(fv(name));
        a.setJavaType(fv(javaType));
        return a;
    }

    private static PcsfEntity entity(String id, String name, String strategy, List<PcsfAttribute> attrs) {
        PcsfEntity e = new PcsfEntity();
        e.setId(id);
        e.setName(fv(name));
        e.setPrimaryModuleId("MOD-01");
        e.setPrimaryKeyStrategy(strategy);
        e.setAttributes(new ArrayList<>(attrs));
        return e;
    }

    private static Pcsf pcsf(List<PcsfEntity> entities) {
        PcsfProject project = new PcsfProject();
        project.setName(fv("Any"));
        PcsfModule module = new PcsfModule();
        module.setId("MOD-01");
        module.setName(fv("Stock Management"));

        Pcsf pcsf = new Pcsf();
        pcsf.setProject(project);
        pcsf.setModules(new ArrayList<>(List.of(module)));
        pcsf.setEntities(new ArrayList<>(entities));
        return pcsf;
    }

    private BackendProjection build(List<PcsfEntity> entities) {
        return builder.buildBackendProjection(pcsf(entities));
    }

    private static BackendEntity named(BackendProjection p, String className) {
        return p.getEntities().stream().filter(e -> className.equals(e.getClassName()))
                .findFirst().orElseThrow();
    }

    private static String typeOf(BackendEntity entity, String fieldName) {
        return entity.getFields().stream().filter(f -> fieldName.equals(f.getName()))
                .map(BackendField::getJavaType).findFirst().orElseThrow();
    }

    /** The exact observed shape: strategy says UUID, every declared id attribute says Long. */
    private static List<PcsfEntity> observedProject() {
        return List.of(
                entity("entity_1", "Product", "UUID", List.of(
                        attr("id", "Long"), attr("name", "String"), attr("categoryId", "Long"))),
                entity("entity_2", "Category", "UUID", List.of(
                        attr("id", "Long"), attr("name", "String"), attr("parentCategoryId", "Long"))),
                entity("entity_3", "Stock", "UUID", List.of(
                        attr("id", "Long"), attr("quantity", "Integer"), attr("productId", "Long"))));
    }

    @Test
    void theDeclaredIdAttributeWinsOverAStrategyNobodySet() {
        // primaryKeyStrategy is not in the schema the inference pass fills in — it is a DTO field
        // initialiser re-serialised on every pass, so "UUID" is present whether or not it was chosen.
        BackendProjection projection = build(observedProject());

        assertThat(named(projection, "Product").getIdType()).isEqualTo("Long");
        assertThat(named(projection, "Product").getIdStrategy()).isEqualTo("IDENTITY");
    }

    @Test
    void everyForeignKeyMatchesThePrimaryKeyItPointsAt() {
        BackendProjection projection = build(observedProject());

        assertThat(typeOf(named(projection, "Stock"), "productId"))
                .isEqualTo(named(projection, "Product").getIdType());
        assertThat(typeOf(named(projection, "Product"), "categoryId"))
                .isEqualTo(named(projection, "Category").getIdType());
    }

    @Test
    void aQualifiedForeignKeyNameStillResolvesToItsEntity() {
        // parentCategoryId names Category, not an entity called ParentCategory.
        BackendProjection projection = build(observedProject());

        assertThat(typeOf(named(projection, "Category"), "parentCategoryId")).isEqualTo("Long");
    }

    @Test
    void aForeignKeyIsRetypedEvenWhenTheTargetKeepsAUuidKey() {
        // The mismatch that produced the compile error: UUID key, Long foreign key.
        List<PcsfEntity> entities = List.of(
                entity("entity_1", "Product", "UUID", List.of(attr("name", "String"))),
                entity("entity_2", "Stock", "UUID", List.of(attr("productId", "Long"))));

        BackendProjection projection = build(entities);

        assertThat(named(projection, "Product").getIdType()).isEqualTo("UUID");
        assertThat(typeOf(named(projection, "Stock"), "productId")).isEqualTo("UUID");
    }

    @Test
    void aFieldNamingNoDeclaredEntityIsLeftExactlyAsDeclared() {
        // AuditLog.entityId is a polymorphic reference and taxId is not a reference at all —
        // guessing at either would be worse than leaving the declaration alone.
        List<PcsfEntity> entities = List.of(
                entity("entity_1", "Product", "UUID", List.of(attr("name", "String"))),
                entity("entity_2", "AuditLog", "UUID", List.of(
                        attr("entityId", "Long"), attr("taxId", "String"))));

        BackendProjection projection = build(entities);

        assertThat(typeOf(named(projection, "AuditLog"), "entityId")).isEqualTo("Long");
        assertThat(typeOf(named(projection, "AuditLog"), "taxId")).isEqualTo("String");
    }

    @Test
    void aSampleValueIsRegeneratedForTheNewTypeSoGeneratedTestsStillCompile() {
        BackendProjection projection = build(List.of(
                entity("entity_1", "Product", "UUID", List.of(attr("name", "String"))),
                entity("entity_2", "Stock", "UUID", List.of(attr("productId", "Long")))));

        BackendField fk = named(projection, "Stock").getFields().stream()
                .filter(f -> "productId".equals(f.getName())).findFirst().orElseThrow();

        assertThat(fk.getJavaType()).isEqualTo("UUID");
        // A Long literal left behind here would not be assignable to a UUID field.
        assertThat(fk.getSampleValue()).satisfiesAnyOf(
                v -> assertThat(v).isNull(),
                v -> assertThat(v).contains("UUID"));
    }
}
