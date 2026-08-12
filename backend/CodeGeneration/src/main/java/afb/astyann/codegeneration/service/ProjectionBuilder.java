package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.*;
import afb.astyann.codegeneration.domain.projection.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the envelope-heavy {@link Pcsf} model into the flat projection DTOs consumed by the
 * template engines. Only the backend projection is implemented in this slice; the frontend and
 * infrastructure builders are stubbed until their template passes land.
 */
@Service
@Slf4j
public class ProjectionBuilder {

    /** Attributes managed by base classes / audit columns — never emitted as user fields. */
    private static final Set<String> RESERVED_ATTRIBUTE_NAMES = Set.of(
            "id", "createdat", "lastmodifiedat", "created_at", "last_modified_at");

    private static final List<String> DEFAULT_CRUD = List.of("CREATE", "READ", "UPDATE", "DELETE");
    private static final Set<String> CRUD_VERBS = Set.of(
            "create", "add", "new", "read", "list", "view", "get", "find", "show",
            "update", "edit", "modify", "delete", "remove");

    // ── Public API ──────────────────────────────────────────────────────────

    public BackendProjection buildBackendProjection(Pcsf pcsf) {
        BackendProjectInfo info = buildProjectInfo(pcsf);
        List<BackendRole> roles = buildRoles(pcsf);

        Map<String, BackendEntity> entitiesById = new LinkedHashMap<>();
        Map<String, String> entityIdToClassName = new LinkedHashMap<>();
        for (PcsfEntity e : nullSafe(pcsf.getEntities())) {
            String className = fv(e.getName());
            if (isBlank(className)) continue;
            BackendEntity be = buildEntity(e, className, hasStatusMachine(pcsf, e, className));
            String key = e.getId() != null ? e.getId() : className;
            entitiesById.put(key, be);
            entityIdToClassName.put(key, className);
        }

        // Second pass: a foreign key's type depends on the entity it points at, which may not have
        // been built yet during the first.
        alignForeignKeyTypes(entitiesById);

        applyRelationships(pcsf, entitiesById, entityIdToClassName);

        List<BackendEntity> entities = new ArrayList<>(entitiesById.values());
        List<BackendModule> modules = buildModules(pcsf, info, roles, entitiesById);

        return BackendProjection.builder()
                .projectInfo(info)
                .entities(entities)
                .modules(modules)
                .roles(roles)
                .build();
    }

    public FrontendProjection buildFrontendProjection(Pcsf pcsf) {
        FrontendProjectInfo info = buildFrontendProjectInfo(pcsf);
        List<FrontendEntity> entities = buildFrontendEntities(pcsf);
        Map<String, FrontendEntity> entityByClass = new LinkedHashMap<>();
        for (FrontendEntity e : entities) entityByClass.put(e.getClassName(), e);

        List<BackendRole> roles = buildRoles(pcsf);
        BackendProjection backend = buildBackendProjection(pcsf); // reuse module + endpoint derivation
        List<FrontendModule> modules = new ArrayList<>();
        for (BackendModule bm : backend.getModules()) {
            FrontendEntity entity = entityByClass.get(bm.getEntityClassName());
            if (entity == null) continue;
            modules.add(buildFrontendModule(bm, entity, info, pcsf));
        }

        List<FrontendNavItem> navigation = buildNavigation(pcsf, modules, roles);
        if (!modules.isEmpty()) info.setDefaultRoute("/" + modules.get(0).getComponentPrefix());

        return FrontendProjection.builder()
                .projectInfo(info).entities(entities).modules(modules).navigation(navigation)
                .build();
    }

    private FrontendProjectInfo buildFrontendProjectInfo(Pcsf pcsf) {
        PcsfProject project = pcsf.getProject();
        PcsfDerivedNames derived = project != null ? project.getDerived() : null;
        PcsfApiConfig api = pcsf.getApiConfig() != null ? pcsf.getApiConfig() : new PcsfApiConfig();
        PcsfInfrastructureConfig infra = pcsf.getInfrastructureConfig();
        PcsfColours colours = pcsf.getUserInterface() != null && pcsf.getUserInterface().getColours() != null
                ? pcsf.getUserInterface().getColours() : new PcsfColours();

        String appName = project != null ? firstNonBlank(fv(project.getDisplayName()), fv(project.getName())) : null;
        if (isBlank(appName)) appName = "Application";
        int backendPort = infra != null ? infra.getBackendPort() : 8080;

        return FrontendProjectInfo.builder()
                .appName(appName)
                .angularProjectName(derived != null && !isBlank(derived.getAngularProjectName())
                        ? derived.getAngularProjectName() : toKebabCase(appName) + "-web")
                .apiBaseUrl("http://localhost:" + backendPort + (!isBlank(api.getVersionPrefix()) ? api.getVersionPrefix() : "/api/v1"))
                .primaryColour(colours.getPrimary())
                .secondaryColour(colours.getSecondary())
                .neutralColour(colours.getNeutral())
                .textColour(colours.getText())
                .primaryDark(darkenHex(colours.getPrimary(), 15))
                .primaryLight(lightenHex(colours.getPrimary(), 20))
                .primaryAlpha(hexToRgba(colours.getPrimary(), 0.15))
                .fontFamily("Inter")
                .build();
    }

    private List<FrontendEntity> buildFrontendEntities(Pcsf pcsf) {
        List<FrontendEntity> out = new ArrayList<>();
        for (PcsfEntity e : nullSafe(pcsf.getEntities())) {
            String className = fv(e.getName());
            if (isBlank(className)) continue;
            List<FrontendField> fields = new ArrayList<>();
            for (PcsfAttribute a : nullSafe(e.getAttributes())) {
                String name = fv(a.getName());
                if (isBlank(name) || RESERVED_ATTRIBUTE_NAMES.contains(name.toLowerCase(Locale.ROOT))) continue;
                PcsfConstraints c = a.getConstraints();
                fields.add(FrontendField.builder()
                        .name(name)
                        .tsType(mapTsType(fv(a.getJavaType())))
                        .label(toTitleCase(name))
                        .required(c != null && fvBool(c.getRequired(), false))
                        .unique(c != null && fvBool(c.getUnique(), false))
                        .minLength(c != null ? fvInt(c.getMinLength()) : null)
                        .maxLength(c != null ? fvInt(c.getMaxLength()) : null)
                        .pattern(c != null ? fv(c.getPattern()) : null)
                        .build());
            }
            out.add(FrontendEntity.builder()
                    .className(className)
                    .fileName(toKebabCase(className))
                    .instanceName(toLowerCamelCase(className))
                    .fields(fields)
                    .build());
        }
        return out;
    }

