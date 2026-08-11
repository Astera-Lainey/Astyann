package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.client.DocumentServiceClient;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.dto.DocumentContentDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the approved documents' structured content and turns it into per-module specification
 * input for the logic-injection prompt.
 *
 * <p>Until now the only route from an approved document to generated code was a RAG lookup: five
 * chunks, capped at 8000 characters, competing project-wide, over prose that had been flattened out
 * of a {@code .docx}. The documents were generated from structured JSON in the first place, so this
 * reads that JSON instead — the functional requirements, module responsibilities and entity
 * behaviour arrive as data rather than as retrieved text.
 *
 * <p>Only three document types are read. {@code SRS}, {@code FUNCTIONAL_ANALYSIS} and
 * {@code DESIGN_DOCUMENT} carry the behavioural specification; {@code API_CONTRACT} and
 * {@code DATA_DICTIONARY} are themselves derived from the PCSF, so feeding them back would be a
 * lossy round-trip to where the generator already was.
 *
 * <p>Entirely best-effort. A document that is missing, unapproved, or predates content persistence
 * is skipped with a log line, and generation proceeds with whatever else was found.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpecificationLoader {

    private final DocumentServiceClient documentServiceClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final String SRS = "SRS";
    private static final String FUNCTIONAL_ANALYSIS = "FUNCTIONAL_ANALYSIS";
    private static final String DESIGN_DOCUMENT = "DESIGN_DOCUMENT";

    /** An identified requirement — the id matters, because it makes traceability possible. */
    public record Requirement(String id, String description) {}

    /** What a document says an entity does, as opposed to what fields it holds. */
    public record EntityBehaviour(String entityName, String methods) {}

    /** Everything the documents contribute to one module's prompt. */
    public record ModuleSpecification(List<Requirement> functionalRequirements,
                                      boolean requirementsAreProjectWide,
                                      List<String> responsibilities,
                                      List<EntityBehaviour> entityBehaviour,
                                      List<Requirement> nonFunctionalRequirements) {

        public static ModuleSpecification empty() {
            return new ModuleSpecification(List.of(), false, List.of(), List.of(), List.of());
        }

        public boolean isEmpty() {
            return functionalRequirements.isEmpty() && responsibilities.isEmpty()
                    && entityBehaviour.isEmpty() && nonFunctionalRequirements.isEmpty();
        }
    }

    /**
     * The whole project's document-derived specification, loaded once per generation.
     *
     * @param snapshotIds the approved snapshots these documents came from — used to scope the
     *                    supplementary RAG lookup to the same versions
     */
    public record ProjectSpecification(List<Requirement> functionalRequirements,
                                       List<Requirement> nonFunctionalRequirements,
                                       List<ModuleResponsibility> moduleResponsibilities,
                                       List<EntityBehaviour> entityBehaviour,
                                       Set<UUID> snapshotIds) {

        public static ProjectSpecification empty() {
            return new ProjectSpecification(List.of(), List.of(), List.of(), List.of(), Set.of());
        }

        public boolean isEmpty() {
            return functionalRequirements.isEmpty() && nonFunctionalRequirements.isEmpty()
                    && moduleResponsibilities.isEmpty() && entityBehaviour.isEmpty();
        }
    }

    public record ModuleResponsibility(String moduleName, String description, String responsibilities) {}

    // ── Loading ─────────────────────────────────────────────────────────────

    public ProjectSpecification load(UUID projectId) {
        Fetched srs = fetch(projectId, SRS);
        Fetched functional = fetch(projectId, FUNCTIONAL_ANALYSIS);
        Fetched design = fetch(projectId, DESIGN_DOCUMENT);

        Set<UUID> snapshots = new LinkedHashSet<>();
        for (Fetched f : List.of(srs, functional, design)) {
            if (f.snapshotId() != null) snapshots.add(f.snapshotId());
        }

        List<Requirement> frs = new ArrayList<>();
        frs.addAll(requirements(functional.content(), "fr"));
        frs.addAll(requirements(srs.content(), "fr"));
        // The SRS and the functional analysis routinely restate the same requirement; keep the
        // first mention of each id rather than showing the model the same thing twice.
        List<Requirement> dedupedFrs = dedupeById(frs);

        List<Requirement> nfrs = new ArrayList<>();
        nfrs.addAll(requirements(functional.content(), "nfr"));
        nfrs.addAll(requirements(srs.content(), "nfr"));

        ProjectSpecification spec = new ProjectSpecification(
                dedupedFrs, dedupeById(nfrs), moduleResponsibilities(design.content()),
                entityBehaviour(functional.content()), snapshots);

        if (spec.isEmpty()) {
            log.info("No document-derived specification available for project {} — the prompt will "
                     + "rely on the PCSF alone.", projectId);
        } else {
            log.info("Loaded specification for project {}: {} FR(s), {} NFR(s), {} module "
                     + "responsibility block(s), {} entity behaviour entry/entries, from {} snapshot(s).",
                    projectId, spec.functionalRequirements().size(), spec.nonFunctionalRequirements().size(),
                    spec.moduleResponsibilities().size(), spec.entityBehaviour().size(),
                    spec.snapshotIds().size());
        }
        return spec;
    }

    /**
     * One document's content and the snapshot it came from. Returned rather than accumulated in a
     * field: this component is a singleton and two projects can generate at once, so per-call state
     * must stay on the stack.
     */
    private record Fetched(JsonNode content, UUID snapshotId) {
        static Fetched none() {
            return new Fetched(null, null);
        }
    }

    private Fetched fetch(UUID projectId, String type) {
        try {
            var response = documentServiceClient.approvedContent(projectId, type);
            DocumentContentDTO dto = response != null ? response.getData() : null;
            if (dto == null || dto.getContent() == null || dto.getContent().isEmpty()) {
                log.debug("No approved {} content for project {}", type, projectId);
                return Fetched.none();
            }
            // Arrives as a Map because that is the one shape Jackson 2 and Jackson 3 agree on over
            // the wire; converted here so the extraction below can stay tree-shaped.
            return new Fetched(objectMapper.valueToTree(dto.getContent()), dto.getSnapshotId());
        } catch (Exception ex) {
            // 404 (not approved / absent) and 409 (generated before content was stored) are both
            // ordinary here — the document simply does not contribute.
            log.debug("Could not load {} for project {}: {}", type, projectId, ex.getMessage());
            return Fetched.none();
        }
    }

    // ── Per-module view ─────────────────────────────────────────────────────

    /**
     * Narrows the project specification to one module.
     *
     * <p>Functional requirements carry no module reference — the schema is {@code fr(id,
     * description)} and nothing more — so attribution is by mention: a requirement whose text names
     * the module or its primary entity belongs to it. When nothing matches, every requirement is
     * passed through and flagged as project-wide rather than dropped, because an unattributed
     * requirement in front of the model is worth more than a silently missing one. The flag is what
     * lets the prompt say which of the two it is instead of implying a precision it does not have.
     */
    public ModuleSpecification forModule(ProjectSpecification spec, BackendModule module) {
        if (spec == null || spec.isEmpty()) return ModuleSpecification.empty();

        Set<String> keywords = keywordsFor(module);

        List<Requirement> matched = spec.functionalRequirements().stream()
                .filter(r -> mentionsAny(r.description(), keywords))
                .toList();
        boolean projectWide = matched.isEmpty() && !spec.functionalRequirements().isEmpty();
        List<Requirement> frs = projectWide ? spec.functionalRequirements() : matched;

        // Matched on the whole name, not on keywords: a design document's module list names the
        // same modules the PCSF does, and keyword matching would let "Product Management" claim
        // "User Management" on the strength of the word they share.
        String moduleKey = moduleKey(module);
        List<String> responsibilities = spec.moduleResponsibilities().stream()
                .filter(mr -> normalise(mr.moduleName()).equals(moduleKey))
                .map(mr -> join(mr.description(), mr.responsibilities()))
                .filter(s -> !s.isBlank())
                .toList();

        List<EntityBehaviour> behaviour = spec.entityBehaviour().stream()
                .filter(eb -> normalise(eb.entityName()).equals(normalise(module.getEntityClassName())))
                .filter(eb -> eb.methods() != null && !eb.methods().isBlank())
                .toList();

        return new ModuleSpecification(frs, projectWide, responsibilities, behaviour,
                spec.nonFunctionalRequirements());
    }

    /**
     * Structural words that appear in almost every module name and identify nothing. Without
     * excluding them, "Product Management" matches any requirement mentioning management — which in
     * a project of Product/User/Stock "Management" modules is all of them.
     */
    private static final Set<String> GENERIC_NAME_WORDS = Set.of(
            "management", "managment", "module", "service", "system", "component",
            "manager", "handler", "controller", "process", "processing");

    /** The module's own name, normalised — used where an exact correspondence is expected. */
    private String moduleKey(BackendModule module) {
        String controller = module.getControllerName();
        return controller == null ? "" : normalise(controller.replaceAll("Controller$", ""));
    }

    private Set<String> keywordsFor(BackendModule module) {
        Set<String> keywords = new LinkedHashSet<>();
        String entity = module.getEntityClassName();
        if (entity != null && !entity.isBlank()) keywords.add(normalise(entity));
        // The controller name carries the module's own name; the request mapping does not reliably,
        // since a module whose endpoints share no resource root maps to just the version prefix.
        String controller = module.getControllerName();
        if (controller != null) {
            String name = controller.replaceAll("Controller$", "");
            for (String word : name.split("(?<!^)(?=[A-Z])")) {
                String key = normalise(word);
                if (key.length() > 3 && !GENERIC_NAME_WORDS.contains(key)) keywords.add(key);
            }
        }
        return keywords;
    }

    private boolean mentionsAny(String text, Set<String> keywords) {
        if (text == null || text.isBlank() || keywords.isEmpty()) return false;
        String haystack = normalise(text);
        return keywords.stream().anyMatch(k -> !k.isBlank() && haystack.contains(k));
    }

    // ── JSON extraction ─────────────────────────────────────────────────────

    private List<Requirement> requirements(JsonNode doc, String field) {
        List<Requirement> out = new ArrayList<>();
        if (doc == null) return out;
        JsonNode array = doc.get(field);
        if (array == null || !array.isArray()) return out;
        for (JsonNode node : array) {
            String id = text(node, "id");
            String description = text(node, "description");
            if (description.isBlank()) continue;
            out.add(new Requirement(id.isBlank() ? "(unnumbered)" : id, description));
        }
        return out;
    }

    private List<ModuleResponsibility> moduleResponsibilities(JsonNode design) {
        List<ModuleResponsibility> out = new ArrayList<>();
        if (design == null) return out;
        JsonNode array = design.get("module");
        if (array == null || !array.isArray()) return out;
        for (JsonNode node : array) {
            String name = text(node, "name");
            if (name.isBlank()) continue;
            out.add(new ModuleResponsibility(name, text(node, "description"),
                    text(node, "responsibilities")));
        }
        return out;
    }

    private List<EntityBehaviour> entityBehaviour(JsonNode functional) {
        List<EntityBehaviour> out = new ArrayList<>();
        if (functional == null) return out;
        JsonNode array = functional.get("entity");
        if (array == null || !array.isArray()) return out;
        for (JsonNode node : array) {
            String name = text(node, "name");
            if (name.isBlank()) continue;
            out.add(new EntityBehaviour(name, text(node, "methods")));
        }
        return out;
    }

    private List<Requirement> dedupeById(List<Requirement> requirements) {
        List<Requirement> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Requirement r : requirements) {
            String key = "(unnumbered)".equals(r.id()) ? normalise(r.description()) : normalise(r.id());
            if (seen.add(key)) out.add(r);
        }
        return out;
    }

    private String join(String a, String b) {
        if (a == null || a.isBlank()) return b == null ? "" : b;
        if (b == null || b.isBlank()) return a;
        return a + " — " + b;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String normalise(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
