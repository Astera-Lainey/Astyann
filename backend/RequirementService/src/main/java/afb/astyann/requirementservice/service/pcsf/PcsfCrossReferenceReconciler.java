package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.domain.pcsf.FieldValue;
import afb.astyann.requirementservice.domain.pcsf.Pcsf;
import afb.astyann.requirementservice.domain.pcsf.PcsfApiEndpoint;
import afb.astyann.requirementservice.domain.pcsf.PcsfEntity;
import afb.astyann.requirementservice.domain.pcsf.PcsfModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repairs the id cross-references between PCSF sections after an inference pass.
 *
 * <p>The PCSF is assembled by several independent AI passes. Each later pass is asked to emit
 * foreign keys — {@code endpoints[].moduleId}, {@code responseEntityId} and so on — into sections
 * an earlier pass created. Nothing validates them, so a pass that guesses an id produces a PCSF
 * that parses cleanly and is internally dangling: every consumer downstream silently finds nothing
 * and falls back to a convention, which is how the generated controllers came to ignore the
 * declared API contract entirely.
 *
 * <p>What this class does is deliberately narrow. It never invents an endpoint, changes a path or
 * a verb, or alters anything the model actually decided — it only resolves the endpoint's stated
 * identity onto ids that exist:
 *
 * <ul>
 *   <li>a {@code moduleId} that already resolves is left alone;</li>
 *   <li>otherwise the module is inferred from the endpoint's own path, since
 *       {@code /api/v1/stock-movements/{id}} names its module far more reliably than an invented
 *       {@code module_2} does;</li>
 *   <li>an entity reference that resolves to no entity is cleared, so downstream code can
 *       distinguish "no entity declared" from "an entity that does not exist".</li>
 * </ul>
 *
 * <p>Anything that cannot be resolved is left unset rather than guessed. An endpoint with no
 * module is a real signal — usually that the PCSF declares endpoints for a module it never
 * defined — and hiding it behind a guess would put the wrong operations on the wrong controller.
 */
@Component
@Slf4j
public class PcsfCrossReferenceReconciler {

    /**
     * @return how many endpoint references were repaired or cleared, for logging
     */
    public int reconcileEndpoints(Pcsf pcsf) {
        if (pcsf == null || pcsf.getEndpoints() == null || pcsf.getEndpoints().isEmpty()) return 0;

        Set<String> moduleIds = pcsf.getModules() == null ? Set.of()
                : pcsf.getModules().stream().map(PcsfModule::getId).filter(id -> id != null)
                        .collect(Collectors.toSet());
        Set<String> entityIds = pcsf.getEntities() == null ? Set.of()
                : pcsf.getEntities().stream().map(PcsfEntity::getId).filter(id -> id != null)
                        .collect(Collectors.toSet());

        Map<String, String> moduleByKeyword = indexModulesByKeyword(pcsf);
        String versionPrefix = pcsf.getApiConfig() != null ? pcsf.getApiConfig().getVersionPrefix() : "/api/v1";

        int repaired = 0;
        for (PcsfApiEndpoint ep : pcsf.getEndpoints()) {
            if (ep == null) continue;

            if (ep.getModuleId() == null || !moduleIds.contains(ep.getModuleId())) {
                String resolved = moduleForPath(ep.getPath(), versionPrefix, moduleByKeyword);
                if (resolved != null) {
                    log.debug("Endpoint {} {} referenced module '{}' which does not exist — resolved to '{}' from its path",
                            ep.getHttpMethod(), ep.getPath(), ep.getModuleId(), resolved);
                } else {
                    log.debug("Endpoint {} {} references module '{}' which does not exist and no module matches its path — left unassigned",
                            ep.getHttpMethod(), ep.getPath(), ep.getModuleId());
                }
                ep.setModuleId(resolved);
                repaired++;
            }
            if (ep.getRequestBodyEntityId() != null && !entityIds.contains(ep.getRequestBodyEntityId())) {
                ep.setRequestBodyEntityId(null);
                repaired++;
            }
            if (ep.getResponseEntityId() != null && !entityIds.contains(ep.getResponseEntityId())) {
                ep.setResponseEntityId(null);
                repaired++;
            }
        }
        if (repaired > 0) {
            log.info("Reconciled {} dangling endpoint reference(s) across {} endpoint(s)",
                    repaired, pcsf.getEndpoints().size());
        }
        return repaired;
    }

