package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.projection.BackendEndpoint;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.BackendField;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjectInfo;
import afb.astyann.codegeneration.domain.projection.BackendRelationship;
import afb.astyann.codegeneration.domain.projection.BackendRole;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders every backend FreeMarker template against a representative projection to catch FTL
 * syntax and field-reference errors without booting the Spring context (no DB required).
 */
class BackendTemplateRenderTest {

    private final FreeMarkerEngine engine = new FreeMarkerEngine();

    @Test
    void every_backend_template_renders_against_a_representative_projection() {
        BackendProjectInfo project = BackendProjectInfo.builder()
                .appName("Inventory").artifactId("inventory").packageName("com.example.inventory")
                .packagePath("com/example/inventory").databaseName("inventory_db").databaseUser("inv")
                .backendPort(8080).jwtAccessTokenValidityMs(3_600_000L).jwtRefreshTokenValidityMs(604_800_000L)
                .corsAllowedOriginsDev("http://localhost:4200").versionPrefix("/api/v1")
                .build();

        BackendEntity product = BackendEntity.builder()
                .className("Product").tableName("products").instanceName("product")
                .audited(true).idStrategy("UUID")
                .fields(List.of(
                        BackendField.builder().name("name").columnName("name").javaType("String")
                                .required(true).unique(true).minLength(2).maxLength(120).build(),
                        BackendField.builder().name("price").columnName("price").javaType("BigDecimal")
                                .required(true).build(),
                        BackendField.builder().name("active").columnName("active").javaType("Boolean").build()))
                .relationships(List.of(
                        BackendRelationship.builder().fieldName("category").targetEntity("Category")
                                .relationType("MANY_TO_ONE").joinColumn("category_id").owning(true).build()))
                .build();

        BackendModule module = BackendModule.builder()
                .controllerName("ProductController").serviceName("ProductService")
                .serviceImplName("ProductServiceImpl").repositoryName("ProductRepository")
                .requestMapping("/api/v1/product").packageName("com.example.inventory")
                .entityClassName("Product").entityInstanceName("product")
                .endpoints(List.of(
                        endpoint("POST", "", "createProduct", "ProductResponseDto", true, false,
                                "CreateProductDto", "ProductResponseDto", true, List.of("STOCK_MANAGER")),
                        endpoint("GET", "", "getAllProducts", "List<ProductResponseDto>", false, false,
                                null, "ProductResponseDto", true, List.of("STOCK_MANAGER")),
                        endpoint("GET", "/{id}", "getProductById", "ProductResponseDto", false, true,
                                null, "ProductResponseDto", true, List.of("STOCK_MANAGER")),
                        endpoint("PUT", "/{id}", "updateProduct", "ProductResponseDto", true, true,
                                "CreateProductDto", "ProductResponseDto", true, List.of("STOCK_MANAGER")),
                        endpoint("DELETE", "/{id}", "deleteProduct", "void", false, true,
                                null, null, true, List.of("STOCK_MANAGER")),
                        endpoint("POST", "/{id}/archive", "archive", "ProductResponseDto", false, true,
                                null, "ProductResponseDto", false, List.of("STOCK_MANAGER"))))
                .build();

        BackendRole role = BackendRole.builder()
                .roleName("ROLE_STOCK_MANAGER").enumValue("STOCK_MANAGER").type("INTERNAL").build();

        Map<String, Object> base = new HashMap<>();
        base.put("project", project);
        base.put("entities", List.of(product));
        base.put("modules", List.of(module));
        base.put("roles", List.of(role));
        base.put("appClassName", "InventoryApplication");

        Map<String, Object> entityModel = new HashMap<>(base);
        entityModel.put("entity", product);

        Map<String, Object> moduleModel = new HashMap<>(base);
        moduleModel.put("module", module);
        moduleModel.put("entity", product);

        assertThat(engine.render("backend/Entity.java.ftl", entityModel))
                .contains("class Product").contains("@Column(name = \"name\"");
        assertThat(engine.render("backend/Repository.java.ftl", entityModel))
                .contains("interface ProductRepository");
        assertThat(engine.render("backend/CreateDto.java.ftl", entityModel))
                .contains("class CreateProductDto");
        assertThat(engine.render("backend/ResponseDto.java.ftl", entityModel))
                .contains("class ProductResponseDto");
        assertThat(engine.render("backend/ServiceInterface.java.ftl", moduleModel))
                .contains("interface ProductService");
        assertThat(engine.render("backend/ServiceImpl.java.ftl", moduleModel))
                .contains("class ProductServiceImpl").contains("archive not yet implemented");
        assertThat(engine.render("backend/Controller.java.ftl", moduleModel))
                .contains("@RestController").contains("hasAnyRole('STOCK_MANAGER')");
        assertThat(engine.render("backend/Application.java.ftl", base))
                .contains("class InventoryApplication");
        assertThat(engine.render("backend/SecurityConfig.java.ftl", base))
                .contains("class SecurityConfig");
        assertThat(engine.render("backend/JwtFilter.java.ftl", base))
                .contains("class JwtFilter");
        assertThat(engine.render("backend/ApplicationProperties.ftl", base))
                .contains("server.port=8080");
        assertThat(engine.render("backend/PomXml.ftl", base))
                .contains("<artifactId>inventory</artifactId>");
        assertThat(engine.render("backend/BackendDockerfile.ftl", base))
                .contains("EXPOSE 8080");
    }

    private BackendEndpoint endpoint(String method, String path, String name, String returnType,
                                     boolean body, boolean pathVar, String bodyType, String responseType,
                                     boolean crud, List<String> roles) {
        return BackendEndpoint.builder()
                .httpMethod(method).path(path).methodName(name).returnType(returnType)
                .hasRequestBody(body).hasPathVariable(pathVar)
                .requestBodyType(bodyType).responseType(responseType)
                .crud(crud).roles(roles).build();
    }
}
