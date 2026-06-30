package afb.astyann.requirementservice.service.pcsf;

import afb.astyann.requirementservice.domain.ClarificationQuestion;
import afb.astyann.requirementservice.domain.PcsfStatus;
import afb.astyann.requirementservice.domain.Requirement;
import afb.astyann.requirementservice.domain.QuestionType;
import afb.astyann.requirementservice.domain.pcsf.FieldValue;
import afb.astyann.requirementservice.domain.pcsf.Pcsf;
import afb.astyann.requirementservice.repository.ClarificationQuestionRepository;
import afb.astyann.requirementservice.repository.RequirementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionGeneratorService {

    private final ClarificationQuestionRepository questionRepository;
    private final RequirementRepository           requirementRepository;
    private final ObjectMapper                    objectMapper;

    @Transactional
    public void generateQuestions(Requirement requirement, Pcsf pcsf,
                                  List<String> missing, List<String> conditionalPending) {
        questionRepository.deleteByRequirement(requirement);

        String projectName = safeValue(pcsf.getProject().getName());
        String displayName = safeValue(pcsf.getProject().getDisplayName());
        if (displayName == null || displayName.isBlank()) displayName = projectName;

        List<ClarificationQuestion> questions = new ArrayList<>();

        if (conditionalPending.contains("9.6")) {
            questions.add(build(requirement, "9.6",
                    "conditionalFeatures.multiTenancy.required.value", 0,
                    "⚠️ Important: Should different Afriland branches or agencies see ONLY their own data, " +
                    "completely separated from other branches' data?",
                    QuestionType.YES_NO,
                    List.of("No — all branches share the same data", "Yes — each branch sees only its own data"),
                    null));
        }

        if (missing.stream().anyMatch(m -> m.equals("1.1"))) {
            questions.add(build(requirement, "1.1", "project.name.value", 1,
                    "What is the name of this project?",
                    QuestionType.TEXT, null, "e.g. Loan Management System"));
        }
        if (missing.stream().anyMatch(m -> m.equals("1.2"))) {
            questions.add(build(requirement, "1.2", "project.description.value", 1,
                    "In 2 to 4 sentences, what does " + projectName +
                    " do, and what business problem does it solve for Afriland First Bank?",
                    QuestionType.TEXTAREA, null,
                    "e.g. This system allows branch managers to track client loan applications " +
                    "from submission through approval to disbursement."));
        }
        if (missing.stream().anyMatch(m -> m.equals("7.1"))) {
            questions.add(build(requirement, "7.1", "project.displayName.value", 1,
                    "What name should appear in the application header and browser tab? " +
                    "(This can be the same as the project name or a shorter label.)",
                    QuestionType.TEXT, null, "e.g. Loan Tracker"));
        }

        boolean actorsMissing = missing.stream().anyMatch(m ->
                m.equals("2.1") || m.startsWith("2.1[") || m.startsWith("2.2[") || m.startsWith("2.3["));
        if (actorsMissing) {
            questions.add(build(requirement, "2.1", "actors", 2,
                    "Who are the different types of users of this application? " +
                    "For each type, write their name, whether they are an Afriland employee " +
                    "(Internal) or an external party (External), and what they do in the system. " +
                    "Write one user type per line in this format: " +
                    "Name | Internal or External | What they do",
                    QuestionType.TEXTAREA, null,
                    "Administrator | Internal | Manages users and system settings\n" +
                    "Branch Manager | Internal | Approves loan applications\n" +
                    "Client | External | Submits loan applications"));
        }

        boolean modulesMissing = missing.stream().anyMatch(m ->
                m.equals("3.1") || m.startsWith("3.1[") || m.startsWith("3.2[") || m.startsWith("3.3["));
        if (modulesMissing) {
            questions.add(build(requirement, "3.1", "modules", 3,
                    "What are the main functional areas of " + displayName + "? " +
                    "For each area, write its name and what users can do there. " +
                    "Write one area per line: Area Name | What users can do there",
                    QuestionType.TEXTAREA, null,
                    "Loan Management | Submit, review and approve loan applications\n" +
                    "Client Management | Register and manage client profiles\n" +
                    "Reporting | Generate monthly loan disbursement reports"));
        }

        if (conditionalPending.contains("3.8")) {
            questions.add(build(requirement, "3.8",
                    "conditionalFeatures.fileUpload.required.value", 5,
                    "Does " + displayName + " need to allow users to upload files or documents?",
                    QuestionType.YES_NO, List.of("No", "Yes"), null));
        }
        if (conditionalPending.contains("3.9")) {
            questions.add(build(requirement, "3.9",
                    "conditionalFeatures.dataExport.required.value", 5,
                    "Should " + displayName + " allow users to export data as PDF or Excel files?",
                    QuestionType.YES_NO, List.of("No", "Yes"), null));
        }
        if (conditionalPending.contains("3.10")) {
            questions.add(build(requirement, "3.10",
                    "conditionalFeatures.searchFilter.required.value", 5,
                    "Should users be able to search or filter data in " + displayName + "?",
                    QuestionType.YES_NO, List.of("No", "Yes"), null));
        }

        questionRepository.saveAll(questions);
        requirement.setPendingQuestionsCount(questions.size());
        requirement.setPcsfStatus(PcsfStatus.UNDER_REVIEW);
        requirementRepository.save(requirement);
        log.info("Generated {} clarification questions for requirement={}",
                questions.size(), requirement.getRequirementId());
    }

    private ClarificationQuestion build(Requirement requirement,
                                        String ref, String path, int priority,
                                        String question, QuestionType type,
                                        List<String> options, String placeholder) {
        String id = UUID.randomUUID().toString();
        String optionsJson = null;
        if (options != null) {
            try { optionsJson = new ObjectMapper().writeValueAsString(options); }
            catch (Exception ignored) {}
        }
        return ClarificationQuestion.builder()
                .id(id).requirement(requirement).inventoryRef(ref).targetPath(path)
                .priority(priority).question(question).type(type)
                .optionsJson(optionsJson).placeholder(placeholder)
                .answered(false).build();
    }

    private String safeValue(FieldValue<String> fv) {
        return (fv != null && fv.getValue() != null) ? fv.getValue() : "this project";
    }
}
