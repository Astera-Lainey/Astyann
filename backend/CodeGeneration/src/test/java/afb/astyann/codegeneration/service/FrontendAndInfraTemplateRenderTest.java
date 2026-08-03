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
                                .inputType("text").required(true)
                                .validatorsExpression("Validators.required").hasValidators(true).build(),
                        FrontendFormField.builder().label("Price").fieldName("price")
                                .inputType("number").required(true)
                                .validatorsExpression("Validators.required").hasValidators(true).build()))
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
                .contains("class ProductService")
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

    /**
     * Custom (non-CRUD) use-case actions must reach the UI. The backend exposes them as
     * {@code POST /{id}/<action>}; before this the frontend dropped them entirely, so the generated
     * app could not invoke endpoints the generated API provided.
     */
    @Test
    void custom_use_case_actions_reach_the_frontend() {
        FrontendProjectInfo project = FrontendProjectInfo.builder()
                .appName("Inventory").angularProjectName("inventory-web")
                .apiBaseUrl("http://localhost:8080/api/v1").defaultRoute("/stock-movements").build();
        FrontendEntity movement = FrontendEntity.builder()
                .className("StockMovement").fileName("stock-movement").instanceName("stockMovement")
                .fields(List.of(FrontendField.builder().name("quantity").tsType("number")
                        .label("Quantity").required(true).build()))
                .build();

        FrontendEndpoint action = FrontendEndpoint.builder()
                .methodName("recordAStockEntry").httpMethod("POST").path("/{id}/recordAStockEntry")
                .hasPathId(true).hasBody(false).returnType("StockMovement")
                .crud(false).actionSegment("recordAStockEntry")
                .methodNamePascal("RecordAStockEntry").label("Record a stock entry")
                .build();

        FrontendModule module = FrontendModule.builder()
                .serviceName("StockMovementsService").serviceFileName("stock-movements")
                .componentPrefix("stock-movements").entityClassName("StockMovement")
                .entityFileName("stock-movement").entityInstanceName("stockMovement")
                .apiPath("/api/v1/stock-movements")
                .hasCreate(true).hasRead(true).hasUpdate(false).hasDelete(true)
                .endpoints(List.of(action))
                .customActions(List.of(action)).hasCustomActions(true)
                .listColumns(List.of()).formFields(List.of())
                .build();

        Map<String, Object> model = new HashMap<>();
        model.put("project", project);
        model.put("entities", List.of(movement));
        model.put("navigation", List.of());
        model.put("module", module);
        model.put("entity", movement);

        // Service gains a typed method hitting POST /{id}/<segment>.
        String service = mustache.render("frontend/service.ts.mustache", model);
        assertThat(service)
                .contains("recordAStockEntry(id: string): Observable<StockMovement>")
                .contains("`${this.baseUrl}/${id}/recordAStockEntry`");

        // Component gains a handler plus the in-flight guard.
        String listTs = mustache.render("frontend/list.component.ts.mustache", model);
        assertThat(listTs)
                .contains("readonly actionInFlight = signal<string | null>(null);")
                .contains("onRecordAStockEntry(id: string): void")
                .contains("this.service.recordAStockEntry(id)");

        // Template gains a row button wired to that handler.
        assertThat(mustache.render("frontend/list.component.html.mustache", model))
                .contains("(click)=\"onRecordAStockEntry(row.id)\"")
                .contains("[disabled]=\"actionInFlight() === row.id\"")
                .contains(">Record a stock entry</button>");
    }

    /**
     * Validators and badge variants are raw TypeScript containing quotes, so they must be
     * interpolated with a triple-mustache — {@code DefaultMustacheFactory} HTML-escapes {@code {{ }}}
     * and would turn {@code '} into {@code &#39;}, producing source that will not compile.
     */
    @Test
    void validators_and_badge_variants_render_unescaped() {
        Map<String, Object> model = new HashMap<>();
        model.put("project", FrontendProjectInfo.builder().appName("CRM")
                .angularProjectName("crm-web").apiBaseUrl("http://x").defaultRoute("/order").build());
        model.put("entities", List.of());
        model.put("navigation", List.of());
        model.put("entity", FrontendEntity.builder().className("Order").fileName("order")
                .instanceName("order").fields(List.of()).build());
        model.put("module", FrontendModule.builder()
                .serviceName("OrderService").serviceFileName("order").componentPrefix("order")
                .entityClassName("Order").entityFileName("order").entityInstanceName("order")
                .apiPath("/api/v1/order")
                .hasCreate(true).hasRead(true).hasUpdate(true).hasDelete(true)
                .endpoints(List.of()).customActions(List.of()).hasCustomActions(false)
                .listColumns(List.of(
                        FrontendColumn.builder().label("Reference").fieldName("reference").build(),
                        FrontendColumn.builder().label("Status").fieldName("status").badge(true)
                                .variantsExpression("{ 'DRAFT': 'warning', 'APPROVED': 'success' }").build()))
                .formFields(List.of(
                        FrontendFormField.builder().label("Name").fieldName("name").inputType("text")
                                .required(true)
                                .validatorsExpression("Validators.required, Validators.maxLength(120)")
                                .hasValidators(true).build(),
                        FrontendFormField.builder().label("Note").fieldName("note").inputType("text")
                                .required(false).hasValidators(false).build()))
                .build());

        String listTs = mustache.render("frontend/list.component.ts.mustache", model);
        assertThat(listTs)
                .contains("{ label: 'Status', fieldName: 'status', kind: 'badge' as const, "
                        + "variants: { 'DRAFT': 'warning', 'APPROVED': 'success' } }")
                .contains("{ label: 'Reference', fieldName: 'reference' }")
                .doesNotContain("&#39;");

        String formTs = mustache.render("frontend/form.component.ts.mustache", model);
        assertThat(formTs)
                .contains("name: [null, [Validators.required, Validators.maxLength(120)]],")
                // A field with no constraints must not emit an empty validator array.
                .contains("note: [null],")
                .doesNotContain("&#39;");
    }

    @Test
    void modules_without_custom_actions_emit_no_action_scaffolding() {
        Map<String, Object> model = new HashMap<>();
        model.put("project", FrontendProjectInfo.builder().appName("Inventory")
                .angularProjectName("inventory-web").apiBaseUrl("http://x").defaultRoute("/p").build());
        model.put("entities", List.of());
        model.put("navigation", List.of());
        model.put("entity", FrontendEntity.builder().className("Product").fileName("product")
                .instanceName("product").fields(List.of()).build());
        model.put("module", FrontendModule.builder()
                .serviceName("ProductService").serviceFileName("product").componentPrefix("product")
                .entityClassName("Product").entityFileName("product").entityInstanceName("product")
                .apiPath("/api/v1/product")
                .hasCreate(true).hasRead(true).hasUpdate(true).hasDelete(true)
                .endpoints(List.of()).customActions(List.of()).hasCustomActions(false)
                .listColumns(List.of()).formFields(List.of())
                .build());

        assertThat(mustache.render("frontend/list.component.ts.mustache", model))
                .doesNotContain("actionInFlight");
        assertThat(mustache.render("frontend/list.component.html.mustache", model))
                .doesNotContain("actionInFlight");
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
                .contains("inventory-db:").contains("inventory-backend:").contains("inventory-frontend:");
        assertThat(freeMarker.render("infrastructure/env.example.ftl", base))
                .contains("DB_NAME=inventory_db").contains("JWT_SECRET=");
        assertThat(freeMarker.render("infrastructure/schema.sql.ftl", base))
                .contains("CREATE DATABASE IF NOT EXISTS inventory_db");
    }

    @Test
    void compose_refuses_to_start_without_the_secrets_instead_of_failing_as_unhealthy() {
        InfraProjection infra = InfraProjection.builder()
                .appName("Inventory").artifactId("inventory")
                .backendServiceName("inventory-backend")
                .frontendServiceName("inventory-frontend")
                .databaseServiceName("inventory-db")
                .databaseName("inventory_db").databaseUser("inv")
                .backendPort(8080).frontendPort(4200).deploymentTarget("VPS")
                .build();
        Map<String, Object> base = new HashMap<>();
        base.put("infra", infra);

        String compose = freeMarker.render("infrastructure/docker-compose.yml.ftl", base);

        // Blank secrets let MySQL start and then die with "Database is uninitialized and password
        // option is not specified" — surfacing to the user only as "container is unhealthy", with
        // nothing pointing at the real cause. `:?` makes compose name the variable up front.
        assertThat(compose)
                .contains("${DB_PASSWORD:?missing - copy .env.example to .env and set DB_PASSWORD}")
                .contains("${DB_ROOT_PASSWORD:?missing - copy .env.example to .env and set DB_ROOT_PASSWORD}")
                .contains("${JWT_SECRET:?missing - copy .env.example to .env and set JWT_SECRET")
                // A bare reference would silently default to an empty string.
                .doesNotContain("${DB_PASSWORD}")
                .doesNotContain("${DB_ROOT_PASSWORD}")
                .doesNotContain("${JWT_SECRET}");

        // Non-secret settings keep working defaults so a filled-in .env stays minimal.
        assertThat(compose)
                .contains("${DB_NAME:-inventory_db}")
                .contains("${DB_USER:-inv}")
                // .env.example advertises these two, so compose has to actually honour them.
                .contains("${BACKEND_PORT:-8080}:8080")
                .contains("${FRONTEND_PORT:-4200}:80");

        // Compose warns that `version` is obsolete and ignores it.
        assertThat(compose).doesNotContain("version:");
    }

    /** The model the frontend layer renders its container files from — {@code project} + {@code infra}. */
    private Map<String, Object> containerModel() {
        Map<String, Object> base = new HashMap<>();
        base.put("project", FrontendProjectInfo.builder()
                .appName("Inventory").angularProjectName("inventory-web")
                .apiBaseUrl("http://localhost:8080/api/v1").defaultRoute("/products").build());
        base.put("infra", InfraProjection.builder()
                .appName("Inventory").artifactId("inventory")
                .backendServiceName("inventory-backend").backendPort(8080)
                .frontendServiceName("inventory-frontend").frontendPort(4200)
                .build());
        return base;
    }

    @Test
    void frontend_dockerfile_installs_without_a_lockfile_and_copies_the_real_build_output() {
        String dockerfile = mustache.render("frontend/Dockerfile.mustache", containerModel());

        // No package-lock.json is generated, and `npm ci` refuses to run without one — so the
        // install must fall back to `npm install` rather than failing the build outright.
        assertThat(dockerfile)
                .contains("npm install --no-audit --no-fund")
                .contains("if [ -f package-lock.json ]");

        // The Angular application builder emits dist/<project>/browser. Copying dist/ itself would
        // leave index.html two directories below nginx's root and every route would 404. The name
        // comes from the same `project` object angular.json's outputPath does, so they cannot drift.
        assertThat(dockerfile)
                .contains("COPY --from=build /app/dist/inventory-web/browser /usr/share/nginx/html");

        // nginx listens on 80 regardless of which host port compose publishes.
        assertThat(dockerfile).contains("EXPOSE 80");
    }

    @Test
    void frontend_dockerfile_survives_a_flaky_network() {
        String dockerfile = mustache.render("frontend/Dockerfile.mustache", containerModel());

        // npm's defaults are 2 retries with short timeouts — not enough for a ~1000-package
        // install over a connection that stalls, which is how ECONNRESET shows up in Docker.
        assertThat(dockerfile)
                .contains("NPM_CONFIG_FETCH_RETRIES=5")
                .contains("NPM_CONFIG_FETCH_TIMEOUT=600000");

        // Three attempts, and the last one unguarded so a total failure still fails the build
        // instead of producing an image with no node_modules.
        assertThat(dockerfile).containsSubsequence(
                "npm_install()", "|| {", "npm_install; }", "|| {", "npm_install; }");
        assertThat(dockerfile).doesNotContain("|| true");

        // Line continuations would carry a stray \r into /bin/sh on a CRLF checkout.
        assertThat(dockerfile.lines().filter(l -> l.startsWith("RUN ") || l.startsWith("ENV "))
                .filter(l -> l.endsWith("\\")).toList()).isEmpty();
    }

    @Test
    void frontend_nginx_conf_renders_with_the_backend_service_as_the_api_upstream() {
        assertThat(mustache.render("frontend/nginx.conf.mustache", containerModel()))
                .contains("proxy_pass http://inventory-backend:8080/api/;")
                // SPA fallback: without it a deep-linked route 404s on refresh.
                .contains("try_files $uri $uri/ /index.html;");
    }

    /**
     * docker-compose declares {@code context: ../frontend} with {@code dockerfile: Dockerfile}, and
     * COPY paths resolve against that context — so these three must be emitted into the frontend
     * layer, next to the sources, not into the infrastructure layer.
     */
    @Test
    void container_files_are_copied_relative_to_the_frontend_build_context() {
        assertThat(mustache.render("frontend/Dockerfile.mustache", containerModel()))
                // A bare filename, so it only resolves if nginx.conf sits in the frontend layer
                // alongside package.json — not under infrastructure/, where Docker never looks.
                .contains("COPY nginx.conf /etc/nginx/conf.d/default.conf")
                .contains("COPY package.json package-lock.json* ./");
    }

    @Test
    void frontend_dockerignore_keeps_the_host_node_modules_out_of_the_image() {
        // `COPY . .` runs after the install; without this the host's node_modules (native binaries
        // built for the developer's OS) would overwrite the one installed inside alpine.
        assertThat(mustache.render("frontend/dockerignore.mustache", new HashMap<>()))
                .contains("node_modules")
                .contains("dist")
                .contains(".angular");
    }
}
