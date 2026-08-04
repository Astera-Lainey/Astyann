package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.PcsfBusinessRule;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    private static FieldValue<String> fv(String v) {
        return FieldValue.<String>builder().value(v).build();
    }

    @Test
    void systemPromptDemandsJsonOnly() {
        assertThat(builder.systemPrompt())
                .contains("JSON")
                .contains("serviceImpl")
                .contains("additionalFiles");
    }

    @Test
    void userPromptIncludesModuleEntityRulesAndRag() {
        BackendModule module = BackendModule.builder()
                .entityClassName("Product")
                .requestMapping("/api/v1/products")
                .serviceName("ProductService")
                .build();
        PcsfModule pcsfModule = PcsfModule.builder().name(fv("Products")).build();
        List<PcsfBusinessRule> rules = List.of(
                PcsfBusinessRule.builder().description(fv("Price must be positive")).build());

        String prompt = builder.userPrompt(module, pcsfModule, null, rules,
                "class ProductServiceImpl {}", "interface ProductRepository {}",
                "class ProductController {}", "class Product {}",
                List.of("findByName(String)"),
                Map.of("Product", "class Product {}"),
                Map.of("ProductRepository", "interface ProductRepository {}"),
                List.of("CreateProductDto"),
                "RAG_SNIPPET_XYZ");

        assertThat(prompt)
                .contains("## MODULE")
                .contains("Product")
                .contains("## AVAILABLE TYPES")
                .contains("## BUSINESS RULES")
                .contains("Price must be positive")
                .contains("RAG_SNIPPET_XYZ");
    }

    private static BackendModule productModule() {
        return BackendModule.builder()
                .entityClassName("Product")
                .requestMapping("/api/v1/products")
                .serviceName("ProductService")
                .build();
    }

    private String crossModulePrompt(Map<String, List<String>> serviceApis,
                                     List<ModuleDependencyResolver.Collaborator> collaborators,
                                     List<ModuleDependencyResolver.SharedRule> sharedRules) {
        return builder.userPrompt(productModule(),
                PcsfModule.builder().name(fv("Products")).build(), null, List.of(),
                "class ProductServiceImpl {}", "interface ProductRepository {}",
                "class ProductController {}", "class Product {}",
                List.of(), Map.of("Product", "class Product {}"),
                Map.of("ProductRepository", "interface ProductRepository {}"),
                List.of("ProductResponseDto"), null,
                serviceApis, collaborators, sharedRules);
    }

    @Test
    void promptOffersOtherModuleServicesAsInjectableCollaborators() {
        String prompt = crossModulePrompt(
                Map.of("StockMovementService", List.of(
                        "StockMovementResponseDto recordMovement(UUID productId, int quantity)")),
                List.of(new ModuleDependencyResolver.Collaborator(
                        "StockMovement", "Stock Movements", "StockMovementService",
                        "one-to-many", true, false)),
                List.of());

        assertThat(prompt)
                .contains("## OTHER MODULE SERVICES YOU MAY INJECT")
                .contains("StockMovementService")
                .contains("recordMovement(UUID productId, int quantity)")
                // A cycle makes Spring refuse to start the context, so the escape hatch is stated.
                .contains("CIRCULAR DEPENDENCY RULE")
                .contains("Repository");

        assertThat(prompt)
                .contains("## MODULE DEPENDENCIES")
                .contains("Product -> StockMovement (one-to-many)")
                .contains("owned by module \"Stock Movements\" via StockMovementService");
    }

    @Test
    void promptNamesTheFarSideOfARuleTwoModulesShare() {
        String prompt = crossModulePrompt(Map.of(), List.of(),
                List.of(new ModuleDependencyResolver.SharedRule(
                        "Reserving stock must record a movement",
                        "Stock Movements", "StockMovementService")));

        assertThat(prompt)
                .contains("## RULES SHARED WITH OTHER MODULES")
                .contains("Reserving stock must record a movement")
                .contains("other side: module \"Stock Movements\", StockMovementService")
                // Each side implements half; duplicating the other half is the failure mode.
                .contains("Implement only this module's half");
    }

    @Test
    void promptOmitsCrossModuleSectionsEntirelyWhenThereAreNone() {
        // A single-module project must produce exactly the prompt it did before this existed.
        String prompt = crossModulePrompt(Map.of(), List.of(), List.of());

        assertThat(prompt)
                .doesNotContain("OTHER MODULE SERVICES")
                .doesNotContain("MODULE DEPENDENCIES")
                .doesNotContain("RULES SHARED WITH OTHER MODULES")
                .doesNotContain("CIRCULAR DEPENDENCY RULE");
    }

    @Test
    void promptNeverOffersAModuleItsOwnServiceToInject() {
        String prompt = crossModulePrompt(
                Map.of("ProductService", List.of("ProductResponseDto getProductById(UUID id)")),
                List.of(), List.of());

        assertThat(prompt).doesNotContain("getProductById(UUID id)");
    }

    @Test
    void compileFixPromptsCarryErrorsAndPackageHint() {
        assertThat(builder.systemPromptForCompileFix()).contains("fixedSource");
        String user = builder.userPromptForCompileFix("class Foo {}",
                List.of("Foo.java:1:1 cannot find symbol"), "com.example.app");
        assertThat(user)
                .contains("COMPILE ERRORS")
                .contains("cannot find symbol")
                .contains("com/example/app/dto/YourType.java");
    }

    @Test
    void compileFixPromptCarriesRelatedFilesAsReadOnlyContext() {
        // Without the interface in view, a signature mismatch is unfixable: satisfying the impl
        // breaks the interface and the loop oscillates.
        String prompt = builder.userPromptForCompileFix(
                "class ProductServiceImpl {}",
                List.of("ProductServiceImpl.java:46 incompatible types: Long cannot be converted to UUID"),
                "com.example.app",
                new java.util.LinkedHashMap<>(java.util.Map.of(
                        "service/ProductService.java  (interface this class implements)",
                        "interface ProductService { ProductResponseDto getProductById(UUID id); }")));

        assertThat(prompt)
                .contains("RELATED FILES")
                .contains("read-only")
                .contains("interface this class implements")
                .contains("getProductById(UUID id)")
                // The model must be told which file it is actually editing.
                .contains("CURRENT FILE (this is the one to fix)")
                // And be given an escape hatch rather than guessing.
                .contains("leave \"fixedSource\" unchanged");
    }

    @Test
    void compileFixPromptOmitsTheContextSectionWhenThereIsNone() {
        String prompt = builder.userPromptForCompileFix("class Foo {}",
                List.of("Foo.java:1 boom"), "com.example.app");
        assertThat(prompt).doesNotContain("RELATED FILES");
    }

    @Test
    void angularTemplateFixPromptAsksForHtmlNotTypeScript() {
        assertThat(builder.systemPromptForAngularTemplateFix())
                .containsIgnoringCase("template")
                .contains("TypeScript")   // ...as something NOT to return
                .contains("must remain an Angular template, not a component class");

        String user = builder.userPromptForAngularTemplateFix(
                "<div><iconify-icon></iconify-icon></div>",
                List.of("sidebar.component.html:6:8 NG8001 'iconify-icon' is not a known element"),
                "src/app/layout/sidebar/sidebar.component.html");

        assertThat(user)
                .contains("TEMPLATE FILE")
                .contains("sidebar.component.html")
                .contains("NG8001")
                .contains("Return the full corrected HTML template");
    }

    @Test
    void tsFixPromptsCarryErrorsAndPath() {
        assertThat(builder.systemPromptForTsFix()).containsIgnoringCase("TypeScript");
        String user = builder.userPromptForTsFix("export class Foo {}",
                List.of("foo.ts:1:1 TS2304 Cannot find name"), "src/app/foo.ts");
        assertThat(user)
                .contains("TYPE-CHECK ERRORS")
                .contains("Cannot find name")
                .contains("src/app/foo.ts")
                .contains("export class Foo");
    }
}