    private FrontendModule buildFrontendModule(BackendModule bm, FrontendEntity entity,
                                               FrontendProjectInfo info, Pcsf pcsf) {
        // From the module's own name, not from the last segment of its request mapping. A module
        // whose declared endpoints share no resource root (say /api/v1/dashboard alongside
        // /api/v1/reports/stock) has a request mapping of just the version prefix, and reading the
        // last segment of that produced components called "v1" — v1-list.component.ts under
        // features/v1. The controller name is derived from the module name and always present.
        String moduleKebab = toKebabCase(bm.getControllerName().replaceAll("Controller$", ""));
        boolean hasCreate = bm.getEndpoints().stream().anyMatch(e -> "POST".equals(e.getHttpMethod()) && !e.isHasPathVariable());
        boolean hasRead   = bm.getEndpoints().stream().anyMatch(e -> "GET".equals(e.getHttpMethod()));
        boolean hasUpdate = bm.getEndpoints().stream().anyMatch(e -> "PUT".equals(e.getHttpMethod()));
        boolean hasDelete = bm.getEndpoints().stream().anyMatch(e -> "DELETE".equals(e.getHttpMethod()));

        List<FrontendEndpoint> endpoints = new ArrayList<>();
        for (BackendEndpoint be : bm.getEndpoints()) {
            endpoints.add(FrontendEndpoint.builder()
                    .methodName(be.getMethodName())
                    .httpMethod(be.getHttpMethod())
                    .path(be.getPath())
                    .hasPathId(be.isHasPathVariable())
                    .hasBody(be.isHasRequestBody())
                    .returnType(mapEndpointReturnType(be.getReturnType(), entity.getClassName()))
                    .crud(be.isCrud())
                    .actionSegment(actionSegment(be.getPath()))
                    // Capitalise in place rather than re-splitting: the method name is already a
                    // valid camelCase identifier, so this cannot corrupt it.
                    .methodNamePascal(capitaliseFirst(be.getMethodName()))
                    .label(toSentenceCase(be.getMethodName()))
                    .httpMethodLower(be.getHttpMethod() == null ? "post"
                            : be.getHttpMethod().toLowerCase(Locale.ROOT))
                    .sendsBody("POST".equals(be.getHttpMethod())
                            || "PUT".equals(be.getHttpMethod())
                            || "PATCH".equals(be.getHttpMethod()))
                    .build());
        }

        // Use-case actions the backend exposes beyond CRUD. Without these the generated UI has no
        // way to invoke endpoints the generated API provides.
        List<FrontendEndpoint> customActions = endpoints.stream()
                .filter(e -> !e.isCrud())
                .filter(e -> !isBlank(e.getActionSegment()))
                .toList();

        // Status values are only known when the PCSF declares a state machine for this entity —
        // without them there is no colour mapping to build, so the column stays plain text.
        List<String> statusStates = statusStatesFor(pcsf, entity.getClassName());
        String variantsExpression = statusStates.isEmpty() ? null : badgeVariantsExpression(statusStates);

        List<FrontendColumn> listColumns = new ArrayList<>();
        int count = 0;
        for (FrontendField f : entity.getFields()) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if (lower.contains("password")) continue;
            boolean isStatus = variantsExpression != null && (lower.equals("status") || lower.equals("state"));
            listColumns.add(FrontendColumn.builder()
                    .label(f.getLabel()).fieldName(f.getName())
                    .badge(isStatus)
                    .variantsExpression(isStatus ? variantsExpression : null)
                    .build());
            if (++count >= 6) break;
        }