    /**
     * Maps a normalised keyword to a module id, for every name a module can plausibly be addressed
     * by in a URL: the module's own name, and the name of each entity it owns.
     *
     * <p>Longer keywords are preferred at lookup time, so a module called "Stock" cannot claim a
     * path belonging to "Stock Movements".
     */
    private Map<String, String> indexModulesByKeyword(Pcsf pcsf) {
        Map<String, String> index = new LinkedHashMap<>();
        if (pcsf.getModules() == null) return index;

        for (PcsfModule m : pcsf.getModules()) {
            if (m == null || m.getId() == null) continue;
            addKeyword(index, value(m.getName()), m.getId());
        }
        if (pcsf.getEntities() != null) {
            for (PcsfEntity e : pcsf.getEntities()) {
                if (e == null || e.getPrimaryModuleId() == null) continue;
                addKeyword(index, value(e.getName()), e.getPrimaryModuleId());
            }
        }
        return index;
    }

    private void addKeyword(Map<String, String> index, String rawName, String moduleId) {
        String key = normalise(rawName);
        if (key.isEmpty()) return;
        index.putIfAbsent(key, moduleId);
        // "Product Management" should also answer to "product", because that is what the path says.
        String head = normalise(rawName.split("[\\s_\\-]+")[0]);
        if (!head.isEmpty()) index.putIfAbsent(head, moduleId);
    }

    /**
     * Infers the owning module from the endpoint's path — {@code /api/v1/stock-movements/{id}}
     * resolves through the {@code stock-movements} segment.
     */
    private String moduleForPath(String path, String versionPrefix, Map<String, String> moduleByKeyword) {
        if (path == null || path.isBlank() || moduleByKeyword.isEmpty()) return null;

        String remainder = path.trim();
        if (versionPrefix != null && !versionPrefix.isBlank() && remainder.startsWith(versionPrefix)) {
            remainder = remainder.substring(versionPrefix.length());
        }
        for (String segment : remainder.split("/")) {
            // A path variable identifies a row, never the module.
            if (segment.isBlank() || segment.startsWith("{")) continue;
            String key = normalise(segment);
            String direct = moduleByKeyword.get(key);
            if (direct != null) return direct;
            String singular = singularise(key);
            String viaSingular = moduleByKeyword.get(singular);
            if (viaSingular != null) return viaSingular;
            // Prefix match last: "usermanagement" is reached from "user", but only once the exact
            // and singular lookups have failed, so a closer match always wins.
            for (Map.Entry<String, String> candidate : moduleByKeyword.entrySet()) {
                if (candidate.getKey().startsWith(singular) || candidate.getKey().startsWith(key)) {
                    return candidate.getValue();
                }
            }
        }
        return null;
    }

    private String singularise(String word) {
        if (word.endsWith("ies") && word.length() > 3) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("ses") && word.length() > 3) return word.substring(0, word.length() - 2);
        if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 1) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /** Lowercase, letters and digits only — so "Stock Movements", "stock-movements" and
     *  "stock_movements" all collapse onto the same key. */
    private String normalise(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String value(FieldValue<String> fv) {
        return fv != null && fv.getValue() != null ? fv.getValue() : "";
    }

    /** Convenience for callers that hold a mutable list rather than a whole PCSF. */
    public List<PcsfApiEndpoint> withResolvedModules(Pcsf pcsf) {
        reconcileEndpoints(pcsf);
        return pcsf.getEndpoints() == null ? new ArrayList<>() : pcsf.getEndpoints();
    }
}