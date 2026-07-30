package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.projection.BackendEndpoint;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.BackendField;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjectInfo;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the generated test-suite templates and asserts the output is valid Java.
 *
 * <p>Parsing with JavaParser is the strongest check available here: actually executing the
 * generated tests would require resolving Spring/JPA dependencies for a synthetic project, which
 * belongs to an end-to-end run rather than a unit test.
 */
class GeneratedTestsRenderTest {

    private final FreeMarkerEngine engine = new FreeMarkerEngine();
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    private void assertValidJava(String source, String what) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        assertThat(result.isSuccessful())
                .as("%s should render valid Java but failed: %s", what, result.getProblems())
                .isTrue();
    }

    private BackendProjectInfo projectInfo() {
        return BackendProjectInfo.builder()
                .appName("Inventory").artifactId("inventory").packageName("com.example.inventory")
                .packagePath("com/example/inventory").databaseName("inventory_db").databaseUser("inv")
                .backendPort(8080).jwtAccessTokenValidityMs(3_600_000L).jwtRefreshTokenValidityMs(604_800_000L)
                .corsAllowedOriginsDev("http://localhost:4200").versionPrefix("/api/v1")
                .build();
    }

    private BackendEntity testableEntity() {
        return BackendEntity.builder()
                .className("Product").tableName("products").instanceName("product")
                .audited(true).idStrategy("UUID").testable(true)
                .fields(List.of(
                        BackendField.builder().name("name").columnName("name").javaType("String")
                                .required(true).sampleValue("\"sample\"").build(),
                        BackendField.builder().name("price").columnName("price").javaType("BigDecimal")
                                .required(true).sampleValue("new java.math.BigDecimal(\"1.00\")").build(),
                        // Unknown type: no sample value — must simply be skipped, not emitted as null.
                        BackendField.builder().name("status").columnName("status").javaType("ProductStatus")
                                .required(false).build()))
                .build();
    }

    private BackendModule module() {
        return BackendModule.builder()
                .controllerName("ProductController").serviceName("ProductService")
                .serviceImplName("ProductServiceImpl").repositoryName("ProductRepository")
                .requestMapping("/api/v1/product").packageName("com.example.inventory")
                .entityClassName("Product").entityInstanceName("product")
                .endpoints(List.of(
                        BackendEndpoint.builder().httpMethod("GET").path("").methodName("getAllProducts")
                                .returnType("List<ProductResponseDto>").hasRequestBody(false)
                                .hasPathVariable(false).responseType("ProductResponseDto").crud(true)
                                .roles(List.of("STOCK_MANAGER")).build(),
                        BackendEndpoint.builder().httpMethod("GET").path("/{id}").methodName("getProductById")
                                .returnType("ProductResponseDto").hasRequestBody(false)
                                .hasPathVariable(true).responseType("ProductResponseDto").crud(true)
                                .roles(List.of("STOCK_MANAGER")).build()))
                .build();
    }

    private Map<String, Object> baseModel() {
        Map<String, Object> base = new HashMap<>();
        base.put("project", projectInfo());
        base.put("appClassName", "InventoryApplication");
        return base;
    }

    @Test
    void smokeTestAndJpaConfigRenderValidJava() {
        Map<String, Object> base = baseModel();
        String smoke = engine.render("backend/ApplicationSmokeTest.java.ftl", base);
        assertValidJava(smoke, "ApplicationSmokeTest");
        assertThat(smoke).contains("class InventoryApplicationSmokeTest")
                .contains("@SpringBootTest").contains("@ActiveProfiles(\"test\")");

        String jpaConfig = engine.render("backend/JpaConfig.java.ftl", base);
        assertValidJava(jpaConfig, "JpaConfig");
        assertThat(jpaConfig).contains("@EnableJpaAuditing");
    }

    @Test
    void repositoryTestRendersPersistRoundTripForTestableEntity() {
        Map<String, Object> model = baseModel();
        model.put("entity", testableEntity());

        String source = engine.render("backend/RepositoryTest.java.ftl", model);

        assertValidJava(source, "RepositoryTest (testable)");
        assertThat(source).contains("class ProductRepositoryTest")
                .contains("@DataJpaTest")
                .contains("savesAndReadsBack")
                .contains("product.setName(\"sample\");")
                .contains("product.setPrice(new java.math.BigDecimal(\"1.00\"));")
                // The unknown-typed field has no sample value and must be omitted entirely.
                .doesNotContain("setStatus");
    }

    @Test
    void repositoryTestOmitsPersistTestWhenEntityNotTestable() {
        BackendEntity notTestable = BackendEntity.builder()
                .className("Order").tableName("orders").instanceName("order")
                .audited(false).idStrategy("UUID").testable(false)
                .fields(List.of(BackendField.builder().name("status").columnName("status")
                        .javaType("OrderStatus").required(true).build()))
                .build();
        Map<String, Object> model = baseModel();
        model.put("entity", notTestable);

        String source = engine.render("backend/RepositoryTest.java.ftl", model);

        assertValidJava(source, "RepositoryTest (not testable)");
        assertThat(source).contains("class OrderRepositoryTest")
                .contains("repositoryIsWired")
                .doesNotContain("savesAndReadsBack");
    }

    @Test
    void controllerTestRendersAgainstListEndpoint() {
        Map<String, Object> model = baseModel();
        model.put("module", module());
        model.put("entity", testableEntity());

        String source = engine.render("backend/ControllerTest.java.ftl", model);

        assertValidJava(source, "ControllerTest");
        assertThat(source).contains("class ProductControllerTest")
                .contains("@WebMvcTest(controllers = ProductController.class)")
                .contains("addFilters = false")
                .contains("when(service.getAllProducts()).thenReturn(java.util.List.of());")
                .contains("get(\"/api/v1/product\")");
    }

    @Test
    void controllerTestFallsBackWhenModuleHasNoListEndpoint() {
        BackendModule noList = BackendModule.builder()
                .controllerName("ReportController").serviceName("ReportService")
                .serviceImplName("ReportServiceImpl").repositoryName("ReportRepository")
                .requestMapping("/api/v1/report").packageName("com.example.inventory")
                .entityClassName("Report").entityInstanceName("report")
                .endpoints(List.of(BackendEndpoint.builder().httpMethod("POST").path("/{id}/run")
                        .methodName("runReport").returnType("ReportResponseDto")
                        .hasRequestBody(false).hasPathVariable(true).crud(false)
                        .roles(List.of("ADMIN")).build()))
                .build();
        Map<String, Object> model = baseModel();
        model.put("module", noList);

        String source = engine.render("backend/ControllerTest.java.ftl", model);

        assertValidJava(source, "ControllerTest (no list endpoint)");
        assertThat(source).contains("controllerIsWired").doesNotContain("thenReturn");
    }

    @Test
    void testPropertiesPointAtInMemoryH2() {
        String props = engine.render("backend/TestApplicationProperties.ftl", baseModel());
        assertThat(props).contains("jdbc:h2:mem:inventory_db_test")
                .contains("org.hibernate.dialect.H2Dialect")
                .contains("create-drop")
                .contains("jwt.secret=");
    }

    @Test
    void generatedPomDeclaresH2TestDependency() {
        assertThat(engine.render("backend/PomXml.ftl", baseModel()))
                .contains("<artifactId>h2</artifactId>");
    }
}
