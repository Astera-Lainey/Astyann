package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfConstraints;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.projection.BackendField;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfProject;
import afb.astyann.codegeneration.domain.pcsf.PcsfUseCase;
import afb.astyann.codegeneration.domain.projection.BackendEndpoint;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionBuilderTest {

    private final ProjectionBuilder builder = new ProjectionBuilder();

    private static FieldValue<String> fv(String v) {
        return FieldValue.<String>builder().value(v).build();
    }

    private Pcsf minimalPcsf() {
        PcsfEntity product = PcsfEntity.builder()
                .id("e1")
                .name(fv("Product"))
                .attributes(List.of(
                        PcsfAttribute.builder().name(fv("name")).javaType(fv("String")).build(),
                        // "id" is reserved and must be filtered out of user fields
                        PcsfAttribute.builder().name(fv("id")).javaType(fv("UUID")).build()))
                .build();

        return Pcsf.builder()
                .project(PcsfProject.builder().name(fv("Inventory")).build())
                .entities(List.of(product))
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Products")).build()))
                .build();
    }

    @Test
    void buildsProjectInfoWithDefaultPackage() {
        BackendProjection p = builder.buildBackendProjection(minimalPcsf());
        assertThat(p.getProjectInfo().getPackageName()).isEqualTo("com.example.app");
        assertThat(p.getProjectInfo().getPackagePath()).isEqualTo("com/example/app");
    }

    @Test
    void filtersReservedAttributeNames() {
        BackendProjection p = builder.buildBackendProjection(minimalPcsf());
        assertThat(p.getEntities()).hasSize(1);
        BackendEntity product = p.getEntities().get(0);
        assertThat(product.getClassName()).isEqualTo("Product");
        assertThat(product.getFields()).extracting(f -> f.getName())
                .containsExactly("name")
                .doesNotContain("id");
    }

    @Test
    void derivesModuleNamesAndCrudEndpoints() {
        BackendProjection p = builder.buildBackendProjection(minimalPcsf());
        assertThat(p.getModules()).hasSize(1);
        BackendModule module = p.getModules().get(0);
        assertThat(module.getServiceImplName()).isEqualTo("ProductsServiceImpl");
        assertThat(module.getControllerName()).isEqualTo("ProductsController");
        assertThat(module.getEntityClassName()).isEqualTo("Product");
        assertThat(module.getRequestMapping()).isEqualTo("/api/v1/products");
        // No crudOperations specified -> default CRUD -> at least the 5 standard endpoints
        assertThat(module.getEndpoints()).isNotEmpty();
    }

    @Test
    void derivesSampleValuesPerTypeAndMarksEntityTestable() {
        PcsfEntity e = PcsfEntity.builder().id("e1").name(fv("Thing"))
                .attributes(List.of(
                        PcsfAttribute.builder().name(fv("label")).javaType(fv("String")).build(),
                        PcsfAttribute.builder().name(fv("count")).javaType(fv("Integer")).build(),
                        PcsfAttribute.builder().name(fv("price")).javaType(fv("BigDecimal")).build(),
                        PcsfAttribute.builder().name(fv("active")).javaType(fv("Boolean")).build(),
                        PcsfAttribute.builder().name(fv("due")).javaType(fv("LocalDate")).build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("P")).build())
                .entities(List.of(e))
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Things")).build()))
                .build();

        BackendEntity built = builder.buildBackendProjection(pcsf).getEntities().get(0);

        assertThat(built.isTestable()).isTrue();
        assertThat(built.getFields()).extracting(BackendField::getSampleValue)
                .containsExactly("\"sample\"", "1", "new java.math.BigDecimal(\"1.00\")",
                        "true", "java.time.LocalDate.now()");
    }

    @Test
    void unknownRequiredTypeMakesEntityNotTestable() {
        // An AI-introduced enum: we cannot construct a value, so a required field of that type
        // must disable the persist round-trip test rather than emit code that won't compile.
        PcsfConstraints required = PcsfConstraints.builder()
                .required(FieldValue.<Boolean>builder().value(true).build()).build();
        PcsfEntity e = PcsfEntity.builder().id("e1").name(fv("Thing"))
                .attributes(List.of(
                        PcsfAttribute.builder().name(fv("status")).javaType(fv("OrderStatus"))
                                .constraints(required).build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("P")).build())
                .entities(List.of(e))
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Things")).build()))
                .build();

        BackendEntity built = builder.buildBackendProjection(pcsf).getEntities().get(0);

        assertThat(built.getFields().get(0).getSampleValue()).isNull();
        assertThat(built.isTestable()).isFalse();
    }

    @Test
    void sampleTextRespectsSizeBounds() {
        PcsfConstraints bounds = PcsfConstraints.builder()
                .minLength(FieldValue.<Integer>builder().value(10).build())
                .maxLength(FieldValue.<Integer>builder().value(12).build()).build();
        PcsfEntity e = PcsfEntity.builder().id("e1").name(fv("Thing"))
                .attributes(List.of(PcsfAttribute.builder().name(fv("code")).javaType(fv("String"))
                        .constraints(bounds).build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("P")).build())
                .entities(List.of(e))
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Things")).build()))
                .build();

        String sample = builder.buildBackendProjection(pcsf).getEntities().get(0)
                .getFields().get(0).getSampleValue();

        // Quoted literal whose content satisfies min=10 and max=12.
        String content = sample.substring(1, sample.length() - 1);
        assertThat(content.length()).isBetween(10, 12);
    }

    @Test
    void customActionMethodNamesAreValidJavaIdentifiers() {
        // A use case name with punctuation/parentheses must NOT leak into the method name
        // (regression: "Record a Stock Entry (Goods Received)" -> "recordAStockEntry(goodsReceived)").
        PcsfEntity movement = PcsfEntity.builder()
                .id("e1").name(fv("StockMovement"))
                .attributes(List.of(PcsfAttribute.builder().name(fv("quantity")).javaType(fv("Integer")).build()))
                .build();
        PcsfModule module = PcsfModule.builder().id("m1").name(fv("StockMovements"))
                .useCases(List.of(
                        PcsfUseCase.builder().name(fv("Record a Stock Entry (Goods Received)")).build(),
                        PcsfUseCase.builder().name(fv("Record a Stock Exit (Goods Dispatched)")).build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("Inventory")).build())
                .entities(List.of(movement))
                .modules(List.of(module))
                .build();

        BackendModule bm = builder.buildBackendProjection(pcsf).getModules().get(0);

        assertThat(bm.getEndpoints()).extracting(BackendEndpoint::getMethodName)
                .allSatisfy(name -> assertThat(name).matches("[a-zA-Z][a-zA-Z0-9]*"));
        // The custom actions are present, sanitised.
        assertThat(bm.getEndpoints()).extracting(BackendEndpoint::getMethodName)
                .contains("recordAStockEntryGoodsReceived", "recordAStockExitGoodsDispatched");
    }
}
