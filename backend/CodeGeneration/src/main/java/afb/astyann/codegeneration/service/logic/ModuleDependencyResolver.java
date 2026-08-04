package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfBusinessRule;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfRelationship;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import afb.astyann.codegeneration.domain.projection.BackendProjection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which <em>other</em> modules a module has to collaborate with, from the PCSF's entity
 * relationship graph and its business rules.
 *
 * <p>Logic injection prompts one module at a time, and each prompt shows only that module's own
 * stub plus read-only sources of everything else. Nothing tells the model that a rule spanning two
 * modules involves a service it has never seen, so cross-module behaviour was either reinvented
 * inline or silently dropped. This class derives that missing picture deterministically — no AI
 * involved — so the prompt can state it.
 *
 * <p>Resolution is driven entirely by the PCSF's ids and relationship graph. No entity, module or
 * domain name carries any special meaning here.
 *
 * <p>Everything here degrades to an empty list rather than throwing: a PCSF with no relationships
 * or no business rules simply produces no extra prompt sections.
 */
@Component
public class ModuleDependencyResolver {

    /**
     * Another module this one is related to through its entity graph.
     *
     * @param entityClassName the related entity
     * @param moduleName      the PCSF name of the module that owns it
     * @param serviceName     that module's service interface, i.e. what could be injected
     * @param cardinality     the relationship's cardinality, verbatim from the PCSF
     * @param outgoing        whether this module's entity is the {@code from} side
     * @param owningSide      whether this module's entity owns the foreign key
     */
    public record Collaborator(String entityClassName, String moduleName, String serviceName,
                               String cardinality, boolean outgoing, boolean owningSide) {}

    /**
     * A business rule that reaches two modules at once — because its {@code moduleId} names one
     * module while its {@code affectedEntityId} belongs to another.
     *
     * @param description  the rule text
     * @param otherModule  the module on the far side of the rule
     * @param otherService that module's service interface
     */
    public record SharedRule(String description, String otherModule, String otherService) {}

    /**
     * Collaborators of {@code module}, derived from every relationship touching its entity.
     * Self-relationships and relationships to entities in the same module are excluded — those
     * need no second service.
     */
    public List<Collaborator> collaboratorsFor(Pcsf pcsf, BackendProjection projection, BackendModule module) {
        List<Collaborator> out = new ArrayList<>();
        if (pcsf == null || pcsf.getRelationships() == null || projection == null || module == null) return out;

        PcsfEntity own = entityByName(pcsf, module.getEntityClassName());
        if (own == null || own.getId() == null) return out;

        Map<String, PcsfEntity> entitiesById = entitiesById(pcsf);
        Map<String, String> moduleNameByEntity = moduleNameByEntityName(pcsf);

        for (PcsfRelationship rel : pcsf.getRelationships()) {
            if (rel == null) continue;
            boolean outgoing = own.getId().equals(rel.getFromEntityId());
            boolean incoming = own.getId().equals(rel.getToEntityId());
            if (!outgoing && !incoming) continue;

            String otherId = outgoing ? rel.getToEntityId() : rel.getFromEntityId();
            if (otherId == null || otherId.equals(own.getId())) continue;   // self-reference

            PcsfEntity other = entitiesById.get(otherId);
            if (other == null) continue;
            String otherEntityName = value(other.getName());
            if (otherEntityName == null) continue;

            BackendModule otherModule = moduleForEntity(projection, otherEntityName);
            // Same module (two entities, one service) needs no cross-service call.
            if (otherModule == null || otherModule.getServiceName() == null
                    || otherModule.getServiceName().equals(module.getServiceName())) continue;

            out.add(new Collaborator(
                    otherEntityName,
                    moduleNameByEntity.getOrDefault(otherEntityName, otherModule.getServiceName()),
                    otherModule.getServiceName(),
                    value(rel.getCardinality()),
                    outgoing,
                    own.getId().equals(rel.getOwningEntityId())));
        }
        return out;
    }

