package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.projection.BackendEndpoint;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.BackendField;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjectInfo;
import afb.astyann.codegeneration.domain.projection.BackendRole;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the three templates whose signatures must agree — controller, service interface, service
 * implementation — from a module whose endpoints came from a declared PCSF contract, and parses
 * each one.
 *
 * <p>Asserting on substrings alone would not have caught the failure mode this guards: an endpoint
 * mapped at {@code /{productId}} whose method takes no parameter still "contains" everything you
 * would think to look for, and only fails when javac sees it.
 */
class DeclaredEndpointRenderTest {

    private final FreeMarkerEngine engine = new FreeMarkerEngine();

    private static CompilationUnit parse(String source) {
        ParseResult<CompilationUnit> result = new JavaParser().parse(source);
        assertThat(result.isSuccessful())
                .as("generated source should parse:%n%s%n%s", source, result.getProblems())
                .isTrue();
        return result.getResult().orElseThrow();
    }

    private Map<String, Object> model() {
        BackendProjectInfo project = BackendProjectInfo.builder()
                .appName("Inventory").artifactId("inventory").packageName("com.example.inventory")
                .packagePath("com/example/inventory").databaseName("inventory_db").databaseUser("inv")
                .backendPort(8080).jwtAccessTokenValidityMs(3_600_000L)
                .jwtRefreshTokenValidityMs(604_800_000L)
                .corsAllowedOriginsDev("http://localhost:4200").versionPrefix("/api/v1")
                .build();

        BackendEntity product = BackendEntity.builder()
                .className("Product").tableName("products").instanceName("product")
                .audited(true).idStrategy("UUID").idType("UUID")
                .fields(List.of(BackendField.builder().name("name").columnName("name")
                        .javaType("String").required(true).build()))
                .relationships(List.of())
                .build();

        // Exactly the declared surface observed for the Products module: a named path variable, a
        // PATCH sub-resource action, and a collection-level export.
        BackendModule module = BackendModule.builder()
                .controllerName("ProductController").serviceName("ProductService")
                .serviceImplName("ProductServiceImpl").repositoryName("ProductRepository")
                .requestMapping("/api/v1/products").packageName("com.example.inventory")
                .entityClassName("Product").entityInstanceName("product")
                .endpoints(List.of(
                        BackendEndpoint.builder().httpMethod("POST").path("").methodName("createProduct")
                                .returnType("ProductResponseDto").hasRequestBody(true)
                                .requestBodyType("CreateProductDto").responseType("ProductResponseDto")
                                .pathVariables(List.of()).idVariable("id").crud(true)
                                .roles(List.of("ADMINISTRATOR")).build(),
                        BackendEndpoint.builder().httpMethod("GET").path("").methodName("listProducts")
                                .returnType("Page<ProductResponseDto>").responseType("ProductResponseDto")
                                .pathVariables(List.of()).idVariable("id").crud(true).paged(true)
                                .roles(List.of("ADMINISTRATOR")).build(),
                        BackendEndpoint.builder().httpMethod("GET").path("/{productId}")
                                .methodName("getProductById").returnType("ProductResponseDto")
                                .responseType("ProductResponseDto")
                                .pathVariables(List.of("productId")).idVariable("productId").crud(true)
                                .roles(List.of("ADMINISTRATOR")).build(),
                        BackendEndpoint.builder().httpMethod("PUT").path("/{productId}")
                                .methodName("updateProduct").returnType("ProductResponseDto")
                                .hasRequestBody(true).requestBodyType("CreateProductDto")
                                .responseType("ProductResponseDto")
                                .pathVariables(List.of("productId")).idVariable("productId").crud(true)
                                .roles(List.of("ADMINISTRATOR")).build(),
                        BackendEndpoint.builder().httpMethod("PATCH").path("/{productId}/archive")
                                .methodName("archiveProduct").returnType("ProductResponseDto")
                                .responseType("ProductResponseDto")
                                .pathVariables(List.of("productId")).idVariable("productId").crud(false)
                                .roles(List.of("ADMINISTRATOR")).build(),
                        BackendEndpoint.builder().httpMethod("GET").path("/export")
                                .methodName("exportProductsExcel").returnType("ProductResponseDto")
                                .responseType("ProductResponseDto")
                                .pathVariables(List.of()).idVariable("id").crud(false)
                                .roles(List.of("ADMINISTRATOR")).build()))
                .build();

        Map<String, Object> model = new HashMap<>();
        model.put("project", project);
        model.put("entities", List.of(product));
        model.put("modules", List.of(module));
        model.put("roles", List.of(BackendRole.builder().roleName("ROLE_ADMINISTRATOR")
                .enumValue("ADMINISTRATOR").type("INTERNAL").build()));
        model.put("module", module);
        model.put("entity", product);
        return model;
    }

    @Test
    void theControllerParsesAndCarriesTheDeclaredVerbsAndPaths() {
        String source = engine.render("backend/Controller.java.ftl", model());
        parse(source);

        assertThat(source)
                .contains("@RequestMapping(\"/api/v1/products\")")
                .contains("@PatchMapping(\"/{productId}/archive\")")
                .contains("@GetMapping(\"/export\")")
                // The declared name, not a generic id, and bound as an actual parameter.
                .contains("@PathVariable UUID productId")
                .doesNotContain("@PathVariable UUID id");
    }

    @Test
    void everyMappedPathVariableIsBoundAsAParameter() {
        // The regression that motivated deriving hasPathVariable: a mapping declaring {productId}
        // while the method took nothing compiles as Java but fails at Spring startup.
        String source = engine.render("backend/Controller.java.ftl", model());

        assertThat(source.lines().filter(l -> l.contains("{productId}") && l.contains("Mapping")).count())
                .isEqualTo(3);
        assertThat(source.lines().filter(l -> l.contains("@PathVariable UUID productId")).count())
                .isEqualTo(3);
    }

    @Test
    void theServiceInterfaceAndImplementationAgreeWithTheController() {
        Map<String, Object> model = model();
        String iface = engine.render("backend/ServiceInterface.java.ftl", model);
        String impl = engine.render("backend/ServiceImpl.java.ftl", model);
        parse(iface);
        parse(impl);

        // Same parameter name in all three, or the @Override does not resolve.
        assertThat(iface).contains("ProductResponseDto getProductById(UUID productId)");
        assertThat(impl).contains("public ProductResponseDto getProductById(UUID productId)")
                .contains("repository.findById(productId)");
    }

    @Test
    void nonCanonicalOperationsAreLeftAsStubsForLogicInjection() {
        String impl = engine.render("backend/ServiceImpl.java.ftl", model());

        assertThat(impl)
                .contains("archiveProduct not yet implemented")
                .contains("exportProductsExcel not yet implemented")
                // ...while the canonical four get real bodies.
                .contains("repository.save(entity)")
                .contains("repository.findAll(pageable)");
    }
}
