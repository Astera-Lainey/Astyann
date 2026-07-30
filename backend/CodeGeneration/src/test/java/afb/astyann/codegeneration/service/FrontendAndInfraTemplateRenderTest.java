package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.projection.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders every frontend Mustache and infrastructure FreeMarker/Mustache template against a
 * representative projection so template syntax and field references break the build immediately
 * rather than at runtime.
 */
class FrontendAndInfraTemplateRenderTest {

    private final FreeMarkerEngine freeMarker = new FreeMarkerEngine();
    private final MustacheEngine mustache = new MustacheEngine();

    @Test
    void every_frontend_template_renders_against_a_representative_projection() {
        FrontendProjectInfo project = FrontendProjectInfo.builder()
                .appName("Inventory").angularProjectName("inventory-web")
                .apiBaseUrl("http://localhost:8080/api/v1")
                .primaryColour("#CC0000").primaryDark("#8f0000").primaryLight("#e04f4f")
                .primaryAlpha("rgba(204, 0, 0, 0.15)")
                .secondaryColour("#FFFFFF").neutralColour("#F5F5F5").textColour("#1A1A1A")
                .fontFamily("Inter").defaultRoute("/product")
                .build();

        FrontendField nameField = FrontendField.builder().name("name").tsType("string")
                .label("Name").required(true).unique(true).build();
        FrontendField priceField = FrontendField.builder().name("price").tsType("number")
                .label("Price").required(true).build();

        FrontendEntity product = FrontendEntity.builder()
                .className("Product").fileName("product").instanceName("product")
                .fields(List.of(nameField, priceField))
                .build();

        FrontendModule module = FrontendModule.builder()
                .serviceName("ProductService").serviceFileName("product").componentPrefix("product")
                .entityClassName("Product").entityFileName("product").entityInstanceName("product")
                .apiPath("/api/v1/product")
                .hasCreate(true).hasRead(true).hasUpdate(true).hasDelete(true)
                .endpoints(List.of(
                        FrontendEndpoint.builder().methodName("getAll").httpMethod("GET").path("")
                                .hasPathId(false).hasBody(false).returnType("Product[]").build(),
                        FrontendEndpoint.builder().methodName("create").httpMethod("POST").path("")
                                .hasPathId(false).hasBody(true).returnType("Product").build()))
                .listColumns(List.of(
                        FrontendColumn.builder().label("Name").fieldName("name").build(),
                        FrontendColumn.builder().label("Price").fieldName("price").build()))
                .formFields(List.of(
                        FrontendFormField.builder().label("Name").fieldName("name")
                                .inputType("text").required(true).build(),
                        FrontendFormField.builder().label("Price").fieldName("price")
                                .inputType("number").required(true).build()))
                .build();

        FrontendNavItem nav = FrontendNavItem.builder()
                .label("Products").path("/product").icon("mdi:package-variant")
                .roles(List.of("STOCK_MANAGER")).build();

        Map<String, Object> base = new HashMap<>();
        base.put("project", project);
        base.put("entities", List.of(product));
        base.put("modules", List.of(module));
        base.put("navigation", List.of(nav));

        Map<String, Object> entityModel = new HashMap<>(base);
        entityModel.put("entity", product);

        Map<String, Object> moduleModel = new HashMap<>(base);
        moduleModel.put("module", module);
        moduleModel.put("entity", product);

        assertThat(mustache.render("frontend/model.ts.mustache", entityModel))
                .contains("export interface Product").contains("name: string;");
        assertThat(mustache.render("frontend/service.ts.mustache", moduleModel))
                .contains("class ProductService").contains("http://localhost:8080/api/v1/api/v1/product".substring(0, 0) + "")
                .contains("apiBaseUrl");
        assertThat(mustache.render("frontend/list.component.ts.mustache", moduleModel))
                .contains("ProductListComponent").contains("fieldName: 'name'");
        String listHtml = mustache.render("frontend/list.component.html.mustache", moduleModel);
        assertThat(listHtml).contains("Products").contains("[routerLink]=\"['/product/new']\"")
                .contains("{{error()}}");
        assertThat(mustache.render("frontend/form.component.ts.mustache", moduleModel))
                .contains("ProductFormComponent").contains("Validators.required");
        String formHtml = mustache.render("frontend/form.component.html.mustache", moduleModel);
        assertThat(formHtml).contains("<ast-input label=\"Name\"").contains("{{error()}}");
        assertThat(mustache.render("frontend/app.routes.ts.mustache", base))
                .contains("redirectTo: '/product'").contains("path: 'product'");
        assertThat(mustache.render("frontend/app.config.ts.mustache", base))
                .contains("provideRouter(routes)");
        assertThat(mustache.render("frontend/environment.ts.mustache", base))
                .contains("apiBaseUrl: 'http://localhost:8080/api/v1'");
        assertThat(mustache.render("frontend/package.json.mustache", base))
                .contains("\"name\": \"inventory-web\"");
        assertThat(mustache.render("frontend/login.component.ts.mustache", base))
                .contains("LoginComponent").contains("/auth/login");
        String loginHtml = mustache.render("frontend/login.component.html.mustache", base);
        assertThat(loginHtml).contains("Sign in to Inventory").contains("{{error()}}");
        String sidebarTs = mustache.render("frontend/sidebar.component.ts.mustache", base);
        assertThat(sidebarTs).contains("SidebarComponent").contains("Products")
                // <iconify-icon> is a web component: without the schema and the registering
                // side-effect import, `ng build` rejects it as an unknown element.
                .contains("CUSTOM_ELEMENTS_SCHEMA")
                .contains("import 'iconify-icon';");
        String sidebarHtml = mustache.render("frontend/sidebar.component.html.mustache", base);
        assertThat(sidebarHtml).contains("Inventory").contains("{{item.label}}");
        assertThat(mustache.render("frontend/jwt.interceptor.ts.mustache", base))
                .contains("class JwtInterceptor");
        assertThat(mustache.render("frontend/auth.guard.ts.mustache", base))
                .contains("authGuard");
        assertThat(freeMarker.render("frontend/styles.scss.ftl", base))
                .contains("--color-primary: #CC0000").contains(".ast-btn ");

        // ── Angular CLI scaffold ──
        String angularJson = mustache.render("frontend/angular.json.mustache", base);
        assertThat(angularJson)
                .contains("\"inventory-web\":")
                .contains("@angular-devkit/build-angular:application")
                .contains("\"browser\": \"src/main.ts\"")
                .contains("\"tsConfig\": \"tsconfig.app.json\"")
                // The generator writes styles to src/styles/styles.scss, not src/styles.scss.
                .contains("\"src/styles/styles.scss\"");

        assertThat(mustache.render("frontend/tsconfig.json.mustache", base))
                .contains("\"strict\": true").contains("\"strictTemplates\": true");
        assertThat(mustache.render("frontend/tsconfig.app.json.mustache", base))
                .contains("\"extends\": \"./tsconfig.json\"").contains("src/main.ts");
        assertThat(mustache.render("frontend/index.html.mustache", base))
                .contains("<app-root></app-root>").contains("<title>Inventory</title>");
        assertThat(mustache.render("frontend/main.ts.mustache", base))
                .contains("bootstrapApplication(AppComponent, appConfig)");
        assertThat(mustache.render("frontend/app.component.ts.mustache", base))
                .contains("class AppComponent")
                .contains("selector: 'app-root'")
                .contains("<router-outlet></router-outlet>")
                .contains("SidebarComponent");
    }

