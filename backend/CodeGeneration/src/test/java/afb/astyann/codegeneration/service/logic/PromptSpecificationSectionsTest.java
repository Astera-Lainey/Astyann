package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.service.logic.SpecificationLoader.EntityBehaviour;
import afb.astyann.codegeneration.service.logic.SpecificationLoader.ModuleSpecification;
import afb.astyann.codegeneration.service.logic.SpecificationLoader.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The approved documents used to reach the model as one truncated blob of prose retrieved from a
 * vector store. These pin the typed sections that replaced it, and the fact that a project with no
 * document content still produces the prompt it always did.
 */
class PromptSpecificationSectionsTest {

    private final PromptBuilder builder = new PromptBuilder();

    private static BackendModule module() {
        return BackendModule.builder()
                .controllerName("ProductManagementController").serviceName("ProductService")
                .serviceImplName("ProductServiceImpl").repositoryName("ProductRepository")
                .requestMapping("/api/v1/products").packageName("com.example")
                .entityClassName("Product").entityInstanceName("product")
                .build();
    }

    private String prompt(ModuleSpecification spec) {
        return builder.userPrompt(module(), null, null, List.of(),
                "class ProductServiceImpl {}", "interface ProductRepository {}",
                "class ProductController {}", "class Product {}",
                List.of(), Map.of(), Map.of(), List.of(), null,
                Map.of(), List.of(), List.of(), spec);
    }

    @Test
    void requirementsAppearWithTheirIdsSoTheyCanBeCited() {
        String prompt = prompt(new ModuleSpecification(
                List.of(new Requirement("FR-01", "Archive a product that has movements")),
                false, List.of(), List.of(), List.of()));

        assertThat(prompt)
                .contains("## FUNCTIONAL REQUIREMENTS FOR THIS MODULE")
                .contains("[FR-01] Archive a product that has movements")
                .contains("Cite the requirement id");
    }

    @Test
    void unattributedRequirementsAreLabelledAsProjectWide() {
        // Presenting the project's whole list as "for this module" would invite the model to
        // implement another module's requirements here.
        String prompt = prompt(new ModuleSpecification(
                List.of(new Requirement("FR-09", "Something about invoices")),
                true, List.of(), List.of(), List.of()));

        assertThat(prompt)
                .contains("## FUNCTIONAL REQUIREMENTS (project-wide")
                .contains("implement only those that belong here")
                .doesNotContain("## FUNCTIONAL REQUIREMENTS FOR THIS MODULE");
    }

    @Test
    void responsibilitiesBehaviourAndConstraintsEachGetTheirOwnSection() {
        String prompt = prompt(new ModuleSpecification(
                List.of(), false,
                List.of("Owns the catalogue — Validate SKU uniqueness"),
                List.of(new EntityBehaviour("Product", "archive(), adjustStock()")),
                List.of(new Requirement("NFR-01", "Respond within 2s"))));

        assertThat(prompt)
                .contains("## MODULE RESPONSIBILITIES (from the approved design document)")
                .contains("Validate SKU uniqueness")
                .contains("## ENTITY BEHAVIOUR (from the approved functional analysis)")
                .contains("archive(), adjustStock()")
                .contains("## NON-FUNCTIONAL CONSTRAINTS (project-wide)")
                .contains("[NFR-01] Respond within 2s");
    }

    @Test
    void aProjectWithNoDocumentContentGetsNoneOfTheSections() {
        String prompt = prompt(ModuleSpecification.empty());

        assertThat(prompt)
                .doesNotContain("## FUNCTIONAL REQUIREMENTS")
                .doesNotContain("## MODULE RESPONSIBILITIES")
                .doesNotContain("## ENTITY BEHAVIOUR")
                .doesNotContain("## NON-FUNCTIONAL CONSTRAINTS")
                // ...and still carries everything it always did.
                .contains("## MODULE")
                .contains("## CURRENT STUB — ServiceImpl");
    }

    @Test
    void retrievedProseIsLabelledAsSupplementaryRatherThanAsTheDocumentation() {
        // It is no longer the only document channel, and calling it "related documentation" implied
        // it was the whole of what the documents say.
        String prompt = builder.userPrompt(module(), null, null, List.of(),
                "class ProductServiceImpl {}", "interface ProductRepository {}",
                "class ProductController {}", "class Product {}",
                List.of(), Map.of(), Map.of(), List.of(), "some retrieved prose",
                Map.of(), List.of(), List.of(), ModuleSpecification.empty());

        assertThat(prompt).contains("## ADDITIONAL DOCUMENTATION EXCERPTS")
                .contains("some retrieved prose");
    }

    @Test
    void theOlderOverloadStillWorksForCallersThatHaveNoSpecification() {
        String prompt = builder.userPrompt(module(), null, null, List.of(),
                "class ProductServiceImpl {}", "interface ProductRepository {}",
                "class ProductController {}", "class Product {}",
                List.of(), Map.of(), Map.of(), List.of(), null);

        assertThat(prompt).contains("## MODULE").doesNotContain("## FUNCTIONAL REQUIREMENTS");
    }
}
