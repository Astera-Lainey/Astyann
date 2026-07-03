package afb.astyann.requirementservice.util;

import afb.astyann.requirementservice.domain.pcsf.*;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldSource;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PcsfFieldWriter {

    public void write(Pcsf pcsf, String path, String value) {
        if (path == null || value == null) return;
        try {
            switch (path) {
                case "project.name.value"        -> setString(pcsf.getProject().getName(), value);
                case "project.description.value" -> setString(pcsf.getProject().getDescription(), value);
                case "project.displayName.value" -> setString(pcsf.getProject().getDisplayName(), value);
                case "actors"                    -> pcsf.setActors(mergeActors(pcsf.getActors(), parseActors(value)));
                case "modules"                   -> pcsf.setModules(mergeModules(pcsf.getModules(), parseModules(value)));
                case "entities"                  -> pcsf.setEntities(mergeEntities(pcsf.getEntities(), parseEntities(value)));
                case "relationships"             -> pcsf.setRelationships(mergeRelationships(pcsf.getRelationships(), parseRelationships(value)));
                case "accessControlRules"        -> pcsf.setAccessControlRules(parseAccessControlRules(value));
                case "errorCodes"                -> pcsf.setErrorCodes(parseErrorCodes(value));
                case "userInterface.screens"     -> pcsf.getUserInterface().setScreens(
                        mergeScreens(pcsf.getUserInterface().getScreens(), parseScreens(value)));
                case "userInterface.navigation"  -> pcsf.getUserInterface().setNavigation(parseNavItems(value));

                case "conditionalFeatures.fileUpload.required.value"   ->
                        setFlag(pcsf.getConditionalFeatures().getFileUpload(), value);
                case "conditionalFeatures.dataExport.required.value"   ->
                        setFlag(pcsf.getConditionalFeatures().getDataExport(), value);
                case "conditionalFeatures.searchFilter.required.value" ->
                        setFlag(pcsf.getConditionalFeatures().getSearchFilter(), value);
                case "conditionalFeatures.multiTenancy.required.value" ->
                        setFlag(pcsf.getConditionalFeatures().getMultiTenancy(), value);

                case "publicAccess.hasPublicActor.value" ->
                        setBooleanFieldValue(pcsf.getPublicAccess().getHasPublicActor(), value);
                case "publicAccess.publicPaths" ->
                        pcsf.getPublicAccess().setPublicPaths(splitLines(value));

                case "nonFunctionalRequirements.concurrentUsers.value" -> setIntFv(
                        pcsf.getNonFunctionalRequirements()::getConcurrentUsers,
                        pcsf.getNonFunctionalRequirements()::setConcurrentUsers, value);
                case "nonFunctionalRequirements.targetResponseTimeMs.value" -> setIntFv(
                        pcsf.getNonFunctionalRequirements()::getTargetResponseTimeMs,
                        pcsf.getNonFunctionalRequirements()::setTargetResponseTimeMs, value);
                case "nonFunctionalRequirements.dataVolumeDescription.value" -> setStringFv(
                        pcsf.getNonFunctionalRequirements()::getDataVolumeDescription,
                        pcsf.getNonFunctionalRequirements()::setDataVolumeDescription, value);
                case "nonFunctionalRequirements.availabilityTarget.value" -> setStringFv(
                        pcsf.getNonFunctionalRequirements()::getAvailabilityTarget,
                        pcsf.getNonFunctionalRequirements()::setAvailabilityTarget, value);
                case "nonFunctionalRequirements.securityDepth.value" -> setStringFv(
                        pcsf.getNonFunctionalRequirements()::getSecurityDepth,
                        pcsf.getNonFunctionalRequirements()::setSecurityDepth, value);
                case "nonFunctionalRequirements.locale.value" -> setStringFv(
                        pcsf.getNonFunctionalRequirements()::getLocale,
                        pcsf.getNonFunctionalRequirements()::setLocale, value);

                case "infrastructureConfig.backendPort"      -> setIntSafe(value, pcsf.getInfrastructureConfig()::setBackendPort);
                case "infrastructureConfig.frontendPort"     -> setIntSafe(value, pcsf.getInfrastructureConfig()::setFrontendPort);
                case "infrastructureConfig.diagramRenderer"  -> pcsf.getInfrastructureConfig().setDiagramRenderer(value);
                case "infrastructureConfig.krokiInternalUrl" -> pcsf.getInfrastructureConfig().setKrokiInternalUrl(value);
                case "infrastructureConfig.deploymentTarget" -> pcsf.getInfrastructureConfig().setDeploymentTarget(value);

                case "databaseConfig.name"       -> pcsf.getDatabaseConfig().setName(value);
                case "databaseConfig.user"       -> pcsf.getDatabaseConfig().setUser(value);
                case "databaseConfig.charset"    -> pcsf.getDatabaseConfig().setCharset(value);
                case "databaseConfig.collation"  -> pcsf.getDatabaseConfig().setCollation(value);

                case "apiConfig.versionPrefix"         -> pcsf.getApiConfig().setVersionPrefix(value);
                case "apiConfig.corsAllowedOriginsDev"  -> pcsf.getApiConfig().setCorsAllowedOriginsDev(value);
                case "apiConfig.rateLimitPerMinute"     -> setIntSafe(value, pcsf.getApiConfig()::setRateLimitPerMinute);
                case "apiConfig.defaultPageSize"        -> setIntSafe(value, pcsf.getApiConfig()::setDefaultPageSize);
                case "apiConfig.maxPageSize"            -> setIntSafe(value, pcsf.getApiConfig()::setMaxPageSize);

                default -> log.warn("Unknown PCSF path: {}", path);
            }
        } catch (Exception ex) {
            log.error("Failed to write PCSF path={} value={}", path, value, ex);
        }
    }

    // ── Generic FieldValue setters ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void setString(FieldValue<?> fv, String value) {
        if (fv == null) return;
        ((FieldValue<String>) fv).setValue(value);
        fv.setSource(FieldSource.QA);
        fv.setStatus(FieldStatus.CONFIRMED);
    }

    @SuppressWarnings("unchecked")
    private void setBooleanFieldValue(FieldValue<?> fv, String value) {
        if (fv == null) return;
        boolean boolVal = value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true")
                || value.equals("1");
        ((FieldValue<Boolean>) fv).setValue(boolVal);
        fv.setSource(FieldSource.QA);
        fv.setStatus(FieldStatus.CONFIRMED);
    }

    private void setFlag(ConditionalFlag flag, String value) {
        if (flag == null) return;
        boolean boolVal = value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true")
                || value.equals("1");
        flag.setTriggered(boolVal);
        if (flag.getRequired() == null) {
            flag.setRequired(new FieldValue<>());
        }
        flag.getRequired().setValue(boolVal);
        flag.getRequired().setSource(FieldSource.QA);
        flag.getRequired().setStatus(FieldStatus.CONFIRMED);
    }

    /** Writes into a possibly-null FieldValue&lt;String&gt; slot, instantiating it via the setter first if needed. */
    private void setStringFv(Supplier<FieldValue<String>> getter, Consumer<FieldValue<String>> setter, String value) {
        FieldValue<String> fv = getter.get();
        if (fv == null) {
            fv = new FieldValue<>();
            setter.accept(fv);
        }
        fv.setValue(value);
        fv.setSource(FieldSource.QA);
        fv.setStatus(FieldStatus.CONFIRMED);
    }

    /** Writes into a possibly-null FieldValue&lt;Integer&gt; slot, instantiating it via the setter first if needed. */
    private void setIntFv(Supplier<FieldValue<Integer>> getter, Consumer<FieldValue<Integer>> setter, String value) {
        Integer parsed = parseIntOrNull(value);
        if (parsed == null) return;
        FieldValue<Integer> fv = getter.get();
        if (fv == null) {
            fv = new FieldValue<>();
            setter.accept(fv);
        }
        fv.setValue(parsed);
        fv.setSource(FieldSource.QA);
        fv.setStatus(FieldStatus.CONFIRMED);
    }

    private void setIntSafe(String value, Consumer<Integer> setter) {
        Integer parsed = parseIntOrNull(value);
        if (parsed != null) setter.accept(parsed);
    }

    private Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> splitLines(String value) {
        return Arrays.stream(value.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> splitCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ── Stable ID resolution ─────────────────────────────────────────────────────

    /**
     * Resolves an ID for a bulk-edited row: keeps the provided ID if present (existing row),
     * otherwise mints the next free "PREFIX-NN" not already in use. Callers must first seed
     * {@code used} with every non-blank ID supplied across all rows before resolving blanks,
     * so a later explicit ID never collides with an earlier auto-generated one.
     */
    private String resolveId(String provided, String prefix, Set<String> used) {
        if (provided != null && !provided.isBlank()) return provided;
        int n = 1;
        String candidate;
        do {
            candidate = String.format("%s-%02d", prefix, n++);
        } while (used.contains(candidate));
        used.add(candidate);
        return candidate;
    }

    private Set<String> collectProvidedIds(List<String[]> rows, int idColumn) {
        Set<String> used = new HashSet<>();
        for (String[] row : rows) {
            if (row.length > idColumn && row[idColumn] != null && !row[idColumn].isBlank()) {
                used.add(row[idColumn].trim());
            }
        }
        return used;
    }

    private FieldValue<String> fv(String value) {
        return FieldValue.<String>builder()
                .value(value)
                .source(FieldSource.QA)
                .status(FieldStatus.CONFIRMED)
                .build();
    }

    // ── Actors ────────────────────────────────────────────────────────────────────

    public List<PcsfActor> parseActors(String answer) {
        List<String[]> rows = splitRows(answer, 4);
        Set<String> used = collectProvidedIds(rows, 0);
        return rows.stream()
                .map(parts -> {
                    if (parts.length < 4) return null;
                    String typeVal = parts[2].trim().toUpperCase().contains("EXTERNAL")
                            ? "EXTERNAL" : "INTERNAL";
                    return PcsfActor.builder()
                            .id(resolveId(parts[0].trim(), "ACT", used))
                            .name(fv(parts[1].trim()))
                            .type(fv(typeVal))
                            .description(fv(parts[3].trim()))
                            .build();
                })
                .filter(a -> a != null)
                .collect(Collectors.toList());
    }

    /** Preserves springSecurityRole from the existing actor with the same (resolved) ID. */
    private List<PcsfActor> mergeActors(List<PcsfActor> existing, List<PcsfActor> edited) {
        for (PcsfActor actor : edited) {
            existing.stream().filter(a -> a.getId().equals(actor.getId())).findFirst()
                    .ifPresent(prev -> actor.setSpringSecurityRole(prev.getSpringSecurityRole()));
        }
        return edited;
    }

    // ── Modules ───────────────────────────────────────────────────────────────────

    public List<PcsfModule> parseModules(String answer) {
        List<String[]> rows = splitRows(answer, 3);
        Set<String> used = collectProvidedIds(rows, 0);
        return rows.stream()
                .map(parts -> {
                    if (parts.length < 3) return null;
                    return PcsfModule.builder()
                            .id(resolveId(parts[0].trim(), "MOD", used))
                            .name(fv(parts[1].trim()))
                            .description(fv(parts[2].trim()))
                            .build();
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    /** Preserves crudOperations/useCases from the existing module with the same (resolved) ID. */
    private List<PcsfModule> mergeModules(List<PcsfModule> existing, List<PcsfModule> edited) {
        for (PcsfModule mod : edited) {
            PcsfModule prev = existing.stream().filter(m -> m.getId().equals(mod.getId())).findFirst().orElse(null);
            if (prev != null) {
                mod.setCrudOperations(prev.getCrudOperations());
                mod.setUseCases(prev.getUseCases());
                mod.setJavaPackageName(prev.getJavaPackageName());
            } else {
                mod.setCrudOperations(FieldValue.<List<String>>builder()
                        .value(new ArrayList<>(List.of("CREATE", "READ", "UPDATE", "DELETE")))
                        .source(FieldSource.DEFAULT)
                        .status(FieldStatus.CONFIRMED)
                        .build());
                mod.setUseCases(new ArrayList<>());
            }
        }
        return edited;
    }

    // ── Entities ──────────────────────────────────────────────────────────────────

    public List<PcsfEntity> parseEntities(String answer) {
        List<String[]> rows = splitRows(answer, 6);
        Set<String> used = collectProvidedIds(rows, 0);
        return rows.stream()
                .map(parts -> {
                    if (parts.length < 6) return null;
                    return PcsfEntity.builder()
                            .id(resolveId(parts[0].trim(), "ENT", used))
                            .name(fv(parts[1].trim()))
                            .tableName(fv(parts[2].trim()))
                            .primaryKeyStrategy(parts[3].trim().isEmpty() ? "UUID" : parts[3].trim())
                            .auditFields(isTruthy(parts[4]))
                            .softDelete(FieldValue.<Boolean>builder()
                                    .value(isTruthy(parts[5])).source(FieldSource.QA).status(FieldStatus.CONFIRMED).build())
                            .build();
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());
    }

    /** Preserves attributes/primaryModuleId from the existing entity with the same (resolved) ID. */
    private List<PcsfEntity> mergeEntities(List<PcsfEntity> existing, List<PcsfEntity> edited) {
        for (PcsfEntity ent : edited) {
            PcsfEntity prev = existing.stream().filter(e -> e.getId().equals(ent.getId())).findFirst().orElse(null);
            if (prev != null) {
                ent.setAttributes(prev.getAttributes());
                ent.setPrimaryModuleId(prev.getPrimaryModuleId());
            } else {
                ent.setAttributes(new ArrayList<>());
            }
        }
        return edited;
    }

    // ── Relationships ─────────────────────────────────────────────────────────────

    public List<PcsfRelationship> parseRelationships(String answer) {
        List<String[]> rows = splitRows(answer, 5);
        Set<String> used = collectProvidedIds(rows, 0);
        return rows.stream()
                .map(parts -> {
                    if (parts.length < 5) return null;
                    return PcsfRelationship.builder()
                            .id(resolveId(parts[0].trim(), "REL", used))
                            .fromEntityId(parts[1].trim())
                            .toEntityId(parts[2].trim())
                            .cardinality(fv(parts[3].trim()))
                            .label(fv(parts[4].trim()))
                            .build();
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    /** Preserves optionality/owningEntityId/join column-table names from the existing relationship with the same (resolved) ID. */
    private List<PcsfRelationship> mergeRelationships(List<PcsfRelationship> existing, List<PcsfRelationship> edited) {
        for (PcsfRelationship rel : edited) {
            existing.stream().filter(r -> r.getId().equals(rel.getId())).findFirst().ifPresent(prev -> {
                rel.setOptionality(prev.getOptionality());
                rel.setOwningEntityId(prev.getOwningEntityId());
                rel.setJoinColumnName(prev.getJoinColumnName());
                rel.setJoinTableName(prev.getJoinTableName());
            });
        }
        return edited;
    }

    // ── Access control rules ─────────────────────────────────────────────────────

    public List<PcsfAccessControlRule> parseAccessControlRules(String answer) {
        List<String[]> rows = splitRows(answer, 5);
        Set<String> used = collectProvidedIds(rows, 0);
        return rows.stream()
                .map(parts -> {
                    if (parts.length < 5) return null;
                    return PcsfAccessControlRule.builder()
                            .id(resolveId(parts[0].trim(), "ACL", used))
                            .moduleId(parts[1].trim())
                            .entityId(parts[2].trim())
                            .operation(parts[3].trim())
                            .allowedRoles(FieldValue.<List<String>>builder()
                                    .value(splitCsv(parts[4]))
                                    .source(FieldSource.QA)
                                    .status(FieldStatus.CONFIRMED)
                                    .build())
                            .build();
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    // ── Error codes ───────────────────────────────────────────────────────────────

    public List<PcsfErrorCode> parseErrorCodes(String answer) {
        List<String[]> rows = splitRows(answer, 6);
        Set<String> used = collectProvidedIds(rows, 0);
        return rows.stream()
                .map(parts -> {
                    if (parts.length < 6) return null;
                    PcsfErrorCode ec = new PcsfErrorCode();
                    ec.setId(resolveId(parts[0].trim(), "ERR", used));
                    ec.setCode(parts[1].trim());
                    ec.setHttpStatus(parseIntOrNull(parts[2]) != null ? parseIntOrNull(parts[2]) : 400);
                    ec.setMessageTemplate(parts[3].trim());
                    ec.setExceptionClass(parts[4].trim());
                    ec.setModuleId(parts[5].trim());
                    return ec;
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());
    }

    // ── User interface: screens / navigation ─────────────────────────────────────

    public List<PcsfScreen> parseScreens(String answer) {
        return splitRows(answer, 6).stream()
                .map(parts -> {
                    if (parts.length < 6) return null;
                    return PcsfScreen.builder()
                            .name(parts[0].trim())
                            .type(parts[1].trim())
                            .entityId(parts[2].trim())
                            .moduleId(parts[3].trim())
                            .routePath(parts[4].trim())
                            .requiredRoles(splitCsv(parts[5]))
                            .build();
                })
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    /** Preserves tableColumns/formFields from the existing screen with the same name. */
    private List<PcsfScreen> mergeScreens(List<PcsfScreen> existing, List<PcsfScreen> edited) {
        for (PcsfScreen screen : edited) {
            PcsfScreen prev = existing.stream().filter(s -> s.getName().equals(screen.getName())).findFirst().orElse(null);
            if (prev != null) {
                screen.setTableColumns(prev.getTableColumns());
                screen.setFormFields(prev.getFormFields());
            } else {
                screen.setTableColumns(new ArrayList<>());
                screen.setFormFields(new ArrayList<>());
            }
        }
        return edited;
    }

    public List<PcsfNavItem> parseNavItems(String answer) {
        return splitRows(answer, 5).stream()
                .map(parts -> {
                    if (parts.length < 5) return null;
                    return PcsfNavItem.builder()
                            .label(parts[0].trim())
                            .routePath(parts[1].trim())
                            .icon(parts[2].trim())
                            .visibleToRoles(splitCsv(parts[3]))
                            .moduleId(parts[4].trim())
                            .build();
                })
                .filter(n -> n != null)
                .collect(Collectors.toList());
    }

    // ── Shared row splitting ─────────────────────────────────────────────────────

    private boolean isTruthy(String value) {
        String v = value.trim();
        return v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("true") || v.equals("1");
    }

    private List<String[]> splitRows(String answer, int minColumns) {
        return Arrays.stream(answer.split("\n"))
                .map(String::trim)
                .filter(line -> line.contains("|"))
                .map(line -> line.split("\\|", -1))
                .filter(parts -> parts.length >= minColumns)
                .collect(Collectors.toList());
    }
}
