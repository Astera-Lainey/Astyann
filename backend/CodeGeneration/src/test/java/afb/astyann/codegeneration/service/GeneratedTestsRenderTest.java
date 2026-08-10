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
                                .returnType("Page<ProductResponseDto>").hasRequestBody(false)
                                .pathVariables(java.util.List.of()).responseType("ProductResponseDto").crud(true)
                                .paged(true)
                                .roles(List.of("STOCK_MANAGER")).build(),
                        BackendEndpoint.builder().httpMethod("GET").path("/{id}").methodName("getProductById")
                                .returnType("ProductResponseDto").hasRequestBody(false)
                                .pathVariables(java.util.List.of("id")).responseType("ProductResponseDto").crud(true)
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
                // The service takes a Pageable now — stubbing the no-arg form would not compile,
                // which is exactly what broke `mvn test` on the first paginated generation.
                .contains("when(service.getAllProducts(any(Pageable.class))).thenReturn(Page.empty());")
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
                        .hasRequestBody(false).pathVariables(java.util.List.of("id")).crud(false)
                        .roles(List.of("ADMIN")).build()))
                .build();
        Map<String, Object> model = baseModel();
        model.put("module", noList);

        String source = engine.render("backend/ControllerTest.java.ftl", model);

        assertValidJava(source, "ControllerTest (no list endpoint)");
        assertThat(source).contains("controllerIsWired").doesNotContain("thenReturn");
    }

    /**
     * The generated frontend calls {@code POST {versionPrefix}/auth/login} with
     * {@code {email, password}} and expects {@code {token}}. {@code SecurityConfig} already permits
     * {@code /auth/**}, but nothing served it — so no token could ever be obtained and every
     * request came back 401.
     */
    @Test
    void authControllerServesTheLoginEndpointTheFrontendCalls() {
        Map<String, Object> base = baseModel();
        base.put("roles", List.of(
                afb.astyann.codegeneration.domain.projection.BackendRole.builder()
                        .enumValue("STOCK_MANAGER").roleName("ROLE_STOCK_MANAGER").build()));

        String source = engine.render("backend/AuthController.java.ftl", base);

        assertValidJava(source, "AuthController");
        assertThat(source)
                .contains("@RequestMapping(\"/api/v1/auth\")")
                .contains("@PostMapping(\"/login\")")
                .contains("record LoginRequest(@NotBlank String email, @NotBlank String password)")
                .contains("record LoginResponse(String token)")
                // Claim name must match what JwtFilter reads.
                .contains(".claim(\"roles\", roles)");
    }

    @Test
    void corsConfigAllowsTheFrontendOrigin() {
        String source = engine.render("backend/CorsConfig.java.ftl", baseModel());
        assertValidJava(source, "CorsConfig");
        assertThat(source)
                .contains("CorsConfigurationSource corsConfigurationSource()")
                .contains("app.cors.allowed-origins")
                .contains("setAllowCredentials(true)");

        // The filter chain must actually consult that bean.
        assertThat(engine.render("backend/SecurityConfig.java.ftl", baseModel()))
                .contains(".cors(Customizer.withDefaults())");
    }

    @Test
    void collectionEndpointIsPagedOnBothSidesOfTheContract() {
        Map<String, Object> model = baseModel();
        model.put("entity", testableEntity());
        model.put("module", BackendModule.builder()
                .controllerName("ProductController").serviceName("ProductService")
                .serviceImplName("ProductServiceImpl").repositoryName("ProductRepository")
                .requestMapping("/api/v1/product").packageName("com.example.inventory")
                .entityClassName("Product").entityInstanceName("product")
                .endpoints(List.of(BackendEndpoint.builder()
                        .httpMethod("GET").path("").methodName("getAllProducts")
                        .returnType("Page<ProductResponseDto>")
                        .hasRequestBody(false).pathVariables(java.util.List.of())
                        .responseType("ProductResponseDto").crud(true).paged(true)
                        .roles(List.of("STOCK_MANAGER")).build()))
                .build());

        String controller = engine.render("backend/Controller.java.ftl", model);
        assertValidJava(controller, "Controller (paged)");
        assertThat(controller)
                .contains("ResponseEntity<Page<ProductResponseDto>> getAllProducts(@PageableDefault(size = 20) Pageable pageable)")
                .contains("service.getAllProducts(pageable)");

        String serviceInterface = engine.render("backend/ServiceInterface.java.ftl", model);
        assertValidJava(serviceInterface, "ServiceInterface (paged)");
        assertThat(serviceInterface).contains("Page<ProductResponseDto> getAllProducts(Pageable pageable);");

        String serviceImpl = engine.render("backend/ServiceImpl.java.ftl", model);
        assertValidJava(serviceImpl, "ServiceImpl (paged)");
        assertThat(serviceImpl).contains("return repository.findAll(pageable).map(this::toResponse);");
    }

    /**
     * Every template that mentions the primary key must agree on its type. Entity/Repository/
     * ResponseDto once honoured {@code idStrategy} while Controller/Service hardcoded {@code UUID},
     * so an {@code IDENTITY} entity generated a project that could not compile — and the AI fix
     * loop oscillated forever, because making the impl match the interface broke the interface.
     */
    @Test
    void identityStrategyUsesLongConsistentlyAcrossEveryTemplate() {
        BackendEntity identityEntity = BackendEntity.builder()
                .className("Product").tableName("products").instanceName("product")
                .audited(true).idStrategy("IDENTITY").idType("Long").testable(true)
                .fields(List.of(BackendField.builder().name("name").columnName("name")
                        .javaType("String").required(true).sampleValue("\"sample\"").build()))
                .build();

        Map<String, Object> model = baseModel();
        model.put("entity", identityEntity);
        model.put("module", BackendModule.builder()
                .controllerName("ProductController").serviceName("ProductService")
                .serviceImplName("ProductServiceImpl").repositoryName("ProductRepository")
                .requestMapping("/api/v1/product").packageName("com.example.inventory")
                .entityClassName("Product").entityInstanceName("product")
                .endpoints(List.of(BackendEndpoint.builder()
                        .httpMethod("GET").path("/{id}").methodName("getProductById")
                        .returnType("ProductResponseDto").hasRequestBody(false)
                        .pathVariables(java.util.List.of("id")).responseType("ProductResponseDto").crud(true)
                        .roles(List.of("STOCK_MANAGER")).build()))
                .build());

        String entity = engine.render("backend/Entity.java.ftl", model);
        String repository = engine.render("backend/Repository.java.ftl", model);
        String responseDto = engine.render("backend/ResponseDto.java.ftl", model);
        String controller = engine.render("backend/Controller.java.ftl", model);
        String serviceInterface = engine.render("backend/ServiceInterface.java.ftl", model);
        String serviceImpl = engine.render("backend/ServiceImpl.java.ftl", model);

        for (String source : List.of(entity, repository, responseDto, controller,
                serviceInterface, serviceImpl)) {
            assertValidJava(source, "IDENTITY-strategy source");
        }

        assertThat(entity).contains("private Long id;").contains("GenerationType.IDENTITY");
        assertThat(repository).contains("JpaRepository<Product, Long>");
        assertThat(responseDto).contains("private Long id;");
        // The three that used to hardcode UUID:
        assertThat(controller).contains("@PathVariable Long id").doesNotContain("@PathVariable UUID id");
        assertThat(serviceInterface).contains("getProductById(Long id)").doesNotContain("(UUID id)");
        assertThat(serviceImpl).contains("getProductById(Long id)").doesNotContain("(UUID id)");
    }

    @Test
    void uuidStrategyRemainsTheDefaultEverywhere() {
        Map<String, Object> model = baseModel();
        model.put("entity", testableEntity()); // idType defaults to UUID
        model.put("module", module());

        assertThat(engine.render("backend/Entity.java.ftl", model)).contains("private UUID id;");
        assertThat(engine.render("backend/Repository.java.ftl", model))
                .contains("JpaRepository<Product, UUID>");
        assertThat(engine.render("backend/Controller.java.ftl", model))
                .contains("@PathVariable UUID id");
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