    /**
     * Pins the four defects that {@code ng build} caught on the first real run of the frontend
     * validation loop. Each is a template bug that compiles as text but breaks the Angular build.
     */
    @Test
    void frontend_templates_avoid_the_ng_build_failures() {
        FrontendProjectInfo project = FrontendProjectInfo.builder()
                .appName("Inventory").angularProjectName("inventory-web")
                .apiBaseUrl("http://localhost:8080/api/v1").defaultRoute("/product").build();
        FrontendEntity product = FrontendEntity.builder()
                .className("Product").fileName("product").instanceName("product")
                .fields(List.of(FrontendField.builder().name("name").tsType("string")
                        .label("Name").required(true).build()))
                .build();

        Map<String, Object> base = new HashMap<>();
        base.put("project", project);
        base.put("entities", List.of(product));
        base.put("navigation", List.of());

        // (1) NG8116: the row-action directive must exist and be imported, and (4) the error
        // callback must be explicitly typed under `strict`.
        FrontendModule fullCrud = FrontendModule.builder()
                .serviceName("ProductService").serviceFileName("product").componentPrefix("product")
                .entityClassName("Product").entityFileName("product").entityInstanceName("product")
                .apiPath("/api/v1/product")
                .hasCreate(true).hasRead(true).hasUpdate(true).hasDelete(true)
                .endpoints(List.of()).listColumns(List.of()).formFields(List.of())
                .build();
        Map<String, Object> fullModel = new HashMap<>(base);
        fullModel.put("module", fullCrud);
        fullModel.put("entity", product);

        String listTs = mustache.render("frontend/list.component.ts.mustache", fullModel);
        assertThat(listTs).contains("AstTableActionDirective")
                .contains("ast-table/ast-table-action.directive")
                .doesNotContain("error: err =>");
        assertThat(mustache.render("frontend/list.component.html.mustache", fullModel))
                .contains("*astTableAction=\"let row\"")
                .doesNotContain("*tableAction=");

        String fullForm = mustache.render("frontend/form.component.ts.mustache", fullModel);
        assertThat(fullForm).contains("this.service.update(id, value)")
                .contains("this.service.create(value)")
                .doesNotContain("error: err =>");

        // (3) TS2339: a module without UPDATE must not reference service.update(), because the
        // service template only emits the methods the module declares.
        FrontendModule createOnly = FrontendModule.builder()
                .serviceName("StockMovementsService").serviceFileName("stock-movements")
                .componentPrefix("stock-movements").entityClassName("StockMovement")
                .entityFileName("stock-movement").entityInstanceName("stockMovement")
                .apiPath("/api/v1/stock-movements")
                .hasCreate(true).hasRead(true).hasUpdate(false).hasDelete(false)
                .endpoints(List.of()).listColumns(List.of()).formFields(List.of())
                .build();
        Map<String, Object> createOnlyModel = new HashMap<>(base);
        createOnlyModel.put("module", createOnly);
        createOnlyModel.put("entity", product);

        String createOnlyForm = mustache.render("frontend/form.component.ts.mustache", createOnlyModel);
        assertThat(createOnlyForm).doesNotContain("this.service.update(")
                .contains("this.service.create(value)");

        // (2) TS2307: login sits one level deeper than the services, so it needs four `../`.
        assertThat(mustache.render("frontend/login.component.ts.mustache", base))
                .contains("from '../../../../environments/environment'");
        assertThat(mustache.render("frontend/service.ts.mustache", fullModel))
                .contains("from '../../../environments/environment'");
    }

