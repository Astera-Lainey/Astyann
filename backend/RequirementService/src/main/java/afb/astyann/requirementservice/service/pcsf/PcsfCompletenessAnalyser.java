package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.pcsf.*;
import afb.astyann.requirementservice.domain.pcsf.enums.FieldStatus;
import afb.astyann.requirementservice.repository.RequirementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PcsfCompletenessAnalyser {

    private final RequirementRepository    requirementRepository;
    private final ObjectMapper             objectMapper;
    private final QuestionGeneratorService questionGenerator;
    private final AiInferencePcsfService   inferenceService;

    public PcsfCompletenessAnalyser(
            RequirementRepository requirementRepository,
            ObjectMapper objectMapper,
            @Lazy QuestionGeneratorService questionGenerator,
            @Lazy AiInferencePcsfService inferenceService) {
        this.requirementRepository = requirementRepository;
        this.objectMapper          = objectMapper;
        this.questionGenerator     = questionGenerator;
        this.inferenceService      = inferenceService;
    }

    public record ValidationResult(boolean gate1Passed, List<String> missing) {}

    @Transactional
    public ValidationResult analyse(Requirement requirement) {
        if (requirement.getPcsfJson() == null)
            return new ValidationResult(false, List.of("PCSF_NOT_FOUND"));

        try {
            Pcsf pcsf = objectMapper.readValue(requirement.getPcsfJson(), Pcsf.class);

            List<String> missing            = new ArrayList<>();
            List<String> conditionalPending = new ArrayList<>();
            List<String> errors             = new ArrayList<>();

            checkMandatory(pcsf, missing);

            String allText = collectAllText(pcsf);
            scanConditionals(allText, pcsf, conditionalPending);

            checkCrossReferences(pcsf, errors);

            PcsfValidation validation = pcsf.getValidation() != null
                    ? pcsf.getValidation() : new PcsfValidation();
            validation.setMissingMandatoryItems(missing);
            validation.setPendingConditionalItems(conditionalPending);
            validation.setErrors(errors);
            validation.setCompletenessScore(computeScore(pcsf, missing));
            pcsf.setValidation(validation);

            requirement.setPcsfJson(objectMapper.writeValueAsString(pcsf));
            requirementRepository.save(requirement);

            boolean gate1Passed = missing.isEmpty() && conditionalPending.isEmpty();

            if (gate1Passed) {
                log.info("GATE-1 PASSED for requirement={} — triggering AI inference",
                        requirement.getRequirementId());
                inferenceService.runInferenceAsync(requirement.getRequirementId());
            } else {
                log.debug("GATE-1 not passed for requirement={}: {} missing, {} conditional pending",
                        requirement.getRequirementId(), missing.size(), conditionalPending.size());
                questionGenerator.generateQuestions(requirement, pcsf, missing, conditionalPending);
            }

            return new ValidationResult(gate1Passed, missing);

        } catch (Exception ex) {
            log.error("Completeness analysis failed for requirement={}", requirement.getRequirementId(), ex);
            return new ValidationResult(false, List.of("ANALYSIS_ERROR: " + ex.getMessage()));
        }
    }

    private void checkMandatory(Pcsf pcsf, List<String> missing) {
        if (pcsf.getProject() == null) { missing.add("1.1"); missing.add("1.2"); missing.add("7.1"); return; }
        if (isMissing(pcsf.getProject().getName()))        missing.add("1.1");
        if (isMissing(pcsf.getProject().getDescription())) missing.add("1.2");
        if (isMissing(pcsf.getProject().getDisplayName())) missing.add("7.1");

        if (pcsf.getActors() == null || pcsf.getActors().isEmpty()) {
            missing.add("2.1");
        } else {
            for (PcsfActor actor : pcsf.getActors()) {
                if (isMissing(actor.getName()))        missing.add("2.1[" + actor.getId() + "]");
                if (isMissing(actor.getType()))        missing.add("2.2[" + actor.getId() + "]");
                if (isMissing(actor.getDescription())) missing.add("2.3[" + actor.getId() + "]");
            }
        }

        if (pcsf.getModules() == null || pcsf.getModules().isEmpty()) {
            missing.add("3.1");
        } else {
            for (PcsfModule mod : pcsf.getModules()) {
                if (isMissing(mod.getName()))              missing.add("3.1[" + mod.getId() + "]");
                if (isMissing(mod.getDescription()))       missing.add("3.2[" + mod.getId() + "]");
                if (isListMissing(mod.getCrudOperations())) missing.add("3.3[" + mod.getId() + "]");
                if (mod.getUseCases() != null) {
                    for (PcsfUseCase uc : mod.getUseCases()) {
                        if (isMissing(uc.getPreconditions()))    missing.add("3.4[" + uc.getId() + "]");
                        if (isMissing(uc.getPostconditions()))   missing.add("3.5[" + uc.getId() + "]");
                        if (isListMissing(uc.getMainScenario())) missing.add("3.6[" + uc.getId() + "]");
                        // 3.7 alternativeScenario is optional — AI inference fills it when applicable
                    }
                }
            }
        }
    }

    private void scanConditionals(String text, Pcsf pcsf, List<String> pending) {
        String t = text.toLowerCase();
        PcsfConditionalFeatures cf = pcsf.getConditionalFeatures();
        if (cf == null) return;

        checkKeywords(t, new String[]{"upload","attach","fichier","file","image","photo","scan","pièce jointe"},
                cf.getFileUpload(), "3.8", pending);
        checkKeywords(t, new String[]{"export","pdf","excel","rapport","report","download","extract","extraction"},
                cf.getDataExport(), "3.9", pending);
        checkKeywords(t, new String[]{"search","filter","recherche","filtrer","find","lookup","query"},
                cf.getSearchFilter(), "3.10", pending);
        checkKeywords(t, new String[]{"branch","agence","agency","filiale","tenant","separate data","division"},
                cf.getMultiTenancy(), "9.6", pending);
    }

    private void checkKeywords(String text, String[] keywords, ConditionalFlag flag,
                                String ref, List<String> pending) {
        if (flag == null) return;
        boolean triggered = false;
        for (String kw : keywords) {
            if (text.contains(kw)) { triggered = true; break; }
        }
        if (triggered && (flag.getRequired() == null ||
                flag.getRequired().getStatus() != FieldStatus.CONFIRMED)) {
            flag.setTriggered(true);
            pending.add(ref);
        }
    }

    private void checkCrossReferences(Pcsf pcsf, List<String> errors) {
        if (pcsf.getActors() == null || pcsf.getModules() == null) return;
        var actorIds = pcsf.getActors().stream().map(PcsfActor::getId).toList();
        pcsf.getModules().stream()
                .filter(m -> m.getUseCases() != null)
                .flatMap(m -> m.getUseCases().stream())
                .filter(uc -> uc.getActorId() != null && !actorIds.contains(uc.getActorId()))
                .forEach(uc -> errors.add("UC " + uc.getId() + " references unknown actor " + uc.getActorId()));
    }

    private double computeScore(Pcsf pcsf, List<String> missing) {
        int total = 3;
        if (pcsf.getActors() != null) total += pcsf.getActors().size() * 3;
        if (pcsf.getModules() != null) {
            for (PcsfModule m : pcsf.getModules()) {
                total += 3;
                if (m.getUseCases() != null) total += m.getUseCases().size() * 4;
            }
        }
        int confirmed = total - (int) missing.stream().filter(ref -> !ref.contains("ANALYSIS_ERROR")).count();
        return total == 0 ? 0.0 : Math.max(0.0, (double) confirmed / total);
    }

    private String collectAllText(Pcsf pcsf) {
        StringBuilder sb = new StringBuilder();
        if (pcsf.getProject() != null) {
            appendFv(sb, pcsf.getProject().getName());
            appendFv(sb, pcsf.getProject().getDescription());
        }
        if (pcsf.getModules() != null) pcsf.getModules().forEach(m -> {
            appendFv(sb, m.getName());
            appendFv(sb, m.getDescription());
            if (m.getUseCases() != null) m.getUseCases().forEach(uc -> {
                appendFv(sb, uc.getPreconditions());
                appendFv(sb, uc.getPostconditions());
                appendFv(sb, uc.getAlternativeScenario());
            });
        });
        if (pcsf.getActors() != null) pcsf.getActors().forEach(a -> {
            appendFv(sb, a.getName());
            appendFv(sb, a.getDescription());
        });
        return sb.toString();
    }

    private void appendFv(StringBuilder sb, FieldValue<?> fv) {
        if (fv != null && fv.getValue() != null) sb.append(" ").append(fv.getValue());
    }

    private boolean isMissing(FieldValue<?> fv) {
        return fv == null || fv.getValue() == null || fv.getStatus() == FieldStatus.MISSING;
    }

    private boolean isListMissing(FieldValue<?> fv) {
        if (fv == null || fv.getValue() == null) return true;
        if (fv.getValue() instanceof List<?> list) return list.isEmpty();
        return false;
    }
}
