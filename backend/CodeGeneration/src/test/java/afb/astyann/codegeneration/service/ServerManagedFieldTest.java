package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfAttribute;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfProject;
import afb.astyann.codegeneration.domain.pcsf.PcsfStatusMachine;
import afb.astyann.codegeneration.domain.projection.BackendEntity;
import afb.astyann.codegeneration.domain.projection.BackendField;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Create DTO and {@code applyValues} used to be kept in step by repeating the same literal
 * field names — {@code currentStock}, {@code stockStatus}, {@code status} — in two templates.
 * Beyond being stock-management vocabulary inside a generator meant to build any project, two
 * copies of a condition is one copy too many: they could disagree, and a disagreement is a compile
 * error in the generated project rather than a test failure here.
 */
class ServerManagedFieldTest {

    private final ProjectionBuilder builder = new ProjectionBuilder();
    private final FreeMarkerEngine engine = new FreeMarkerEngine();

    private static <T> FieldValue<T> fv(T value) {
        FieldValue<T> f = new FieldValue<>();
        f.setValue(value);
        return f;
    }

    private static PcsfAttribute attr(String name, String javaType) {
        PcsfAttribute a = new PcsfAttribute();
        a.setId("attr_" + name);
        a.setName(fv(name));
        a.setJavaType(fv(javaType));
        return a;
    }

    /**
     * @param statusMachineOn entity id to attach a status machine to, or null for none
     */
    private static Pcsf pcsf(String entityId, String entityName, List<PcsfAttribute> attributes,
                             String statusMachineOn) {
        PcsfProject project = new PcsfProject();
        project.setName(fv("Any Project"));

        PcsfModule module = new PcsfModule();
        module.setId("MOD-01");
        module.setName(fv(entityName + " Management"));

        PcsfEntity entity = new PcsfEntity();
        entity.setId(entityId);
        entity.setName(fv(entityName));
        entity.setPrimaryModuleId("MOD-01");
        entity.setAttributes(new ArrayList<>(attributes));

        Pcsf pcsf = new Pcsf();
        pcsf.setProject(project);
        pcsf.setModules(new ArrayList<>(List.of(module)));
        pcsf.setEntities(new ArrayList<>(List.of(entity)));
        if (statusMachineOn != null) {
            pcsf.setStatusMachines(new ArrayList<>(List.of(PcsfStatusMachine.builder()
                    .entityId(statusMachineOn)
                    .states(new ArrayList<>(List.of("ACTIVE", "ARCHIVED")))
                    .transitions(new ArrayList<>())
                    .build())));
        }
        return pcsf;
    }

    private BackendEntity entityOf(Pcsf pcsf) {
        return builder.buildBackendProjection(pcsf).getEntities().get(0);
    }

    private List<String> writableFields(BackendEntity entity) {
        return entity.getFields().stream().filter(f -> !f.isServerManaged())
                .map(BackendField::getName).toList();
    }

    @Test
    void aStateFieldIsServerManagedWhenTheEntityHasAStatusMachine() {
        BackendEntity entity = entityOf(pcsf("entity_3", "Product",
                List.of(attr("name", "String"), attr("stockStatus", "String")), "entity_3"));

        assertThat(writableFields(entity)).containsExactly("name");
    }

    @Test
    void theStatusMachineResolvesByEntityIdNotOnlyByClassName() {
        // Real PCSFs name the entity by id; matching only the class name silently found nothing.
        BackendEntity entity = entityOf(pcsf("entity_3", "Product",
                List.of(attr("status", "String")), "entity_3"));

        assertThat(entity.getFields()).singleElement()
                .satisfies(f -> assertThat(f.isServerManaged()).isTrue());
    }

    @Test
    void aStatusLikeFieldStaysWritableWhenNoStateModelIsDeclared() {
        // maritalStatus on an entity with no status machine is an ordinary editable field — the
        // old literal check would have been just as wrong here, in the other direction.
        BackendEntity entity = entityOf(pcsf("entity_1", "Person",
                List.of(attr("fullName", "String"), attr("maritalStatus", "String")), null));

        assertThat(writableFields(entity)).containsExactly("fullName", "maritalStatus");
    }

    @Test
    void anAttributeExplicitlyHiddenFromFormsIsServerManaged() {
        PcsfAttribute computed = attr("runningTotal", "BigDecimal");
        computed.setShowInForm(fv(false));

        BackendEntity entity = entityOf(pcsf("entity_1", "Ledger",
                List.of(attr("reference", "String"), computed), null));

        assertThat(writableFields(entity)).containsExactly("reference");
    }

    @Test
    void noStockVocabularySurvivesInAnUnrelatedDomain() {
        // A clinic project must not have fields dropped because of names that meant something in
        // an inventory system.
        BackendEntity entity = entityOf(pcsf("entity_1", "Appointment",
                List.of(attr("patientName", "String"), attr("currentStock", "Integer"),
                        attr("stockStatus", "String")), null));

        assertThat(writableFields(entity))
                .containsExactly("patientName", "currentStock", "stockStatus");
    }

    @Test
    void everyGetterApplyValuesCallsExistsOnTheCreateDto() {
        // The invariant that matters: applyValues calls request.getX() for each field it copies, so
        // the DTO must declare every one of them. This checks the rendered output rather than the
        // predicate, so it holds however the filter is later expressed.
        PcsfAttribute hidden = attr("runningTotal", "BigDecimal");
        hidden.setShowInForm(fv(false));
        Pcsf pcsf = pcsf("entity_3", "Product",
                List.of(attr("name", "String"), attr("price", "BigDecimal"),
                        attr("stockStatus", "String"), hidden), "entity_3");

        BackendProjection projection = builder.buildBackendProjection(pcsf);
        Map<String, Object> model = new HashMap<>();
        model.put("project", projection.getProjectInfo());
        model.put("entities", projection.getEntities());
        model.put("modules", projection.getModules());
        model.put("roles", projection.getRoles());
        model.put("entity", projection.getEntities().get(0));
        model.put("module", projection.getModules().get(0));

        String createDto = engine.render("backend/CreateDto.java.ftl", model);
        String serviceImpl = engine.render("backend/ServiceImpl.java.ftl", model);

        List<String> copied = new ArrayList<>();
        Matcher matcher = Pattern.compile("request\\.get(\\w+)\\(\\)").matcher(serviceImpl);
        while (matcher.find()) copied.add(matcher.group(1));

        assertThat(copied).isNotEmpty();
        for (String getter : copied) {
            String field = Character.toLowerCase(getter.charAt(0)) + getter.substring(1);
            assertThat(createDto)
                    .as("CreateProductDto must declare '%s' because applyValues copies it", field)
                    .contains(" " + field + ";");
        }
        // ...and the server-managed ones appear in neither.
        assertThat(createDto).doesNotContain("stockStatus").doesNotContain("runningTotal");
        assertThat(serviceImpl).doesNotContain("request.getStockStatus()")
                .doesNotContain("request.getRunningTotal()");
    }

    @Test
    void serverManagedFieldsAreStillReturnedInResponses() {
        // Excluded from input, not from output — a status the client cannot set is still a status
        // the client needs to read.
        Pcsf pcsf = pcsf("entity_3", "Product",
                List.of(attr("name", "String"), attr("stockStatus", "String")), "entity_3");
        BackendProjection projection = builder.buildBackendProjection(pcsf);
        Map<String, Object> model = new HashMap<>();
        model.put("project", projection.getProjectInfo());
        model.put("entity", projection.getEntities().get(0));

        assertThat(engine.render("backend/ResponseDto.java.ftl", model)).contains("stockStatus");
    }
}
