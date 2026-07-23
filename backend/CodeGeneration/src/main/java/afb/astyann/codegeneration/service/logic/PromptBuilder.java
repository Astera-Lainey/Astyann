package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
import afb.astyann.codegeneration.domain.pcsf.Pcsf;
import afb.astyann.codegeneration.domain.pcsf.PcsfBusinessRule;
import afb.astyann.codegeneration.domain.pcsf.PcsfEntity;
import afb.astyann.codegeneration.domain.pcsf.PcsfModule;
import afb.astyann.codegeneration.domain.pcsf.PcsfUseCase;
import afb.astyann.codegeneration.domain.projection.BackendModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the system + user prompts sent to the AI Orchestrator when implementing a whole
 * module. Kept as a bean so the templates stay in one place and can be unit-tested.
 */
@Component
@Slf4j
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a senior Java Spring Boot 3.3 backend engineer.

            You are given the current stub implementation of ONE module of a generated
            microservice, together with everything you need to fully implement it: the entity
            source(s), the module's use cases, business rules, and related documentation
            context retrieved from the project's SFD and diagrams.

            Your task: return the FULLY IMPLEMENTED module.

            Output format — return ONLY a single JSON object with these fields:
            {
              "serviceImpl": "<full source of the updated ServiceImpl.java>",
              "repository":  "<full source of the updated Repository.java, or null>",
              "controller":  "<full source of the updated Controller.java, or null>",
              "notes":       "<one-sentence summary of what was implemented>"
            }

            HARD RULES:
            1. Return ONLY the JSON. No prose before, no prose after, no markdown fences around it.
            2. JSON string values are raw Java source — no ``` fences, no line-continuation escapes.
            3. Every method that currently throws UnsupportedOperationException MUST be
               implemented — do not leave any TODO or stub behind.
            4. Enforce every listed business rule. For precondition violations throw
               IllegalStateException with a clear, user-facing message.
            5. Follow each use case's main scenario steps exactly, in order.
            6. Use ONLY the fields declared on the entity — do NOT invent new fields or columns.
            7. You MAY add new @Query methods to Repository if the standard JpaRepository
               methods are not sufficient. In that case, return the FULL updated repository
               source in the "repository" field.
            8. Keep the existing package declaration and existing imports; add any imports you
               need. Every symbol you use must be either already imported, added to the imports,
               or fully qualified.
            9. Do NOT change method signatures — return types, parameter types, parameter names
               and thrown exceptions must match the stub exactly.
            10. If the controller does NOT need to change (usual case), set "controller" to null.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(BackendModule module,
                             PcsfModule pcsfModule,
                             PcsfEntity primaryEntity,
                             List<PcsfBusinessRule> rules,
                             String serviceImplSource,
                             String repositorySource,
                             String controllerSource,
                             String entitySource,
                             List<String> repositoryMethodSignatures,
                             String ragContext) {

        StringBuilder sb = new StringBuilder();

        sb.append("## MODULE\n")
          .append("name: ").append(nullSafe(fv(pcsfModule == null ? null : pcsfModule.getName()))).append('\n')
          .append("primary entity: ").append(module.getEntityClassName()).append('\n')
          .append("request mapping: ").append(module.getRequestMapping()).append("\n\n");

        sb.append("## ENTITY SOURCE\n```java\n").append(entitySource).append("\n```\n\n");

        sb.append("## CURRENT STUB — ServiceImpl\n```java\n").append(serviceImplSource).append("\n```\n\n");

        sb.append("## CURRENT — Repository\n```java\n").append(repositorySource).append("\n```\n");
        if (!repositoryMethodSignatures.isEmpty()) {
            sb.append("Available repository methods (inherited from JpaRepository + custom):\n");
            for (String sig : repositoryMethodSignatures) sb.append("  - ").append(sig).append('\n');
            sb.append('\n');
        }

        sb.append("## CURRENT — Controller (for reference, usually needs no change)\n```java\n")
          .append(controllerSource).append("\n```\n\n");

        sb.append("## USE CASES\n");
        if (pcsfModule == null || pcsfModule.getUseCases() == null || pcsfModule.getUseCases().isEmpty()) {
            sb.append("(none listed — implement CRUD to the letter and enforce all business rules)\n");
        } else {
            for (PcsfUseCase uc : pcsfModule.getUseCases()) {
                sb.append("### ").append(nullSafe(fv(uc.getName()))).append('\n')
                  .append("Preconditions: ").append(nullSafe(fv(uc.getPreconditions()))).append('\n')
                  .append("Postconditions: ").append(nullSafe(fv(uc.getPostconditions()))).append('\n');
                sb.append("Main scenario:\n");
                List<String> steps = uc.getMainScenario() != null ? uc.getMainScenario().getValue() : null;
                if (steps != null) for (int i = 0; i < steps.size(); i++) sb.append("  ").append(i + 1).append(". ").append(steps.get(i)).append('\n');
                String alt = fv(uc.getAlternativeScenario());
                if (alt != null && !alt.isBlank()) sb.append("Alternative scenario: ").append(alt).append('\n');
                sb.append('\n');
            }
        }

        sb.append("## BUSINESS RULES\n");
        if (rules == null || rules.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (PcsfBusinessRule r : rules) {
                sb.append("- ").append(nullSafe(fv(r.getDescription())));
                String hint = fv(r.getImplementationHint());
                if (hint != null && !hint.isBlank()) sb.append("  (hint: ").append(hint).append(')');
                sb.append('\n');
            }
        }
        sb.append('\n');

        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("## RELATED DOCUMENTATION (retrieved from project knowledge base)\n")
              .append(truncate(ragContext, 8000)).append("\n\n");
        }

        sb.append("Return the JSON object now.");
        return sb.toString();
    }

    public String systemPromptForCompileFix() {
        return """
               You are a senior Java compiler-error fixer. The Java file below FAILS to compile
               against Spring Boot 3.3 / Java 21.

               Return ONLY the fully-fixed source of the file. No prose, no markdown fences, no
               explanation. Preserve the package, all existing imports (adding any that are
               needed), all public method signatures, and the class name.
               """;
    }

    public String userPromptForCompileFix(String currentSource, List<String> errorLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("## COMPILE ERRORS\n");
        for (String e : errorLines) sb.append("- ").append(e).append('\n');
        sb.append("\n## CURRENT FILE\n").append(currentSource);
        sb.append("\n\nReturn the corrected file source now.");
        return sb.toString();
    }

    private static String fv(FieldValue<?> f) {
        if (f == null || f.getValue() == null) return null;
        Object v = f.getValue();
        if (v instanceof List<?> l) return String.join(", ", l.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n... (truncated)";
    }
}