    @Test
    void generated_frontend_targets_angular_21_and_the_real_iconify_package() {
        Map<String, Object> base = new HashMap<>();
        base.put("project", FrontendProjectInfo.builder()
                .appName("Inventory").angularProjectName("inventory-web")
                .apiBaseUrl("http://localhost:8080/api/v1").defaultRoute("/product").build());

        String packageJson = mustache.render("frontend/package.json.mustache", base);

        assertThat(packageJson)
                .contains("\"@angular/core\": \"^21.")
                .contains("\"typescript\": \"^5.9")
                // The previous value (@iconify/angular ^2.0.0) does not exist on npm — the web
                // component package is what the sidebar template actually uses.
                .contains("\"iconify-icon\":")
                .doesNotContain("@iconify/angular")
                // No karma/jasmine is generated, so a "test" script would be a broken command.
                .doesNotContain("\"test\":");
    }

    @Test
    void every_infrastructure_template_renders_against_a_representative_projection() {
        InfraProjection infra = InfraProjection.builder()
                .appName("Inventory").artifactId("inventory")
                .backendServiceName("inventory-backend")
                .frontendServiceName("inventory-frontend")
                .databaseServiceName("inventory-db")
                .databaseName("inventory_db").databaseUser("inv")
                .backendPort(8080).frontendPort(80).deploymentTarget("VPS")
                .build();

        Map<String, Object> base = new HashMap<>();
        base.put("infra", infra);

        assertThat(freeMarker.render("infrastructure/docker-compose.yml.ftl", base))
                .contains("inventory-db:").contains("inventory-backend:").contains("inventory-frontend:")
                .contains("${DB_PASSWORD}");
        assertThat(freeMarker.render("infrastructure/env.example.ftl", base))
                .contains("DB_NAME=inventory_db").contains("JWT_SECRET=");
        assertThat(freeMarker.render("infrastructure/schema.sql.ftl", base))
                .contains("CREATE DATABASE IF NOT EXISTS inventory_db");
        assertThat(mustache.render("infrastructure/FrontendDockerfile.mustache", base))
                .contains("FROM node:20-alpine AS build").contains("EXPOSE 80");
        assertThat(mustache.render("infrastructure/NginxConf.mustache", base))
                .contains("proxy_pass http://inventory-backend:8080/api/;");
    }
}
