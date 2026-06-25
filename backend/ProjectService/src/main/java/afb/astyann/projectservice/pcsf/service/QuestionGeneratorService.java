package afb.astyann.projectservice.pcsf.service;

import afb.astyann.projectservice.domain.ClarificationQuestion;
import afb.astyann.projectservice.domain.Project;
import afb.astyann.projectservice.domain.QuestionType;
import afb.astyann.projectservice.pcsf.model.FieldValue;
import afb.astyann.projectservice.pcsf.model.Pcsf;
import afb.astyann.projectservice.repository.ClarificationQuestionRepository;
import afb.astyann.projectservice.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionGeneratorService {

    private final ClarificationQuestionRepository questionRepository;
    private final ProjectRepository               projectRepository;
    private final ObjectMapper                    objectMapper;

    @Transactional
    public void generateQuestions(Project project, Pcsf pcsf,
                                  List<String> missing, List<String> conditionalPending) {
        questionRepository.deleteByProject(project);

        String projectName  = safeValue(pcsf.getProject().getName());
        String displayName  = safeValue(pcsf.getProject().getDisplayName());
        if (displayName == null || displayName.isBlank()) displayName = projectName;

        AtomicInteger qCounter = new AtomicInteger(1);
        List<ClarificationQuestion> questions = new ArrayList<>();

        // Multi-tenancy is priority 0 — always ask first if triggered
        if (conditionalPending.contains("9.6")) {
            questions.add(build(qCounter, project, "9.6",
                    "conditionalFeatures.multiTenancy.required.value", 0,
                    "⚠️ Important: Should different Afriland branches or agencies see ONLY their own data, " +
                    "completely separated from other branches' data?",
                    QuestionType.YES_NO,
                    List.of("No — all branches share the same data", "Yes — each branch sees only its own data"),
                    null));
        }

        // Mandatory — project identity
        if (missing.stream().anyMatch(m -> m.equals("1.1"))) {
            questions.add(build(qCounter, project, "1.1", "project.name.value", 1,
                    "What is the name of this project?",
                    QuestionType.TEXT, null, "e.g. Loan Management System"));
        }
        if (missing.stream().anyMatch(m -> m.equals("1.2"))) {
            questions.add(build(qCounter, project, "1.2", "project.description.value", 1,
                    "In 2 to 4 sentences, what does " + projectName +
                    " do, and what business problem does it solve for Afriland First Bank?",
                    QuestionType.TEXTAREA, null,
                    "e.g. This system allows branch managers to track client loan applications " +
                    "from submission through approval to disbursement."));
        }
        if (missing.stream().anyMatch(m -> m.equals("7.1"))) {
            questions.add(build(qCounter, project, "7.1", "project.displayName.value", 1,
                    "What name should appear in the application header and browser tab? " +
                    "(This can be the same as the project name or a shorter label.)",
                    QuestionType.TEXT, null, "e.g. Loan Tracker"));
        }

        // Mandatory — actors (asked once regardless of how many actors are missing)
        boolean actorsMissing = missing.stream().anyMatch(m ->
                m.equals("2.1") || m.startsWith("2.1[") || m.startsWith("2.2[") || m.startsWith("2.3["));
        if (actorsMissing) {
            questions.add(build(qCounter, project, "2.1", "actors", 2,
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

        // Mandatory — modules (asked once)
        boolean modulesMissing = missing.stream().anyMatch(m ->
                m.equals("3.1") || m.startsWith("3.1[") || m.startsWith("3.2[") || m.startsWith("3.3["));
        if (modulesMissing) {
            questions.add(build(qCounter, project, "3.1", "modules", 3,
                    "What are the main functional areas of " + displayName + "? " +
                    "For each area, write its name and what users can do there. " +
                    "Write one area per line: Area Name | What users can do there",
                    QuestionType.TEXTAREA, null,
                    "Loan Management | Submit, review and approve loan applications\n" +
                    "Client Management | Register and manage client profiles\n" +
                    "Reporting | Generate monthly loan disbursement reports"));
        }

        // Conditional features
        if (conditionalPending.contains("3.8") && !conditionalPending.contains("9.6") || conditionalPending.contains("3.8")) {
            questions.add(build(qCounter, project, "3.8",
                    "conditionalFeatures.fileUpload.required.value", 5,
                    "Does " + displayName + " need to allow users to upload files or documents?",
                    QuestionType.YES_NO, List.of("No", "Yes"), null));
        }
        if (conditionalPending.contains("3.9")) {
            questions.add(build(qCounter, project, "3.9",
                    "conditionalFeatures.dataExport.required.value", 5,
                    "Should " + displayName + " allow users to export data as PDF or Excel files?",
                    QuestionType.YES_NO, List.of("No", "Yes"), null));
        }
        if (conditionalPending.contains("3.10")) {
            questions.add(build(qCounter, project, "3.10",
                    "conditionalFeatures.searchFilter.required.value", 5,
                    "Should users be able to search or filter data in " + displayName + "?",
                    QuestionType.YES_NO, List.of("No", "Yes"), null));
        }

        questionRepository.saveAll(questions);
        project.setPendingQuestionsCount(questions.size());
        projectRepository.save(project);
        log.info("Generated {} clarification questions for project={}", questions.size(), project.getProjectId());
    }

    private ClarificationQuestion build(AtomicInteger counter, Project project,
                                        String ref, String path, int priority,
                                        String question, QuestionType type,
                                        List<String> options, String placeholder) {
        String id = String.format("Q-%02d", counter.getAndIncrement());
        String optionsJson = null;
        if (options != null) {
            try {
                optionsJson = new ObjectMapper().writeValueAsString(options);
            } catch (Exception ignored) {}
        }
        return ClarificationQuestion.builder()
                .id(id).project(project).inventoryRef(ref).targetPath(path)
                .priority(priority).question(question).type(type)
                .optionsJson(optionsJson).placeholder(placeholder)
                .answered(false).build();
    }

    private String safeValue(FieldValue<String> fv) {
        return (fv != null && fv.getValue() != null) ? fv.getValue() : "this project";
    }
}
