package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.pcsf.*;
import afb.astyann.codegeneration.domain.projection.*;
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
            BackendEntity be = buildEntity(e, className);
            String key = e.getId() != null ? e.getId() : className;
            entitiesById.put(key, be);
            entityIdToClassName.put(key, className);
        }

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
            modules.add(buildFrontendModule(bm, entity, info));
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

    private FrontendModule buildFrontendModule(BackendModule bm, FrontendEntity entity, FrontendProjectInfo info) {
        String moduleKebab = bm.getRequestMapping().substring(bm.getRequestMapping().lastIndexOf('/') + 1);
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
                    .build());
        }

        List<FrontendColumn> listColumns = new ArrayList<>();
        int count = 0;
        for (FrontendField f : entity.getFields()) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if (lower.contains("password")) continue;
            listColumns.add(FrontendColumn.builder().label(f.getLabel()).fieldName(f.getName()).build());
            if (++count >= 6) break;
        }

        List<FrontendFormField> formFields = new ArrayList<>();
        for (FrontendField f : entity.getFields()) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if (Set.of("id", "createdat", "lastmodifiedat", "currentstock", "stockstatus").contains(lower)) continue;
            formFields.add(FrontendFormField.builder()
                    .label(f.getLabel()).fieldName(f.getName())
                    .inputType(deriveInputType(f))
                    .required(f.isRequired()).build());
        }

        return FrontendModule.builder()
                .serviceName(toPascalCase(moduleKebab) + "Service")
                .serviceFileName(moduleKebab)
                .componentPrefix(moduleKebab)
                .entityClassName(entity.getClassName())
                .entityFileName(entity.getFileName())
                .entityInstanceName(entity.getInstanceName())
                .apiPath(bm.getRequestMapping())
                .hasCreate(hasCreate).hasRead(hasRead).hasUpdate(hasUpdate).hasDelete(hasDelete)
                .endpoints(endpoints).listColumns(listColumns).formFields(formFields)
                .build();
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
                        .icon(!isBlank(n.getIcon()) ? n.getIcon() : "mdi:view-list")
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

    private String iconForModule(String moduleName) {
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

    private BackendEntity buildEntity(PcsfEntity e, String className) {
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
                    .sampleValue(sampleValueFor(javaType, minLength, maxLength))
                    .build());
        }

        // An entity is only persist-testable when every NOT NULL field can be given a value.
        boolean testable = fields.stream()
                .noneMatch(f -> f.isRequired() && f.getSampleValue() == null);

        String idStrategy = !isBlank(e.getPrimaryKeyStrategy()) ? e.getPrimaryKeyStrategy().toUpperCase(Locale.ROOT) : "UUID";

        return BackendEntity.builder()
                .className(className)
                .tableName(coalesce(fv(e.getTableName()), toSnakeCase(className)))
                .instanceName(toLowerCamelCase(className))
                .audited(e.isAuditFields())
                .idStrategy(idStrategy)
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

            if (ops.contains("CREATE")) {
                endpoints.add(endpoint("POST", "", "create" + entityClass, responseType, true, false,
                        createType, responseType, roleSet(pcsf, m, "CREATE", allRoleEnums)));
            }
            if (ops.contains("READ")) {
                endpoints.add(endpoint("GET", "", "getAll" + pluralise(entityClass), "List<" + responseType + ">",
                        false, false, null, responseType, roleSet(pcsf, m, "READ", allRoleEnums)));
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

    private BackendEndpoint endpoint(String method, String path, String methodName, String returnType,
                                     boolean hasBody, boolean hasPathVar, String bodyType, String responseType,
                                     List<String> roles) {
        return BackendEndpoint.builder()
                .httpMethod(method).path(path).methodName(methodName).returnType(returnType)
                .hasRequestBody(hasBody).hasPathVariable(hasPathVar)
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
