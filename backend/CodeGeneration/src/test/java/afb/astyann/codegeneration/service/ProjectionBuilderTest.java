package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfConstraints;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.projection.BackendField;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfProject;
import afb.astyann.codegeneration.domain.pcsf.PcsfStatusMachine;
import afb.astyann.codegeneration.domain.pcsf.PcsfUseCase;
import afb.astyann.codegeneration.domain.projection.BackendEndpoint;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.FrontendEndpoint;
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

    /**
     * The frontend's {@code apiBaseUrl} already ends with the version prefix, so the module's
     * {@code apiPath} must be relative to it. Concatenating the full backend request mapping
     * produced {@code http://localhost:8080/api/v1/api/v1/product} and 404'd every call.
     */
    @Test
    void frontendApiPathIsRelativeToApiBaseUrl() {
        Pcsf pcsf = minimalPcsf();

        var projection = builder.buildFrontendProjection(pcsf);
        var module = projection.getModules().get(0);
        var backendModule = builder.buildBackendProjection(pcsf).getModules().get(0);

        assertThat(backendModule.getRequestMapping()).isEqualTo("/api/v1/products");
        assertThat(projection.getProjectInfo().getApiBaseUrl()).endsWith("/api/v1");
        assertThat(module.getApiPath()).isEqualTo("/products");

        // The two concatenated must yield exactly one version prefix.
        String resolved = projection.getProjectInfo().getApiBaseUrl() + module.getApiPath();
        assertThat(resolved).isEqualTo("http://localhost:8080/api/v1/products")
                .doesNotContain("/api/v1/api/v1");
    }

    @Test
    void formFieldsCarryTheSameConstraintsTheBackendEnforces() {
        PcsfConstraints nameRules = PcsfConstraints.builder()
                .required(FieldValue.<Boolean>builder().value(true).build())
                .minLength(FieldValue.<Integer>builder().value(2).build())
                .maxLength(FieldValue.<Integer>builder().value(120).build())
                .build();
        PcsfEntity e = PcsfEntity.builder().id("e1").name(fv("Customer"))
                .attributes(List.of(
                        PcsfAttribute.builder().name(fv("name")).javaType(fv("String"))
                                .constraints(nameRules).build(),
                        PcsfAttribute.builder().name(fv("email")).javaType(fv("String")).build(),
                        // Length rules must NOT be applied to a numeric control — minLength on a
                        // number checks the string length and would reject valid input.
                        PcsfAttribute.builder().name(fv("age")).javaType(fv("Integer"))
                                .constraints(PcsfConstraints.builder()
                                        .minLength(FieldValue.<Integer>builder().value(2).build()).build())
                                .build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("CRM")).build())
                .entities(List.of(e))
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Customers")).build()))
                .build();

        var formFields = builder.buildFrontendProjection(pcsf).getModules().get(0).getFormFields();

        var name = formFields.stream().filter(f -> f.getFieldName().equals("name")).findFirst().orElseThrow();
        assertThat(name.isHasValidators()).isTrue();
        assertThat(name.getValidatorsExpression())
                .isEqualTo("Validators.required, Validators.minLength(2), Validators.maxLength(120)");

        var email = formFields.stream().filter(f -> f.getFieldName().equals("email")).findFirst().orElseThrow();
        assertThat(email.getValidatorsExpression()).isEqualTo("Validators.email");

        var age = formFields.stream().filter(f -> f.getFieldName().equals("age")).findFirst().orElseThrow();
        assertThat(age.getValidatorsExpression()).isNull();
        assertThat(age.isHasValidators()).isFalse();
    }

    @Test
    void statusColumnBecomesABadgeWhenAStateMachineDeclaresTheValues() {
        PcsfEntity order = PcsfEntity.builder().id("e1").name(fv("Order"))
                .attributes(List.of(
                        PcsfAttribute.builder().name(fv("reference")).javaType(fv("String")).build(),
                        PcsfAttribute.builder().name(fv("status")).javaType(fv("OrderStatus")).build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("Sales")).build())
                .entities(List.of(order))
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Orders")).build()))
                .statusMachines(List.of(PcsfStatusMachine.builder()
                        .entityId("Order").initialState("DRAFT")
                        .states(List.of("DRAFT", "APPROVED", "CANCELLED", "SOMETHING_ODD"))
                        .build()))
                .build();

        var columns = builder.buildFrontendProjection(pcsf).getModules().get(0).getListColumns();

        var statusCol = columns.stream().filter(c -> c.getFieldName().equals("status")).findFirst().orElseThrow();
        assertThat(statusCol.isBadge()).isTrue();
        assertThat(statusCol.getVariantsExpression())
                .contains("'DRAFT': 'warning'")
                .contains("'APPROVED': 'success'")
                .contains("'CANCELLED': 'danger'")
                // Unknown states degrade to neutral rather than breaking the column.
                .contains("'SOMETHING_ODD': 'neutral'");

        var plainCol = columns.stream().filter(c -> c.getFieldName().equals("reference")).findFirst().orElseThrow();
        assertThat(plainCol.isBadge()).isFalse();
        assertThat(plainCol.getVariantsExpression()).isNull();
    }

    @Test
    void statusColumnStaysPlainTextWithoutAStateMachine() {
        PcsfEntity order = PcsfEntity.builder().id("e1").name(fv("Order"))
                .attributes(List.of(PcsfAttribute.builder().name(fv("status")).javaType(fv("String")).build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("Sales")).build())
                .entities(List.of(order))
                .modules(List.of(PcsfModule.builder().id("m1").name(fv("Orders")).build()))
                .build();

        var statusCol = builder.buildFrontendProjection(pcsf).getModules().get(0)
                .getListColumns().stream().filter(c -> c.getFieldName().equals("status")).findFirst().orElseThrow();

        // No declared states means no known value set, so no colour mapping can be justified.
        assertThat(statusCol.isBadge()).isFalse();
    }

    @Test
    void frontendProjectionCarriesCustomUseCaseActions() {
        PcsfEntity movement = PcsfEntity.builder()
                .id("e1").name(fv("StockMovement"))
                .attributes(List.of(PcsfAttribute.builder().name(fv("quantity")).javaType(fv("Integer")).build()))
                .build();
        PcsfModule module = PcsfModule.builder().id("m1").name(fv("StockMovements"))
                .useCases(List.of(
                        // Non-CRUD verb -> becomes a custom action
                        PcsfUseCase.builder().name(fv("Record a Stock Entry")).build(),
                        // CRUD verb -> must NOT become a custom action
                        PcsfUseCase.builder().name(fv("List stock movements")).build()))
                .build();
        Pcsf pcsf = Pcsf.builder()
                .project(PcsfProject.builder().name(fv("Inventory")).build())
                .entities(List.of(movement))
                .modules(List.of(module))
                .build();

        var fm = builder.buildFrontendProjection(pcsf).getModules().get(0);

        assertThat(fm.isHasCustomActions()).isTrue();
        assertThat(fm.getCustomActions()).hasSize(1);
        var action = fm.getCustomActions().get(0);
        assertThat(action.getMethodName()).isEqualTo("recordAStockEntry");
        assertThat(action.getMethodNamePascal()).isEqualTo("RecordAStockEntry");
        assertThat(action.getActionSegment()).isEqualTo("recordAStockEntry");
        assertThat(action.getLabel()).isEqualTo("Record a stock entry");
        assertThat(action.isCrud()).isFalse();
        // The standard CRUD endpoints stay out of the custom-action list.
        assertThat(fm.getCustomActions()).noneMatch(FrontendEndpoint::isCrud);
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
