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
