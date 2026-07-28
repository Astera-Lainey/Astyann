package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
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
