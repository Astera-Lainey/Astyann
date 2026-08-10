package afb.astyann.documentservice.service;

import afb.astyann.documentservice.dto.pcsf.PcsfView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Derives the operations the code generator will emit, so the API contract document describes the
 * API that will actually exist.
 *
 * <p>The contract used to be written entirely by a model reading project prose out of the RAG
 * index, while the generator built controllers from its own rules. Two independent renderings of
 * the same source agree only by coincidence, and they did not: the document described a
 * {@code DELETE} the generator never emitted and missed the {@code PATCH .../archive} it did.
 *
 * <p><strong>This must stay in step with {@code ProjectionBuilder.buildModules} in the
 * CodeGeneration service</strong> — the two are the single derivation, rendered twice. Both take
 * the module's declared endpoints when it has any and fall back to the same CRUD convention when
 * it does not. {@code ApiContractDeriverTest} pins the exact shape on this side;
 * {@code DeclaredEndpointProjectionTest} pins it on the other.
 */
@Component
@Slf4j
public class ApiContractDeriver {

    /** One operation, as the document describes it: absolute path, not the controller-relative one. */
    public record DerivedEndpoint(
            String apiCode,
            String httpMethod,
            String path,
            String operationId,
            String summary,
            String group,
            List<String> roles,
            boolean requiresAuth,
            boolean paginated,
            boolean hasRequestBody) {

        /** Identity used to carry authored prose across from the model's version of this row. */
        public String key() {
            return httpMethod + " " + path;
        }
    }

    private static final List<String> DEFAULT_CRUD = List.of("CREATE", "READ", "UPDATE", "DELETE");
    private static final java.util.Set<String> SUPPORTED_HTTP_METHODS =
            java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final java.util.Set<String> CRUD_VERBS = java.util.Set.of(
            "create", "add", "new", "read", "list", "view", "get", "find", "show",
            "update", "edit", "modify", "delete", "remove");

    public List<DerivedEndpoint> derive(PcsfView pcsf) {
        List<DerivedEndpoint> out = new ArrayList<>();
        if (pcsf == null || pcsf.getModules() == null) return out;

        String versionPrefix = pcsf.getApiConfig() != null && pcsf.getApiConfig().getVersionPrefix() != null
                ? pcsf.getApiConfig().getVersionPrefix() : "/api/v1";

        Map<String, List<PcsfView.Endpoint>> declaredByModule = new LinkedHashMap<>();
        for (PcsfView.Endpoint ep : nullSafe(pcsf.getEndpoints())) {
            if (ep == null || ep.getModuleId() == null) continue;
            if (isBlank(ep.getPath()) || isBlank(ep.getHttpMethod())) continue;
            if (!SUPPORTED_HTTP_METHODS.contains(ep.getHttpMethod().trim().toUpperCase(Locale.ROOT))) continue;
            declaredByModule.computeIfAbsent(ep.getModuleId(), k -> new ArrayList<>()).add(ep);
        }

        int sequence = 1;
        for (PcsfView.Module module : nullSafe(pcsf.getModules())) {
            if (module == null) continue;
            String moduleName = value(module.getName());
            if (isBlank(moduleName)) continue;

            List<PcsfView.Endpoint> declared = declaredByModule.get(module.getId());
            List<DerivedEndpoint> rows = (declared != null && !declared.isEmpty())
                    ? fromDeclared(declared, moduleName, sequence)
                    : fromCrudConvention(pcsf, module, moduleName, versionPrefix, sequence);
            sequence += rows.size();
            out.addAll(rows);
        }
        return out;
    }

    private List<DerivedEndpoint> fromDeclared(List<PcsfView.Endpoint> declared, String moduleName, int startAt) {
        List<DerivedEndpoint> rows = new ArrayList<>();
        int n = startAt;
        for (PcsfView.Endpoint ep : declared) {
            String verb = ep.getHttpMethod().trim().toUpperCase(Locale.ROOT);
            boolean hasBody = ep.getRequestBodyEntityId() != null
                    && (verb.equals("POST") || verb.equals("PUT") || verb.equals("PATCH"));
            rows.add(new DerivedEndpoint(
                    apiCode(n++), verb, ep.getPath().trim(),
                    ep.getOperationId(), ep.getSummary(), moduleName,
                    new ArrayList<>(nullSafe(ep.getRequiredRoles())),
                    ep.isRequiresAuth(), ep.isPaginated(), hasBody));
        }
        return rows;
    }