        List<FrontendFormField> formFields = new ArrayList<>();
        for (FrontendField f : entity.getFields()) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if (Set.of("id", "createdat", "lastmodifiedat", "currentstock", "stockstatus").contains(lower)) continue;
            String inputType = deriveInputType(f);
            String validators = validatorsExpression(f, inputType);
            formFields.add(FrontendFormField.builder()
                    .label(f.getLabel()).fieldName(f.getName())
                    .inputType(inputType)
                    .required(f.isRequired())
                    .validatorsExpression(validators)
                    .hasValidators(!isBlank(validators))
                    .build());
        }

        return FrontendModule.builder()
                .serviceName(toPascalCase(moduleKebab) + "Service")
                .serviceFileName(moduleKebab)
                .componentPrefix(moduleKebab)
                .entityClassName(entity.getClassName())
                .entityFileName(entity.getFileName())
                .entityInstanceName(entity.getInstanceName())
                // Relative to apiBaseUrl, which already ends in the version prefix. Passing the
                // full request mapping here produced ".../api/v1/api/v1/product" and 404'd every
                // call the generated frontend made.
                .apiPath(stripVersionPrefix(bm.getRequestMapping(), versionPrefix(pcsf)))
                .hasCreate(hasCreate).hasRead(hasRead).hasUpdate(hasUpdate).hasDelete(hasDelete)
                .endpoints(endpoints).customActions(customActions)
                .hasCustomActions(!customActions.isEmpty())
                .listColumns(listColumns).formFields(formFields)
                .build();
    }

    /**
     * Extracts the action segment from a custom endpoint path — {@code "/{id}/archive"} yields
     * {@code "archive"}. Returns {@code null} for paths with no trailing segment (plain CRUD).
     */
    private String actionSegment(String path) {
        if (isBlank(path)) return null;
        // Strips any path variable, not just the literal "/{id}" the CRUD convention emits — a
        // declared path names its own variable ("/{productId}/archive"), and leaving that in would
        // make the frontend treat the whole thing as the action segment.
        String trimmed = path.replaceAll("/\\{[^}/]*}", "").replaceAll("^/+", "").replaceAll("/+$", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Uppercases the first character, leaving the rest of the identifier untouched. */
    private String capitaliseFirst(String input) {
        if (isBlank(input)) return "";
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    /** {@code recordAStockEntry} → {@code "Record a stock entry"} — button-friendly text. */
    private String toSentenceCase(String input) {
        List<String> words = splitWords(input);
        if (words.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i).toLowerCase(Locale.ROOT);
            if (i == 0) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            } else {
                sb.append(' ').append(word);
            }
        }
        return sb.toString();
    }

    /**
     * Builds the Angular validator list for a field, mirroring the constraints the backend enforces
     * with {@code @NotNull} / {@code @Size} / {@code @Pattern}. Length rules only apply to text
     * inputs — {@code Validators.minLength} on a number control checks the *string* length, which
     * would reject valid values.
     *
     * @return e.g. {@code "Validators.required, Validators.maxLength(120)"}, or {@code null}
     */
    private String validatorsExpression(FrontendField field, String inputType) {
        List<String> validators = new ArrayList<>();
        if (field.isRequired()) validators.add("Validators.required");
        if ("email".equals(inputType)) validators.add("Validators.email");

        boolean textual = Set.of("text", "password", "email").contains(inputType);
        if (textual) {
            if (field.getMinLength() != null && field.getMinLength() > 0) {
                validators.add("Validators.minLength(" + field.getMinLength() + ")");
            }
            if (field.getMaxLength() != null && field.getMaxLength() > 0) {
                validators.add("Validators.maxLength(" + field.getMaxLength() + ")");
            }
            if (!isBlank(field.getPattern())) {
                validators.add("Validators.pattern('" + escapeTsSingleQuoted(field.getPattern()) + "')");
            }
        }
        return validators.isEmpty() ? null : String.join(", ", validators);
    }

    /** The configured API version prefix, defaulting to {@code /api/v1}. */
    private String versionPrefix(Pcsf pcsf) {
        PcsfApiConfig api = pcsf.getApiConfig();
        return api != null && !isBlank(api.getVersionPrefix()) ? api.getVersionPrefix() : "/api/v1";
    }

    /**
     * Removes the leading version prefix from a request mapping, because the frontend's
     * {@code apiBaseUrl} already ends with it: {@code /api/v1/product} → {@code /product}.
     */
    private String stripVersionPrefix(String requestMapping, String versionPrefix) {
        if (isBlank(requestMapping)) return "";
        if (!isBlank(versionPrefix) && requestMapping.startsWith(versionPrefix)) {
            String stripped = requestMapping.substring(versionPrefix.length());
            return stripped.startsWith("/") ? stripped : "/" + stripped;
        }
        return requestMapping;
    }

    /** Escapes a value for embedding in a single-quoted TypeScript string literal. */
    private String escapeTsSingleQuoted(String raw) {
        return raw.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** States declared by the PCSF status machine for {@code entityClassName}, or empty. */
    private List<String> statusStatesFor(Pcsf pcsf, String entityClassName) {
        String entityId = null;
        for (PcsfEntity e : nullSafe(pcsf.getEntities())) {
            if (e != null && !isBlank(entityClassName) && entityClassName.equalsIgnoreCase(fv(e.getName()))) {
                entityId = e.getId();
                break;
            }
        }
        for (PcsfStatusMachine machine : nullSafe(pcsf.getStatusMachines())) {
            String target = machine.getEntityId();
            if (isBlank(target) || isBlank(entityClassName)) continue;
            // A status machine names its entity by id ("entity_3"), not by class name — matching
            // only on the name meant no entity ever resolved and the status column silently lost
            // its badge colours. Both are accepted so either style of PCSF resolves.
            if (!target.equalsIgnoreCase(entityClassName) && !target.equals(entityId)) continue;
            List<String> states = new ArrayList<>();
            for (String state : nullSafe(machine.getStates())) {
                if (!isBlank(state)) states.add(state.trim());
            }
            if (!states.isEmpty()) return states;
        }
        return List.of();
    }

    /**
     * Maps each status value to an {@code ast-badge} variant, producing a TS object literal.
     * Unrecognised states fall back to {@code neutral}, so an unexpected value is only ever a
     * cosmetic miss rather than a broken column.
     */
    private String badgeVariantsExpression(List<String> states) {
        StringBuilder sb = new StringBuilder("{ ");
        for (int i = 0; i < states.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(escapeTsSingleQuoted(states.get(i))).append("': '")
              .append(badgeVariantFor(states.get(i))).append('\'');
        }
        return sb.append(" }").toString();
    }

    private static final Set<String> SUCCESS_STATES = Set.of(
            "ACTIVE", "APPROVED", "COMPLETED", "COMPLETE", "DONE", "PAID", "VALIDATED",
            "CONFIRMED", "DELIVERED", "IN_STOCK", "ENABLED", "PUBLISHED", "CLOSED");
    private static final Set<String> DANGER_STATES = Set.of(
            "REJECTED", "CANCELLED", "CANCELED", "FAILED", "INACTIVE", "ARCHIVED", "BLOCKED",
            "EXPIRED", "OUT_OF_STOCK", "DISABLED", "DELETED", "SUSPENDED");
    private static final Set<String> WARNING_STATES = Set.of(
            "PENDING", "DRAFT", "ON_HOLD", "WAITING", "SUBMITTED", "IN_REVIEW", "LOW_STOCK",
            "RESERVED", "REQUESTED");
    private static final Set<String> INFO_STATES = Set.of(
            "IN_PROGRESS", "PROCESSING", "SHIPPED", "NEW", "ASSIGNED", "OPEN", "STARTED");

    private String badgeVariantFor(String state) {
        String key = normaliseEnum(state);
        if (key == null) return "neutral";
        if (SUCCESS_STATES.contains(key)) return "success";
        if (DANGER_STATES.contains(key)) return "danger";
        if (WARNING_STATES.contains(key)) return "warning";
        if (INFO_STATES.contains(key)) return "info";
        return "neutral";
    }

    private String mapEndpointReturnType(String backendReturnType, String entityClass) {
        if (backendReturnType == null) return entityClass;
        if (backendReturnType.startsWith("List<")) return entityClass + "[]";
        if ("void".equals(backendReturnType)) return "void";
        return entityClass;
    }

    private String deriveInputType(FrontendField f) {
        String lower = f.getName().toLowerCase(Locale.ROOT);
        if (lower.contains("password")) return "password";
        if (lower.endsWith("email")) return "email";
        return switch (f.getTsType()) {
            case "number" -> "number";
            case "boolean" -> "checkbox";
            case "string" -> {
                if (lower.contains("date") && !lower.contains("datetime")) yield "date";
                if (lower.contains("datetime") || lower.contains("timestamp")) yield "datetime-local";
                yield "text";
            }
            default -> "text";
        };
    }

    private List<FrontendNavItem> buildNavigation(Pcsf pcsf, List<FrontendModule> modules, List<BackendRole> roles) {
        List<FrontendNavItem> out = new ArrayList<>();
        List<String> roleEnums = roles.stream().map(BackendRole::getEnumValue).toList();
        if (pcsf.getUserInterface() != null && !nullSafe(pcsf.getUserInterface().getNavigation()).isEmpty()) {
            for (PcsfNavItem n : pcsf.getUserInterface().getNavigation()) {
                if (isBlank(n.getRoutePath())) continue;
                out.add(FrontendNavItem.builder()
                        .label(!isBlank(n.getLabel()) ? n.getLabel() : toTitleCase(n.getRoutePath()))
                        .path(n.getRoutePath())
                        // Same fallback as the derived path below: a declared nav item that simply
                        // omits an icon should get the same guess as one the PCSF never mentioned,
                        // rather than always landing on the generic list glyph.
                        .icon(!isBlank(n.getIcon()) ? n.getIcon()
                                : iconForModule(!isBlank(n.getLabel()) ? n.getLabel() : n.getRoutePath()))
                        .roles(!nullSafe(n.getVisibleToRoles()).isEmpty() ? new ArrayList<>(n.getVisibleToRoles()) : new ArrayList<>(roleEnums))
                        .build());
            }
            return out;
        }
        for (FrontendModule m : modules) {
            out.add(FrontendNavItem.builder()
                    .label(toTitleCase(m.getComponentPrefix()))
                    .path("/" + m.getComponentPrefix())
                    .icon(iconForModule(m.getComponentPrefix()))
                    .roles(new ArrayList<>(roleEnums))
                    .build());
        }
        return out;
    }

    /**
     * Best-effort sidebar glyph for a module with no icon of its own. Purely cosmetic — it never
     * affects generated code — and every unmatched name lands on a neutral list icon, so an
     * unrecognised domain is styled plainly rather than wrongly.
     */
    private String iconForModule(String moduleName) {
        if (isBlank(moduleName)) return "mdi:view-list";
        String lower = moduleName.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(user|account|member).*")) return "mdi:account-group";
        if (lower.matches(".*(product|item|stock|inventory).*")) return "mdi:package-variant";
        if (lower.matches(".*(order|sale|purchase).*")) return "mdi:shopping";
        if (lower.matches(".*(movement|transaction).*")) return "mdi:swap-horizontal";
        if (lower.matches(".*(report|export|analytic).*")) return "mdi:chart-bar";
        if (lower.matches(".*(setting|config).*")) return "mdi:cog";
        if (lower.matches(".*(dashboard|home).*")) return "mdi:view-dashboard";
        return "mdi:view-list";
    }

    // ── Infrastructure projection ────────────────────────────────────────────

    public InfraProjection buildInfraProjection(Pcsf pcsf) {
        PcsfProject project = pcsf.getProject();
        PcsfDerivedNames derived = project != null ? project.getDerived() : null;
        PcsfDatabaseConfig db = pcsf.getDatabaseConfig();
        PcsfInfrastructureConfig infra = pcsf.getInfrastructureConfig() != null
                ? pcsf.getInfrastructureConfig() : new PcsfInfrastructureConfig();

        String artifactId = derived != null && !isBlank(derived.getMavenArtifactId())
                ? derived.getMavenArtifactId() : "app";
        String appName = project != null ? firstNonBlank(fv(project.getDisplayName()), fv(project.getName())) : null;
        if (isBlank(appName)) appName = artifactId;

        return InfraProjection.builder()
                .appName(appName)
                .artifactId(artifactId)
                .backendServiceName(artifactId + "-backend")
                .frontendServiceName(artifactId + "-frontend")
                .databaseServiceName(artifactId + "-db")
                .databaseName(coalesce(derived != null ? derived.getDatabaseName() : null,
                        db != null ? db.getName() : null, artifactId + "_db"))
                .databaseUser(coalesce(derived != null ? derived.getDatabaseUser() : null,
                        db != null ? db.getUser() : null, artifactId + "_user"))
                .backendPort(infra.getBackendPort())
                .frontendPort(infra.getFrontendPort())
                .deploymentTarget(infra.getDeploymentTarget())
                .build();
    }

    // ── TypeScript type mapping ──────────────────────────────────────────────

    private String mapTsType(String javaTypeRaw) {
        String javaType = mapJavaType(javaTypeRaw); // reuse the same normalisation
        return switch (javaType) {
            case "String", "UUID" -> "string";
            case "Integer", "Long", "BigDecimal", "Double", "Float" -> "number";
            case "Boolean" -> "boolean";
            case "LocalDate", "LocalDateTime" -> "string";
            default -> "any";
        };
    }

    // ── Colour helpers ────────────────────────────────────────────────────────

    private String darkenHex(String hex, int percent) {
        int[] rgb = parseHex(hex);
        if (rgb == null) return hex;
        double factor = 1.0 - (percent / 100.0);
        return toHex((int) Math.round(rgb[0] * factor), (int) Math.round(rgb[1] * factor), (int) Math.round(rgb[2] * factor));
    }

    private String lightenHex(String hex, int percent) {
        int[] rgb = parseHex(hex);
        if (rgb == null) return hex;
        double factor = percent / 100.0;
        return toHex(
                (int) Math.round(rgb[0] + (255 - rgb[0]) * factor),
                (int) Math.round(rgb[1] + (255 - rgb[1]) * factor),
                (int) Math.round(rgb[2] + (255 - rgb[2]) * factor));
    }

    private String hexToRgba(String hex, double alpha) {
        int[] rgb = parseHex(hex);
        if (rgb == null) return "rgba(0, 0, 0, " + alpha + ")";
        return "rgba(" + rgb[0] + ", " + rgb[1] + ", " + rgb[2] + ", " + alpha + ")";
    }

    private int[] parseHex(String hex) {
        if (isBlank(hex)) return null;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        if (h.length() != 6) return null;
        try {
            return new int[]{
                    Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16)};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x",
                Math.min(255, Math.max(0, r)),
                Math.min(255, Math.max(0, g)),
                Math.min(255, Math.max(0, b)));
    }

    private String toTitleCase(String input) {
        if (isBlank(input)) return "";
        List<String> words = splitWords(input);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    // ── Project info ────────────────────────────────────────────────────────

    private BackendProjectInfo buildProjectInfo(Pcsf pcsf) {
        PcsfProject project = pcsf.getProject();
        PcsfDerivedNames derived = project != null ? project.getDerived() : null;
        PcsfApiConfig api = pcsf.getApiConfig() != null ? pcsf.getApiConfig() : new PcsfApiConfig();
        PcsfInfrastructureConfig infra = pcsf.getInfrastructureConfig();
        PcsfDatabaseConfig db = pcsf.getDatabaseConfig();

        String packageName = derived != null && !isBlank(derived.getJavaRootPackage())
                ? derived.getJavaRootPackage() : "com.example.app";

        String appName = project != null ? firstNonBlank(fv(project.getDisplayName()), fv(project.getName())) : null;
        if (isBlank(appName)) appName = "Application";

        return BackendProjectInfo.builder()
                .appName(appName)
                .artifactId(derived != null && !isBlank(derived.getMavenArtifactId())
                        ? derived.getMavenArtifactId() : "app")
                .packageName(packageName)
                .packagePath(packageName.replace('.', '/'))
                .databaseName(coalesce(derived != null ? derived.getDatabaseName() : null,
                        db != null ? db.getName() : null, "app_db"))
                .databaseUser(coalesce(derived != null ? derived.getDatabaseUser() : null,
                        db != null ? db.getUser() : null, "app_user"))
                .backendPort(infra != null ? infra.getBackendPort() : 8080)
                .jwtAccessTokenValidityMs(api.getJwtAccessTokenValidityMs())
                .jwtRefreshTokenValidityMs(api.getJwtRefreshTokenValidityMs())
                .corsAllowedOriginsDev(!isBlank(api.getCorsAllowedOriginsDev())
                        ? api.getCorsAllowedOriginsDev() : "http://localhost:4200")
                .versionPrefix(!isBlank(api.getVersionPrefix()) ? api.getVersionPrefix() : "/api/v1")
                .build();
    }

    // ── Entities ────────────────────────────────────────────────────────────

    /**
     * The primary-key strategy, preferring the entity's own declared {@code id} attribute over
     * {@code primaryKeyStrategy}.
     *
     * <p>{@code primaryKeyStrategy} is not part of the schema the inference pass is asked to fill
     * in — it is a field initialiser on the DTO, re-serialised into the stored PCSF on every pass.
     * So it reads {@code "UUID"} whether or not anything chose UUID, while the declared
     * {@code id} attribute is something the model actually wrote. Trusting the default over the
     * declaration produced entities with a {@code UUID} primary key and {@code Long} foreign keys
     * pointing at them — code that cannot compile the moment a repository lookup is written.
     */
    private String resolveIdStrategy(PcsfEntity e) {
        for (PcsfAttribute a : nullSafe(e.getAttributes())) {
            if (a == null || !"id".equalsIgnoreCase(fv(a.getName()))) continue;
            String declared = mapJavaType(fv(a.getJavaType()));
            if ("Long".equals(declared) || "Integer".equals(declared)) return "IDENTITY";
            if ("UUID".equals(declared)) return "UUID";
            break;
        }
        return !isBlank(e.getPrimaryKeyStrategy())
                ? e.getPrimaryKeyStrategy().toUpperCase(Locale.ROOT) : "UUID";
    }

    /**
     * Retypes every foreign-key field to match the primary key of the entity it references.
     *
     * <p>A PCSF can declare {@code Stock.productId} as {@code Long} while {@code Product}'s key is
     * a {@code UUID} — the two come from different parts of the model and nothing reconciles them.
     * Emitted verbatim, the generated project cannot compile as soon as anything writes
     * {@code productRepository.findById(stock.getProductId())}, and the AI fix loop cannot repair
     * it either: the mismatch spans two files, so no single-file edit resolves it.
     *
     * <p>Resolution is by name against the project's own entity list — {@code productId} to
     * {@code Product}, {@code parentCategoryId} to {@code Category} — and a field naming no
     * declared entity is left exactly as the PCSF declared it.
     */
    private void alignForeignKeyTypes(Map<String, BackendEntity> entitiesById) {
        Map<String, BackendEntity> byLowerName = new LinkedHashMap<>();
        for (BackendEntity be : entitiesById.values()) {
            if (be.getClassName() != null) byLowerName.put(be.getClassName().toLowerCase(Locale.ROOT), be);
        }
        for (BackendEntity owner : entitiesById.values()) {
            for (BackendField field : nullSafe(owner.getFields())) {
                BackendEntity target = referencedEntity(field.getName(), byLowerName);
                if (target == null || target.getIdType() == null) continue;
                if (target.getIdType().equals(field.getJavaType())) continue;
                log.debug("Retyping {}.{} from {} to {} to match {}'s primary key",
                        owner.getClassName(), field.getName(), field.getJavaType(),
                        target.getIdType(), target.getClassName());
                field.setJavaType(target.getIdType());
                // A sample value for the old type would no longer be assignable.
                field.setSampleValue(sampleValueFor(target.getIdType(),
                        field.getMinLength(), field.getMaxLength()));
            }
        }
    }

    /** The entity a field named {@code <something>Id} points at, or null if it names none. */
    private BackendEntity referencedEntity(String fieldName, Map<String, BackendEntity> byLowerName) {
        if (fieldName == null || fieldName.length() < 3) return null;
        if (!fieldName.toLowerCase(Locale.ROOT).endsWith("id")) return null;
        String stem = fieldName.substring(0, fieldName.length() - 2).toLowerCase(Locale.ROOT);
        if (stem.isEmpty()) return null;

        BackendEntity exact = byLowerName.get(stem);
        if (exact != null) return exact;
        // "parentCategoryId" and "assignedByUserId" still name a declared entity; take the longest
        // match so "categoryId" cannot be claimed by a shorter entity name that it merely contains.
        BackendEntity best = null;
        for (Map.Entry<String, BackendEntity> candidate : byLowerName.entrySet()) {
            if (stem.endsWith(candidate.getKey())
                    && (best == null || candidate.getKey().length() > best.getClassName().length())) {
                best = candidate.getValue();
            }
        }
        return best;
    }

    /** Whether the PCSF declares a status machine for this entity, by id or by class name. */
    private boolean hasStatusMachine(Pcsf pcsf, PcsfEntity entity, String className) {
        for (PcsfStatusMachine machine : nullSafe(pcsf.getStatusMachines())) {
            String target = machine == null ? null : machine.getEntityId();
            if (isBlank(target)) continue;
            if (target.equals(entity.getId()) || target.equalsIgnoreCase(className)) return true;
        }
        return false;
    }

    /**
     * Whether a field is set by the application rather than supplied by the client.
     *
     * <p>Two signals, both declared rather than guessed from vocabulary:
     * <ul>
     *   <li>the PCSF explicitly marks the attribute as absent from forms, or</li>
     *   <li>the entity has a status machine and this is its state field — the value then belongs to
     *       the machine's transitions, and accepting it on create would let a client start a record
     *       in any state it liked.</li>
     * </ul>
     *
     * <p>The status check is deliberately scoped to entities that actually declare a machine, so a
     * {@code maritalStatus} on an entity with no state model stays an ordinary editable field.
     */
    private boolean isServerManaged(PcsfAttribute a, String name, boolean hasStatusMachine) {
        if (a.getShowInForm() != null && Boolean.FALSE.equals(a.getShowInForm().getValue())) return true;
        return hasStatusMachine && isStateFieldName(name);
    }

    private boolean isStateFieldName(String name) {
        if (isBlank(name)) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("status") || lower.equals("state")
                || lower.endsWith("status") || lower.endsWith("state");
    }

    private BackendEntity buildEntity(PcsfEntity e, String className, boolean hasStatusMachine) {
        List<BackendField> fields = new ArrayList<>();
        for (PcsfAttribute a : nullSafe(e.getAttributes())) {
            String name = fv(a.getName());
            if (isBlank(name) || RESERVED_ATTRIBUTE_NAMES.contains(name.toLowerCase(Locale.ROOT))) continue;

            PcsfConstraints c = a.getConstraints();
            String javaType = mapJavaType(fv(a.getJavaType()));
            Integer minLength = c != null ? fvInt(c.getMinLength()) : null;
            Integer maxLength = c != null ? fvInt(c.getMaxLength()) : null;
            fields.add(BackendField.builder()
                    .name(name)
                    .columnName(coalesce(fv(a.getColumnName()), toSnakeCase(name)))
                    .javaType(javaType)
                    .required(c != null && fvBool(c.getRequired(), false))
                    .unique(c != null && fvBool(c.getUnique(), false))
                    .minLength(minLength)
                    .maxLength(maxLength)
                    .id(false)
                    .serverManaged(isServerManaged(a, name, hasStatusMachine))
                    .sampleValue(sampleValueFor(javaType, minLength, maxLength))
                    .build());
        }

        // An entity is only persist-testable when every NOT NULL field can be given a value.
        boolean testable = fields.stream()
                .noneMatch(f -> f.isRequired() && f.getSampleValue() == null);

        String idStrategy = resolveIdStrategy(e);

        return BackendEntity.builder()
                .className(className)
                .tableName(coalesce(fv(e.getTableName()), toSnakeCase(className)))
                .instanceName(toLowerCamelCase(className))
                .audited(e.isAuditFields())
                .idStrategy(idStrategy)
                .idType("IDENTITY".equals(idStrategy) ? "Long" : "UUID")
                .fields(fields)
                .relationships(new ArrayList<>())
                .testable(testable)
                .build();
    }

    /**
     * Builds a compilable Java literal/expression for {@code javaType}, or {@code null} when the
     * type is not one we recognise (an AI-introduced enum, for instance). String values are sized
     * to satisfy any {@code @Size(min, max)} the entity declares, so generated persist tests are
     * not rejected by bean validation. Types are fully qualified to keep the templates free of
     * import bookkeeping.
     */
    private String sampleValueFor(String javaType, Integer minLength, Integer maxLength) {
        if (isBlank(javaType)) return null;
        return switch (javaType) {
            case "String" -> '"' + sizedSampleText(minLength, maxLength) + '"';
            case "Integer" -> "1";
            case "Long" -> "1L";
            case "Double", "Float" -> "1.0";
            case "BigDecimal" -> "new java.math.BigDecimal(\"1.00\")";
            case "Boolean" -> "true";
            case "LocalDate" -> "java.time.LocalDate.now()";
            case "LocalDateTime" -> "java.time.LocalDateTime.now()";
            case "UUID" -> "java.util.UUID.randomUUID()";
            default -> null; // unknown/custom type — cannot construct safely
        };
    }

    /** Sample text padded to {@code minLength} and clipped to {@code maxLength}. */
    private String sizedSampleText(Integer minLength, Integer maxLength) {
        String base = "sample";
        if (minLength != null && minLength > base.length()) {
            base = base + "x".repeat(minLength - base.length());
        }
        if (maxLength != null && maxLength > 0 && maxLength < base.length()) {
            base = base.substring(0, maxLength);
        }
        return base;
    }

    private void applyRelationships(Pcsf pcsf, Map<String, BackendEntity> entitiesById,
                                    Map<String, String> entityIdToClassName) {
        for (PcsfRelationship r : nullSafe(pcsf.getRelationships())) {
            BackendEntity from = entitiesById.get(r.getFromEntityId());
            String toClass = entityIdToClassName.get(r.getToEntityId());
            if (from == null || isBlank(toClass)) continue;

            String type = normaliseEnum(fv(r.getCardinality()));
            if (isBlank(type)) type = "MANY_TO_ONE";

            BackendRelationship rel;
            switch (type) {
                case "ONE_TO_MANY" -> rel = BackendRelationship.builder()
                        .fieldName(toLowerCamelCase(pluralise(toClass)))
                        .targetEntity(toClass).relationType("ONE_TO_MANY").owning(false).build();
                case "MANY_TO_MANY" -> rel = BackendRelationship.builder()
                        .fieldName(toLowerCamelCase(pluralise(toClass)))
                        .targetEntity(toClass).relationType("MANY_TO_MANY")
                        .joinColumn(coalesce(r.getJoinTableName(),
                                toSnakeCase(from.getClassName()) + "_" + toSnakeCase(toClass)))
                        .owning(from.getClassName().equals(entityIdToClassName.get(r.getOwningEntityId()))
                                || r.getOwningEntityId() == null)
                        .build();
                case "ONE_TO_ONE" -> rel = BackendRelationship.builder()
                        .fieldName(toLowerCamelCase(toClass))
                        .targetEntity(toClass).relationType("ONE_TO_ONE")
                        .joinColumn(coalesce(r.getJoinColumnName(), toSnakeCase(toClass) + "_id"))
                        .owning(true).build();
                default -> rel = BackendRelationship.builder() // MANY_TO_ONE
                        .fieldName(toLowerCamelCase(toClass))
                        .targetEntity(toClass).relationType("MANY_TO_ONE")
                        .joinColumn(coalesce(r.getJoinColumnName(), toSnakeCase(toClass) + "_id"))
                        .owning(true).build();
            }
            from.getRelationships().add(rel);
        }
    }

    // ── Roles ───────────────────────────────────────────────────────────────

    private List<BackendRole> buildRoles(Pcsf pcsf) {
        Map<String, BackendRole> byEnum = new LinkedHashMap<>();
        for (PcsfActor a : nullSafe(pcsf.getActors())) {
            String name = fv(a.getName());
            if (isBlank(name)) continue;
            String enumValue = toScreamingSnakeCase(name);
            if (isBlank(enumValue) || byEnum.containsKey(enumValue)) continue;
            byEnum.put(enumValue, BackendRole.builder()
                    .enumValue(enumValue)
                    .roleName("ROLE_" + enumValue)
                    .type(fv(a.getType()))
                    .build());
        }
        return new ArrayList<>(byEnum.values());
    }

    // ── Modules ─────────────────────────────────────────────────────────────

    private List<BackendModule> buildModules(Pcsf pcsf, BackendProjectInfo info, List<BackendRole> roles,
                                             Map<String, BackendEntity> entitiesById) {
        List<String> allRoleEnums = roles.stream().map(BackendRole::getEnumValue).toList();
        List<BackendModule> modules = new ArrayList<>();

        for (PcsfModule m : nullSafe(pcsf.getModules())) {
            String moduleName = fv(m.getName());
            if (isBlank(moduleName)) continue;

            BackendEntity primary = resolvePrimaryEntity(pcsf, m, entitiesById);
            String entityClass = primary != null ? primary.getClassName() : "Entity";
            String entityInstance = primary != null ? primary.getInstanceName() : "entity";

            List<String> crud = fvList(m.getCrudOperations());
            if (crud.isEmpty()) crud = DEFAULT_CRUD;
            List<String> ops = crud.stream().map(this::normaliseEnum).toList();

            List<BackendEndpoint> endpoints = new ArrayList<>();
            String responseType = entityClass + "ResponseDto";
            String createType = "Create" + entityClass + "Dto";

            // The PCSF's own declared API wins when it has one for this module. Only when it
            // declares nothing do we fall back to inventing a CRUD surface from crudOperations.
            ModuleApi declared = buildDeclaredModuleApi(pcsf, m, entityClass, allRoleEnums);
            if (declared != null) {
                modules.add(BackendModule.builder()
                        .controllerName(toPascalCase(moduleName) + "Controller")
                        .serviceName(toPascalCase(moduleName) + "Service")
                        .serviceImplName(toPascalCase(moduleName) + "ServiceImpl")
                        .repositoryName(entityClass + "Repository")
                        .requestMapping(declared.requestMapping())
                        .packageName(info.getPackageName())
                        .entityClassName(entityClass)
                        .entityInstanceName(entityInstance)
                        .endpoints(declared.endpoints())
                        .build());
                continue;
            }

            if (ops.contains("CREATE")) {
                endpoints.add(endpoint("POST", "", "create" + entityClass, responseType, true, false,
                        createType, responseType, roleSet(pcsf, m, "CREATE", allRoleEnums)));
            }
            if (ops.contains("READ")) {
                BackendEndpoint list = endpoint("GET", "", "getAll" + pluralise(entityClass),
                        "Page<" + responseType + ">",
                        false, false, null, responseType, roleSet(pcsf, m, "READ", allRoleEnums));
                list.setPaged(true);
                endpoints.add(list);
                endpoints.add(endpoint("GET", "/{id}", "get" + entityClass + "ById", responseType,
                        false, true, null, responseType, roleSet(pcsf, m, "READ", allRoleEnums)));
            }
            if (ops.contains("UPDATE")) {
                endpoints.add(endpoint("PUT", "/{id}", "update" + entityClass, responseType, true, true,
                        createType, responseType, roleSet(pcsf, m, "UPDATE", allRoleEnums)));
            }
            if (ops.contains("DELETE")) {
                endpoints.add(endpoint("DELETE", "/{id}", "delete" + entityClass, "void", false, true,
                        null, null, roleSet(pcsf, m, "DELETE", allRoleEnums)));
            }

            for (PcsfUseCase uc : nullSafe(m.getUseCases())) {
                String action = customAction(fv(uc.getName()));
                if (action == null) continue;
                BackendEndpoint custom = endpoint("POST", "/{id}/" + action, action, responseType,
                        false, true, null, responseType, allRoleEnums);
                custom.setCrud(false);
                endpoints.add(custom);
            }

            modules.add(BackendModule.builder()
                    .controllerName(toPascalCase(moduleName) + "Controller")
                    .serviceName(toPascalCase(moduleName) + "Service")
                    .serviceImplName(toPascalCase(moduleName) + "ServiceImpl")
                    .repositoryName(entityClass + "Repository")
                    .requestMapping(info.getVersionPrefix() + "/" + toKebabCase(moduleName))
                    .packageName(info.getPackageName())
                    .entityClassName(entityClass)
                    .entityInstanceName(entityInstance)
                    .endpoints(endpoints)
                    .build());
        }
        return modules;
    }

    // ── Declared API surface ────────────────────────────────────────────────

    /** A module's controller base path plus the operations mounted under it. */
    private record ModuleApi(String requestMapping, List<BackendEndpoint> endpoints) {}

    private static final Set<String> SUPPORTED_HTTP_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    /**
     * Builds a module's API from {@code pcsf.endpoints} — the contract the PCSF actually declares.
     *
     * <p>Only the API <em>surface</em> is taken from the declaration: verb, path, operation name,
     * pagination and roles. Types stay derived from the entity model, because the DTO and entity
     * classes are generated from entities; a declared {@code responseEntityId} routinely points at
     * something that is not an entity at all (a page wrapper, a file download, an ack), and naming
     * a DTO after it would emit references to classes nothing generates.
     *
     * @return {@code null} when the PCSF declares no usable endpoint for this module, so the
     *         caller falls back to the CRUD convention
     */
    private ModuleApi buildDeclaredModuleApi(Pcsf pcsf, PcsfModule m, String entityClass,
                                             List<String> allRoleEnums) {
        if (m.getId() == null) return null;

        List<PcsfApiEndpoint> declared = new ArrayList<>();
        for (PcsfApiEndpoint ep : nullSafe(pcsf.getEndpoints())) {
            if (ep == null || !m.getId().equals(ep.getModuleId())) continue;
            if (isBlank(ep.getPath()) || isBlank(ep.getHttpMethod())) continue;
            if (!SUPPORTED_HTTP_METHODS.contains(ep.getHttpMethod().trim().toUpperCase(Locale.ROOT))) continue;
            declared.add(ep);
        }
        if (declared.isEmpty()) return null;

        String requestMapping = commonPathPrefix(declared.stream().map(PcsfApiEndpoint::getPath).toList());
        if (isBlank(requestMapping)) return null;

        String responseType = entityClass + "ResponseDto";
        String createType = "Create" + entityClass + "Dto";

        List<BackendEndpoint> endpoints = new ArrayList<>();
        Set<String> usedMethodNames = new java.util.HashSet<>();

        for (PcsfApiEndpoint ep : declared) {
            String verb = ep.getHttpMethod().trim().toUpperCase(Locale.ROOT);
            String relative = ep.getPath().trim().substring(requestMapping.length());
            if (!relative.isEmpty() && !relative.startsWith("/")) relative = "/" + relative;

            List<String> pathVariables = pathVariableNames(relative);
            // Canonical only when the operation sits directly on the collection or on a single row
            // — /{id}/archive and /export are real operations, but they are not the CRUD five, so
            // they must reach the stub branch and be implemented by the logic-injection pass.
            boolean bareCollection = relative.isEmpty();
            boolean bareRow = pathVariables.size() == 1
                    && relative.equals("/{" + pathVariables.get(0) + "}");

            boolean paged = ep.isPaginated() && "GET".equals(verb) && bareCollection;
            boolean hasBody = ep.getRequestBodyEntityId() != null
                    && (verb.equals("POST") || verb.equals("PUT") || verb.equals("PATCH"));

            boolean crud = switch (verb) {
                case "POST"   -> bareCollection;
                case "GET"    -> bareCollection || bareRow;
                case "PUT"    -> bareRow;
                case "DELETE" -> bareRow;
                default       -> false;   // PATCH is never one of the generated CRUD bodies
            };

            String returnType = "DELETE".equals(verb) ? "void"
                    : paged ? "Page<" + responseType + ">" : responseType;

            String methodName = uniqueMethodName(usedMethodNames,
                    declaredMethodName(ep, verb, relative, entityClass));

            endpoints.add(BackendEndpoint.builder()
                    .httpMethod(verb)
                    .path(relative)
                    .methodName(methodName)
                    .returnType(returnType)
                    .hasRequestBody(hasBody)
                    .pathVariables(pathVariables)
                    .idVariable(pathVariables.isEmpty() ? "id" : pathVariables.get(0))
                    .requestBodyType(hasBody ? createType : null)
                    .responseType("void".equals(returnType) ? null : responseType)
                    .crud(crud)
                    .paged(paged)
                    .roles(declaredRoles(ep, allRoleEnums))
                    .build());
        }
        return new ModuleApi(requestMapping, endpoints);
    }

    /**
     * Longest path prefix shared by every declared path, on segment boundaries, stopping before
     * the first path variable.
     *
     * <p>The stop matters: a module whose every path carries {@code /{productId}} would otherwise
     * fold that variable into {@code @RequestMapping}, leaving the methods with a variable in the
     * class-level mapping that none of them declares a {@code @PathVariable} for.
     */
    private String commonPathPrefix(List<String> paths) {
        List<List<String>> split = paths.stream()
                .map(p -> java.util.Arrays.stream(p.trim().split("/"))
                        .filter(s -> !s.isEmpty()).toList())
                .toList();
        List<String> prefix = new ArrayList<>();
        int shortest = split.stream().mapToInt(List::size).min().orElse(0);
        for (int i = 0; i < shortest; i++) {
            String segment = split.get(0).get(i);
            if (segment.startsWith("{")) break;
            final int idx = i;
            if (!split.stream().allMatch(s -> s.get(idx).equals(segment))) break;
            prefix.add(segment);
        }
        return prefix.isEmpty() ? "" : "/" + String.join("/", prefix);
    }

    /** Path variable names, in order, as written between braces. */
    private List<String> pathVariableNames(String path) {
        List<String> names = new ArrayList<>();
        if (isBlank(path)) return names;
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\\{([^}/]+)}").matcher(path);
        while (matcher.find()) {
            String name = toLowerCamelCase(matcher.group(1));
            if (!isBlank(name) && Character.isLetter(name.charAt(0)) && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * The declared {@code operationId}, sanitised into a Java identifier. Falls back to a name
     * built from the verb and path when the declaration has none or it survives sanitising empty.
     */
    private String declaredMethodName(PcsfApiEndpoint ep, String verb, String relative, String entityClass) {
        String candidate = toLowerCamelCase(ep.getOperationId());
        if (!isBlank(candidate) && Character.isLetter(candidate.charAt(0))) return candidate;

        String tail = relative.replaceAll("\\{[^}/]*}", "").replaceAll("[^A-Za-z0-9]+", " ").trim();
        String derived = toLowerCamelCase(verb.toLowerCase(Locale.ROOT) + " " + entityClass
                + (tail.isEmpty() ? "" : " " + tail));
        return isBlank(derived) || !Character.isLetter(derived.charAt(0)) ? "handle" + entityClass : derived;
    }

    /** Appends a numeric suffix rather than emitting two methods with the same signature. */
    private String uniqueMethodName(Set<String> used, String candidate) {
        String name = candidate;
        int suffix = 2;
        while (!used.add(name)) {
            name = candidate + suffix++;
        }
        return name;
    }

    /**
     * Declared roles, normalised onto the project's role enum. An endpoint marked as not requiring
     * auth gets no roles at all, so no {@code @PreAuthorize} is rendered; anything that requires
     * auth but names no resolvable role falls back to every role, matching {@code roleSet}.
     */
    private List<String> declaredRoles(PcsfApiEndpoint ep, List<String> allRoleEnums) {
        if (!ep.isRequiresAuth()) return new ArrayList<>();
        List<String> matched = new ArrayList<>();
        for (String raw : nullSafe(ep.getRequiredRoles())) {
            if (isBlank(raw)) continue;
            String en = toScreamingSnakeCase(raw.startsWith("ROLE_") ? raw.substring(5) : raw);
            if (!isBlank(en) && allRoleEnums.contains(en) && !matched.contains(en)) matched.add(en);
        }
        return matched.isEmpty() ? new ArrayList<>(allRoleEnums) : matched;
    }

    private BackendEndpoint endpoint(String method, String path, String methodName, String returnType,
                                     boolean hasBody, boolean hasPathVar, String bodyType, String responseType,
                                     List<String> roles) {
        return BackendEndpoint.builder()
                .httpMethod(method).path(path).methodName(methodName).returnType(returnType)
                .hasRequestBody(hasBody)
                // Convention-derived paths are always the generic /{id}, so the templates see the
                // same shape here as they do for a declared endpoint that names its variable.
                .pathVariables(hasPathVar ? new ArrayList<>(List.of("id")) : new ArrayList<>())
                .idVariable("id")
                .requestBodyType(bodyType).responseType(responseType)
                .crud(true).roles(new ArrayList<>(roles)).build();
    }

    private BackendEntity resolvePrimaryEntity(Pcsf pcsf, PcsfModule m, Map<String, BackendEntity> entitiesById) {
        for (PcsfEntity e : nullSafe(pcsf.getEntities())) {
            if (m.getId() != null && m.getId().equals(e.getPrimaryModuleId())) {
                BackendEntity be = entitiesById.get(e.getId() != null ? e.getId() : fv(e.getName()));
                if (be != null) return be;
            }
        }
        String moduleName = fv(m.getName());
        if (!isBlank(moduleName)) {
            String needle = toPascalCase(moduleName);
            for (BackendEntity be : entitiesById.values()) {
                if (be.getClassName().equalsIgnoreCase(needle)
                        || needle.contains(be.getClassName()) || be.getClassName().contains(needle)) {
                    return be;
                }
            }
        }
        return entitiesById.values().stream().findFirst().orElse(null);
    }

    private List<String> roleSet(Pcsf pcsf, PcsfModule m, String operation, List<String> allRoleEnums) {
        List<String> matched = new ArrayList<>();
        for (PcsfAccessControlRule rule : nullSafe(pcsf.getAccessControlRules())) {
            if (m.getId() == null || !m.getId().equals(rule.getModuleId())) continue;
            if (rule.getOperation() != null && !rule.getOperation().equalsIgnoreCase(operation)) continue;
            for (String r : fvList(rule.getAllowedRoles())) {
                String en = toScreamingSnakeCase(r.startsWith("ROLE_") ? r.substring(5) : r);
                if (!isBlank(en) && !matched.contains(en)) matched.add(en);
            }
        }
        return matched.isEmpty() ? allRoleEnums : matched;
    }

    /** Returns the camelCase action for a non-CRUD use case, or null if it is plain CRUD. */
    private String customAction(String useCaseName) {
        if (isBlank(useCaseName)) return null;
        String[] words = useCaseName.trim().split("[\\s_\\-]+");
        if (words.length == 0) return null;
        String firstVerb = words[0].toLowerCase(Locale.ROOT);
        if (CRUD_VERBS.contains(firstVerb)) return null;
        String action = toLowerCamelCase(useCaseName.trim());
        // Guard: only emit a custom endpoint when the derived name is a valid Java identifier
        // start (a letter). Anything else is dropped rather than rendered into broken source.
        if (action.isEmpty() || !Character.isLetter(action.charAt(0))) return null;
        return action;
    }

    // ── Type mapping ──────────────────────────────────────────────────────────

    private String mapJavaType(String raw) {
        if (isBlank(raw)) return "String";
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "STRING", "TEXT", "VARCHAR", "CHAR" -> "String";
            case "INTEGER", "INT" -> "Integer";
            case "LONG", "BIGINT" -> "Long";
            case "DECIMAL", "DOUBLE", "FLOAT", "BIGDECIMAL", "NUMERIC" -> "BigDecimal";
            case "BOOLEAN", "BOOL" -> "Boolean";
            case "DATE" -> "LocalDate";
            case "DATETIME", "LOCALDATETIME", "TIMESTAMP" -> "LocalDateTime";
            case "UUID" -> "UUID";
            default -> {
                // Already a Java type (e.g. "String", "BigDecimal") — keep as supplied.
                yield raw.trim();
            }
        };
    }

    // ── FieldValue helpers ─────────────────────────────────────────────────────

    private static String fv(FieldValue<?> f) {
        if (f == null || f.getValue() == null) return null;
        return String.valueOf(f.getValue());
    }

    @SuppressWarnings("unchecked")
    private static List<String> fvList(FieldValue<?> f) {
        if (f == null || f.getValue() == null) return List.of();
        Object v = f.getValue();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        return List.of(String.valueOf(v));
    }

    private static boolean fvBool(FieldValue<?> f, boolean dflt) {
        if (f == null || f.getValue() == null) return dflt;
        Object v = f.getValue();
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static Integer fvInt(FieldValue<?> f) {
        if (f == null || f.getValue() == null) return null;
        Object v = f.getValue();
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ── String helpers ─────────────────────────────────────────────────────────

    private String toPascalCase(String input) {
        if (isBlank(input)) return "";
        StringBuilder sb = new StringBuilder();
        for (String word : splitWords(input)) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private String toLowerCamelCase(String input) {
        String pascal = toPascalCase(input);
        if (pascal.isEmpty()) return "";
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String toKebabCase(String input) {
        if (isBlank(input)) return "";
        return String.join("-", splitWords(input)).toLowerCase(Locale.ROOT);
    }

    private String toScreamingSnakeCase(String input) {
        if (isBlank(input)) return "";
        return String.join("_", splitWords(input)).toUpperCase(Locale.ROOT);
    }

    private String toSnakeCase(String input) {
        if (isBlank(input)) return "";
        return String.join("_", splitWords(input)).toLowerCase(Locale.ROOT);
    }

    /**
     * Splits on camelCase boundaries and on any run of non-alphanumeric characters into
     * lowercase-friendly words. Crucially this drops punctuation — parentheses, slashes, commas,
     * etc. — so a use case named "Record a Stock Entry (Goods Received)" derives the identifier
     * {@code recordAStockEntryGoodsReceived} rather than {@code recordAStockEntry(goodsReceived)},
     * which is invalid Java and broke template rendering downstream.
     */
    private List<String> splitWords(String input) {
        String spaced = input.trim()
                // Split runs of capitals before a capitalised word: "recordAStockEntry" must yield
                // "record A Stock Entry", not "record AStock Entry" (which round-trips to the
                // malformed identifier "RecordAstockEntry").
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim();
        List<String> words = new ArrayList<>();
        for (String w : spaced.split(" ")) if (!w.isEmpty()) words.add(w);
        return words;
    }

    private String pluralise(String word) {
        if (isBlank(word)) return word;
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.endsWith("y") && word.length() > 1 && !"aeiou".contains(String.valueOf(lower.charAt(lower.length() - 2)))) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("ch") || lower.endsWith("sh")) {
            return word + "es";
        }
        return word + "s";
    }

    private String normaliseEnum(String value) {
        if (isBlank(value)) return null;
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private static String coalesce(String... values) {
        for (String v : values) if (!isBlank(v)) return v;
        return null;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
