package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.client.DocumentServiceClient;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.dto.ApiResponse;
import afb.astyann.codegeneration.dto.DocumentContentDTO;
import afb.astyann.codegeneration.service.logic.SpecificationLoader.ModuleSpecification;
import afb.astyann.codegeneration.service.logic.SpecificationLoader.ProjectSpecification;
import afb.astyann.codegeneration.service.logic.SpecificationLoader.Requirement;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Until this existed, an approved document reached generated code only through a five-chunk RAG
 * lookup over prose extracted back out of a Word file. These cover reading the JSON the document
 * was built from instead, and narrowing it to one module.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpecificationLoaderTest {

    @Mock private DocumentServiceClient documentServiceClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID FA_SNAPSHOT = UUID.randomUUID();
    private static final UUID SRS_SNAPSHOT = UUID.randomUUID();

    private SpecificationLoader loader() {
        return new SpecificationLoader(documentServiceClient, mapper);
    }

    /**
     * Content arrives as a {@code Map} because that is what survives the Jackson 2 / Jackson 3
     * boundary between these services — see {@code DocumentContentWireFormatTest}. Building the
     * fixture the same way keeps this test honest about what the loader really receives.
     */
    private void stub(String type, UUID snapshotId, String json) {
        try {
            Map<String, Object> content =
                    mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            when(documentServiceClient.approvedContent(eq(PROJECT), eq(type))).thenReturn(
                    ApiResponse.<DocumentContentDTO>builder().status(200)
                            .data(DocumentContentDTO.builder()
                                    .documentId(UUID.randomUUID()).type(type).status("APPROVED")
                                    .snapshotId(snapshotId).content(content).build())
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubMissing(String type) {
        when(documentServiceClient.approvedContent(eq(PROJECT), eq(type)))
                .thenThrow(new RuntimeException("404 Not Found"));
    }

    private static BackendModule module(String controllerName, String entityClass) {
        return BackendModule.builder()
                .controllerName(controllerName).serviceName("S").serviceImplName("SI")
                .repositoryName("R").requestMapping("/api/v1/x").packageName("p")
                .entityClassName(entityClass).entityInstanceName("x")
                .build();
    }

    @Test
    void readsRequirementsAndBehaviourOutOfTheApprovedDocuments() {
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT, """
                {"fr":[{"id":"FR-01","description":"Record a stock entry for a Product"}],
                 "nfr":[{"id":"NFR-01","description":"Respond within 2s"}],
                 "entity":[{"name":"Product","attributes":"sku, name","methods":"archive(), adjustStock()"}]}""");
        stub("DESIGN_DOCUMENT", UUID.randomUUID(), """
                {"module":[{"name":"Product Management","description":"Owns the catalogue",
                            "responsibilities":"Validate SKU uniqueness"}]}""");
        stubMissing("SRS");

        ProjectSpecification spec = loader().load(PROJECT);

        assertThat(spec.functionalRequirements()).extracting(Requirement::id).containsExactly("FR-01");
        assertThat(spec.nonFunctionalRequirements()).extracting(Requirement::id).containsExactly("NFR-01");
        assertThat(spec.entityBehaviour()).singleElement()
                .satisfies(e -> assertThat(e.methods()).contains("archive()"));
        assertThat(spec.moduleResponsibilities()).singleElement()
                .satisfies(m -> assertThat(m.moduleName()).isEqualTo("Product Management"));
    }

    @Test
    void collectsTheSnapshotsTheContentCameFromSoRetrievalCanBeScopedToThem() {
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT, "{\"fr\":[]}");
        stub("SRS", SRS_SNAPSHOT, "{\"fr\":[]}");
        stubMissing("DESIGN_DOCUMENT");

        assertThat(loader().load(PROJECT).snapshotIds())
                .containsExactlyInAnyOrder(FA_SNAPSHOT, SRS_SNAPSHOT);
    }

    @Test
    void aRequirementStatedInBothTheSrsAndTheFunctionalAnalysisIsShownOnce() {
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT,
                "{\"fr\":[{\"id\":\"FR-01\",\"description\":\"From the analysis\"}]}");
        stub("SRS", SRS_SNAPSHOT,
                "{\"fr\":[{\"id\":\"FR-01\",\"description\":\"Restated in the SRS\"}]}");
        stubMissing("DESIGN_DOCUMENT");

        List<Requirement> frs = loader().load(PROJECT).functionalRequirements();

        assertThat(frs).hasSize(1);
        // The functional analysis is read first, so its wording is the one kept.
        assertThat(frs.get(0).description()).isEqualTo("From the analysis");
    }

    @Test
    void everyDocumentBeingUnavailableIsNotAFailure() {
        // 404 (never approved) and 409 (generated before content was persisted) are both ordinary.
        stubMissing("SRS");
        stubMissing("FUNCTIONAL_ANALYSIS");
        stubMissing("DESIGN_DOCUMENT");

        ProjectSpecification spec = loader().load(PROJECT);

        assertThat(spec.isEmpty()).isTrue();
        assertThat(spec.snapshotIds()).isEmpty();
    }

    @Test
    void attributesARequirementToTheModuleItNames() {
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT, """
                {"fr":[{"id":"FR-01","description":"Archive a Product that has movements"},
                       {"id":"FR-02","description":"Deactivate a User account"}]}""");
        stubMissing("SRS");
        stubMissing("DESIGN_DOCUMENT");
        SpecificationLoader loader = loader();

        ModuleSpecification spec = loader.forModule(
                loader.load(PROJECT), module("ProductManagementController", "Product"));

        assertThat(spec.functionalRequirements()).extracting(Requirement::id).containsExactly("FR-01");
        assertThat(spec.requirementsAreProjectWide()).isFalse();
    }

    @Test
    void passesEveryRequirementThroughWhenNoneNamesTheModule() {
        // Requirements carry no module reference, so attribution is by mention. Dropping everything
        // when nothing matches would hide the specification entirely; the flag lets the prompt say
        // these are unattributed rather than claim they belong here.
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT, """
                {"fr":[{"id":"FR-01","description":"Something about invoices"},
                       {"id":"FR-02","description":"Something about payments"}]}""");
        stubMissing("SRS");
        stubMissing("DESIGN_DOCUMENT");
        SpecificationLoader loader = loader();

        ModuleSpecification spec = loader.forModule(
                loader.load(PROJECT), module("ProductManagementController", "Product"));

        assertThat(spec.functionalRequirements()).hasSize(2);
        assertThat(spec.requirementsAreProjectWide()).isTrue();
    }

    @Test
    void entityBehaviourIsMatchedToTheModulesOwnEntity() {
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT, """
                {"entity":[{"name":"Product","methods":"archive()"},
                           {"name":"User","methods":"deactivate()"}]}""");
        stubMissing("SRS");
        stubMissing("DESIGN_DOCUMENT");
        SpecificationLoader loader = loader();

        ModuleSpecification spec = loader.forModule(
                loader.load(PROJECT), module("ProductManagementController", "Product"));

        assertThat(spec.entityBehaviour()).singleElement()
                .satisfies(e -> assertThat(e.entityName()).isEqualTo("Product"));
    }

    @Test
    void anEntityWithNoDeclaredMethodsContributesNothing() {
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT, "{\"entity\":[{\"name\":\"Product\",\"methods\":\"\"}]}");
        stubMissing("SRS");
        stubMissing("DESIGN_DOCUMENT");
        SpecificationLoader loader = loader();

        assertThat(loader.forModule(loader.load(PROJECT),
                module("ProductManagementController", "Product")).entityBehaviour()).isEmpty();
    }

    @Test
    void responsibilitiesAreMatchedToTheModuleByName() {
        stub("DESIGN_DOCUMENT", UUID.randomUUID(), """
                {"module":[{"name":"Product Management","description":"Catalogue",
                            "responsibilities":"Validate SKU uniqueness"},
                           {"name":"User Management","description":"Accounts",
                            "responsibilities":"Enforce role rules"}]}""");
        stubMissing("SRS");
        stubMissing("FUNCTIONAL_ANALYSIS");
        SpecificationLoader loader = loader();

        ModuleSpecification spec = loader.forModule(
                loader.load(PROJECT), module("ProductManagementController", "Product"));

        // "User Management" must not match on the word both names share.
        assertThat(spec.responsibilities()).singleElement()
                .satisfies(r -> assertThat(r).contains("SKU uniqueness").doesNotContain("role rules"));
    }

    @Test
    void aGenericWordSharedByEveryModuleNameDoesNotAttributeAnotherModulesRequirements() {
        stub("FUNCTIONAL_ANALYSIS", FA_SNAPSHOT, """
                {"fr":[{"id":"FR-02","description":"User management screens list all accounts"}]}""");
        stubMissing("SRS");
        stubMissing("DESIGN_DOCUMENT");
        SpecificationLoader loader = loader();

        ModuleSpecification spec = loader.forModule(
                loader.load(PROJECT), module("ProductManagementController", "Product"));

        // Nothing genuinely matches, so it falls back to project-wide rather than wrongly claiming
        // FR-02 belongs to the Product module.
        assertThat(spec.requirementsAreProjectWide()).isTrue();
    }

    @Test
    void anEmptySpecificationNarrowsToAnEmptyModuleView() {
        assertThat(loader().forModule(ProjectSpecification.empty(),
                module("ProductManagementController", "Product")).isEmpty()).isTrue();
        assertThat(loader().forModule(null, module("C", "E")).isEmpty()).isTrue();
    }
}