    /**
     * The surface the generator invents when a module declares no endpoints of its own — kept
     * identical to {@code ProjectionBuilder}'s fallback so the document still matches the code in
     * that case rather than describing an API nobody will build.
     */
    private List<DerivedEndpoint> fromCrudConvention(PcsfView pcsf, PcsfView.Module module, String moduleName,
                                                     String versionPrefix, int startAt) {
        String base = versionPrefix + "/" + toKebabCase(moduleName);
        String entity = primaryEntityName(pcsf, module, moduleName);

        List<String> ops = new ArrayList<>();
        if (module.getCrudOperations() != null && module.getCrudOperations().getValue() != null
                && !module.getCrudOperations().getValue().isEmpty()) {
            module.getCrudOperations().getValue().forEach(o -> ops.add(normaliseEnum(o)));
        } else {
            ops.addAll(DEFAULT_CRUD);
        }

        List<DerivedEndpoint> rows = new ArrayList<>();
        int n = startAt;
        if (ops.contains("CREATE")) {
            rows.add(new DerivedEndpoint(apiCode(n++), "POST", base, "create" + entity,
                    "Create a new " + entity, moduleName, List.of(), true, false, true));
        }
        if (ops.contains("READ")) {
            rows.add(new DerivedEndpoint(apiCode(n++), "GET", base, "getAll" + entity,
                    "Retrieve a paginated list of " + entity, moduleName, List.of(), true, true, false));
            rows.add(new DerivedEndpoint(apiCode(n++), "GET", base + "/{id}", "get" + entity + "ById",
                    "Retrieve a single " + entity + " by id", moduleName, List.of(), true, false, false));
        }
        if (ops.contains("UPDATE")) {
            rows.add(new DerivedEndpoint(apiCode(n++), "PUT", base + "/{id}", "update" + entity,
                    "Update an existing " + entity, moduleName, List.of(), true, false, true));
        }
        if (ops.contains("DELETE")) {
            rows.add(new DerivedEndpoint(apiCode(n++), "DELETE", base + "/{id}", "delete" + entity,
                    "Delete a " + entity, moduleName, List.of(), true, false, false));
        }
        for (PcsfView.UseCase uc : nullSafe(module.getUseCases())) {
            String action = customAction(value(uc.getName()));
            if (action == null) continue;
            rows.add(new DerivedEndpoint(apiCode(n++), "POST", base + "/{id}/" + action, action,
                    value(uc.getName()), moduleName, List.of(), true, false, false));
        }
        return rows;
    }

    private String primaryEntityName(PcsfView pcsf, PcsfView.Module module, String moduleName) {
        for (PcsfView.Entity e : nullSafe(pcsf.getEntities())) {
            if (e != null && module.getId() != null && module.getId().equals(e.getPrimaryModuleId())
                    && !isBlank(value(e.getName()))) {
                return value(e.getName());
            }
        }
        String pascal = toPascalCase(moduleName);
        for (PcsfView.Entity e : nullSafe(pcsf.getEntities())) {
            String name = e == null ? null : value(e.getName());
            if (!isBlank(name) && pascal.startsWith(name)) return name;
        }
        return isBlank(pascal) ? "Entity" : pascal;
    }

    /** Mirrors the generator: a use case whose name starts with a CRUD verb is already covered. */
    private String customAction(String useCaseName) {
        if (isBlank(useCaseName)) return null;
        String[] words = useCaseName.trim().split("[\\s_\\-]+");
        if (words.length == 0) return null;
        if (CRUD_VERBS.contains(words[0].toLowerCase(Locale.ROOT))) return null;
        String action = toLowerCamelCase(useCaseName.trim());
        return action.isEmpty() || !Character.isLetter(action.charAt(0)) ? null : action;
    }

    private String apiCode(int n) {
        return String.format("API-%02d", n);
    }

    // ── small string helpers, matching the generator's conventions ──────────

    private String toKebabCase(String input) {
        if (isBlank(input)) return "resource";
        return input.trim().replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[\\s_]+", "-").toLowerCase(Locale.ROOT).replaceAll("-+", "-");
    }

    private String toPascalCase(String input) {
        if (isBlank(input)) return "";
        StringBuilder sb = new StringBuilder();
        for (String word : input.trim().split("[\\s_\\-.]+")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1));
        }
        return sb.toString();
    }

    private String toLowerCamelCase(String input) {
        String pascal = toPascalCase(input);
        return pascal.isEmpty() ? "" : Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String normaliseEnum(String value) {
        return isBlank(value) ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    private String value(PcsfView.FieldValue<String> fv) {
        return fv == null || fv.getValue() == null ? "" : fv.getValue();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