    /**
     * Rules that {@code module} implements jointly with another module.
     *
     * <p>{@code LogicInjectionService.filterBusinessRules} hands a rule to every module it matches
     * — by {@code moduleId} or by {@code affectedEntityId} — so a rule spanning two modules is
     * delivered to both, and each implements its half with no idea the other exists. Naming the
     * far side lets the two halves line up.
     */
    public List<SharedRule> sharedRulesFor(Pcsf pcsf, BackendProjection projection, BackendModule module) {
        List<SharedRule> out = new ArrayList<>();
        if (pcsf == null || pcsf.getBusinessRules() == null || projection == null || module == null) return out;

        Map<String, String> moduleNameByEntity = moduleNameByEntityName(pcsf);

        for (PcsfBusinessRule rule : pcsf.getBusinessRules()) {
            if (rule == null) continue;
            BackendModule byModuleId = moduleForPcsfModuleId(pcsf, projection, rule.getModuleId());
            BackendModule byEntity = rule.getAffectedEntityId() == null ? null
                    : moduleForEntityIgnoreCase(projection, rule.getAffectedEntityId());

            if (byModuleId == null || byEntity == null) continue;
            if (byModuleId.getServiceName() == null || byEntity.getServiceName() == null) continue;
            if (byModuleId.getServiceName().equals(byEntity.getServiceName())) continue;  // one module only

            BackendModule far;
            if (byModuleId.getServiceName().equals(module.getServiceName())) far = byEntity;
            else if (byEntity.getServiceName().equals(module.getServiceName())) far = byModuleId;
            else continue;   // rule does not involve this module at all

            String description = value(rule.getDescription());
            if (description == null || description.isBlank()) continue;
            out.add(new SharedRule(
                    description,
                    moduleNameByEntity.getOrDefault(far.getEntityClassName(), far.getServiceName()),
                    far.getServiceName()));
        }
        return out;
    }

    // ── lookups ────────────────────────────────────────────────────────────

    private Map<String, PcsfEntity> entitiesById(Pcsf pcsf) {
        Map<String, PcsfEntity> out = new LinkedHashMap<>();
        if (pcsf.getEntities() == null) return out;
        for (PcsfEntity e : pcsf.getEntities()) {
            if (e != null && e.getId() != null) out.put(e.getId(), e);
        }
        return out;
    }

    /** Entity class name → the PCSF name of the module that declares it as its primary entity. */
    private Map<String, String> moduleNameByEntityName(Pcsf pcsf) {
        Map<String, String> out = new LinkedHashMap<>();
        if (pcsf.getEntities() == null || pcsf.getModules() == null) return out;
        Map<String, String> moduleNameById = new LinkedHashMap<>();
        for (PcsfModule m : pcsf.getModules()) {
            if (m != null && m.getId() != null && value(m.getName()) != null) {
                moduleNameById.put(m.getId(), value(m.getName()));
            }
        }
        for (PcsfEntity e : pcsf.getEntities()) {
            if (e == null || value(e.getName()) == null) continue;
            String moduleName = moduleNameById.get(e.getPrimaryModuleId());
            if (moduleName != null) out.put(value(e.getName()), moduleName);
        }
        return out;
    }

    private PcsfEntity entityByName(Pcsf pcsf, String name) {
        if (pcsf.getEntities() == null || name == null) return null;
        return pcsf.getEntities().stream()
                .filter(e -> e != null && name.equals(value(e.getName())))
                .findFirst().orElse(null);
    }

    private BackendModule moduleForEntity(BackendProjection projection, String entityClassName) {
        if (projection.getModules() == null) return null;
        return projection.getModules().stream()
                .filter(m -> entityClassName.equals(m.getEntityClassName()))
                .findFirst().orElse(null);
    }

    /** Mirrors {@code filterBusinessRules}, which matches {@code affectedEntityId} case-insensitively. */
    private BackendModule moduleForEntityIgnoreCase(BackendProjection projection, String entityClassName) {
        if (projection.getModules() == null) return null;
        return projection.getModules().stream()
                .filter(m -> entityClassName.equalsIgnoreCase(m.getEntityClassName()))
                .findFirst().orElse(null);
    }

    /**
     * Resolves a PCSF module id to a generated module, via the same kebab-case name match
     * {@code LogicInjectionService.findMatchingPcsfModule} uses in the other direction.
     */
    private BackendModule moduleForPcsfModuleId(Pcsf pcsf, BackendProjection projection, String moduleId) {
        if (moduleId == null || pcsf.getModules() == null || projection.getModules() == null) return null;
        PcsfModule pcsfModule = pcsf.getModules().stream()
                .filter(m -> m != null && moduleId.equals(m.getId()))
                .findFirst().orElse(null);
        if (pcsfModule == null || value(pcsfModule.getName()) == null) return null;

        String kebab = value(pcsfModule.getName()).toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return projection.getModules().stream()
                .filter(m -> {
                    String mapping = m.getRequestMapping();
                    if (mapping == null) return false;
                    String tail = mapping.substring(mapping.lastIndexOf('/') + 1).toLowerCase();
                    return !tail.isEmpty() && kebab.contains(tail);
                })
                .findFirst().orElse(null);
    }

    private static String value(FieldValue<String> field) {
        return field == null ? null : field.getValue();
    }
}
