package afb.astyann.codegeneration.service.logic;

import afb.astyann.codegeneration.domain.pcsf.FieldValue;
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
            microservice, together with everything you need to fully implement it: the source
            of every entity and repository already present in the project, the module's use
            cases, business rules, and related documentation context retrieved from the
            project's SFD and diagrams.

            Your task: return the FULLY IMPLEMENTED module.

            Output format — return ONLY a single JSON object with these fields:
            {
              "serviceImpl":     "<full source of the updated ServiceImpl.java>",
              "repository":      "<full source of the updated Repository.java, or null>",
              "controller":      "<full source of the updated Controller.java, or null>",
              "additionalFiles": { "<path relative to src/main/java/{packagePath}/>": "<full Java source>" },
              "notes":           "<one-sentence summary of what was implemented>"
            }

            HARD RULES:
            1. Return ONLY the JSON. No prose before, no prose after, no markdown fences.
            2. JSON string values are raw Java source — no ``` fences, no line-continuation escapes.
            3. Every method that currently throws UnsupportedOperationException MUST be
               implemented — do not leave any TODO or stub behind.
            4. Enforce every listed business rule. For precondition violations throw
               IllegalStateException with a clear, user-facing message.
            5. Follow each use case's main scenario steps exactly, in order.
            6. Use ONLY the fields declared on each entity — do NOT invent new fields on any
               entity, and do NOT invent columns.
            7. STRICT TYPE RULE: Every type you reference must be either:
                 (a) a JDK type,
                 (b) a Spring / JPA / Lombok type from a standard dependency,
                 (c) one of the entity / DTO / repository classes shown in the context, OR
                 (d) declared by YOU in "additionalFiles".
               If you need an enum, a custom exception, or a helper class that does not exist
               yet, you MUST add it to "additionalFiles" with the full source. Do NOT reference
               a type you have not either seen or declared.
            8. Additional files must live under a reasonable sub-package. Suggested locations:
                 - enums:      "enums/<Name>.java"
                 - exceptions: "exception/<Name>.java"
                 - helpers:    "service/impl/<Name>.java"
               The path is relative to src/main/java/{packagePath}/ — do not include the
               leading package directories.
            9. You MAY add new @Query methods to Repository if the standard JpaRepository
               methods are not sufficient. In that case, return the FULL updated repository
               source in the "repository" field.
           10. Keep the existing package declaration and existing imports; add any imports you
               need. Every symbol you use must be either already imported, added to the imports,
               or fully qualified.
           11. Do NOT change method signatures — return types, parameter types, parameter names
               and thrown exceptions must match the stub exactly.
           12. If a file does not need to change, set its field to null. If you have no
               additional files, set "additionalFiles" to an empty object {} or null.
            """;

    private static final String TEST_INSTRUCTIONS = """

            ADDITIONALLY, return a "testSource" field containing a JUnit 5 test class that proves
            the business rules above are enforced.

            TEST RULES:
            a. Package: {packageName}.service.impl — class name <ServiceImplName>BusinessRulesTest.
            b. Plain Mockito unit test — NO Spring context. Annotate with
               @ExtendWith(MockitoExtension.class); use @Mock for every repository the service
               constructor takes and @InjectMocks for the service implementation.
            c. One @Test per business rule. Assert the RULE, not the plumbing: for a precondition
               violation use assertThrows(...) and check the message; for a happy path verify the
               saved entity's state.
            d. Stub only what the method under test actually calls (Mockito is strict about
               unnecessary stubbing — an unused when(...) fails the test).
            e. Use ONLY types shown in the context. Do not invent DTO fields or entity getters.
            f. Every test must compile and pass against the serviceImpl you are returning in this
               same response.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** System prompt variant that also asks for a business-rule test class. */
    public String systemPromptWithTests() {
        return SYSTEM_PROMPT + TEST_INSTRUCTIONS;
    }

    public String userPrompt(BackendModule module,
                             PcsfModule pcsfModule,
                             PcsfEntity primaryEntity,
                             List<PcsfBusinessRule> rules,
                             String serviceImplSource,
                             String repositorySource,
                             String controllerSource,
                             String primaryEntitySource,
                             List<String> repositoryMethodSignatures,
                             java.util.Map<String, String> allEntitySources,
                             java.util.Map<String, String> allRepositorySources,
                             java.util.List<String> allDtoClassNames,
                             String ragContext) {
        return userPrompt(module, pcsfModule, primaryEntity, rules, serviceImplSource,
                repositorySource, controllerSource, primaryEntitySource, repositoryMethodSignatures,
                allEntitySources, allRepositorySources, allDtoClassNames, ragContext,
                java.util.Map.of(), List.of(), List.of());
    }

    /**
     * Full prompt, including the cross-module context a single-module view cannot supply.
     *
     * @param otherServiceApis  other modules' service interfaces as name → method signatures.
     *                          Signatures only: the bodies are irrelevant and would dominate the
     *                          prompt. Without these the model cannot know a collaborator exists,
     *                          so it reimplements the behaviour inline or drops it.
     * @param collaborators     which of those services this module is actually related to, and how
     * @param sharedRules       business rules another module is implementing the other half of
     */
    public String userPrompt(BackendModule module,
                             PcsfModule pcsfModule,
                             PcsfEntity primaryEntity,
                             List<PcsfBusinessRule> rules,
                             String serviceImplSource,
                             String repositorySource,
                             String controllerSource,
                             String primaryEntitySource,
                             List<String> repositoryMethodSignatures,
                             java.util.Map<String, String> allEntitySources,
                             java.util.Map<String, String> allRepositorySources,
                             java.util.List<String> allDtoClassNames,
                             String ragContext,
                             java.util.Map<String, List<String>> otherServiceApis,
                             List<ModuleDependencyResolver.Collaborator> collaborators,
                             List<ModuleDependencyResolver.SharedRule> sharedRules) {

        StringBuilder sb = new StringBuilder();

        sb.append("## MODULE\n")
          .append("name: ").append(nullSafe(fv(pcsfModule == null ? null : pcsfModule.getName()))).append('\n')
          .append("primary entity: ").append(module.getEntityClassName()).append('\n')
          .append("request mapping: ").append(module.getRequestMapping()).append("\n\n");

        // ── Full catalog of already-declared types the AI is allowed to reference ──
        sb.append("## AVAILABLE TYPES (already declared — you may reference these freely)\n");
        sb.append("Entities: ").append(String.join(", ", allEntitySources.keySet())).append('\n');
        sb.append("Repositories: ").append(String.join(", ", allRepositorySources.keySet())).append('\n');
        sb.append("DTOs: ").append(String.join(", ", allDtoClassNames)).append("\n\n");

        sb.append("## PRIMARY ENTITY SOURCE\n```java\n").append(primaryEntitySource).append("\n```\n\n");

        if (allEntitySources.size() > 1) {
            sb.append("## OTHER ENTITIES IN THE PROJECT\n");
            for (var entry : allEntitySources.entrySet()) {
                if (entry.getKey().equals(module.getEntityClassName())) continue;
                sb.append("### ").append(entry.getKey()).append("\n```java\n")
                  .append(truncate(entry.getValue(), 2500)).append("\n```\n");
            }
            sb.append('\n');
        }

        sb.append("## CURRENT STUB — ServiceImpl\n```java\n").append(serviceImplSource).append("\n```\n\n");

        sb.append("## CURRENT — Repository (module primary)\n```java\n").append(repositorySource).append("\n```\n");
        if (!repositoryMethodSignatures.isEmpty()) {
            sb.append("Available repository methods (inherited from JpaRepository + custom):\n");
            for (String sig : repositoryMethodSignatures) sb.append("  - ").append(sig).append('\n');
            sb.append('\n');
        }

        if (allRepositorySources.size() > 1) {
            sb.append("## OTHER REPOSITORIES YOU MAY INJECT\n");
            for (var entry : allRepositorySources.entrySet()) {
                if (entry.getKey().equals(module.getEntityClassName() + "Repository")) continue;
                sb.append("### ").append(entry.getKey()).append("\n```java\n")
                  .append(truncate(entry.getValue(), 1200)).append("\n```\n");
            }
            sb.append('\n');
        }

        appendCrossModuleContext(sb, module, otherServiceApis, collaborators, sharedRules);

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

    /**
     * Emits the three cross-module sections. Each is skipped entirely when empty, so a
     * single-module project's prompt is byte-for-byte what it was before.
     */
    private void appendCrossModuleContext(StringBuilder sb,
                                          BackendModule module,
                                          java.util.Map<String, List<String>> otherServiceApis,
                                          List<ModuleDependencyResolver.Collaborator> collaborators,
                                          List<ModuleDependencyResolver.SharedRule> sharedRules) {

        if (otherServiceApis != null && !otherServiceApis.isEmpty()) {
            sb.append("## OTHER MODULE SERVICES YOU MAY INJECT\n")
              .append("Constructor-inject any of these instead of reimplementing what they already do.\n")
              .append("Signatures only — call them, do not redefine them.\n");
            for (var entry : otherServiceApis.entrySet()) {
                if (entry.getKey().equals(module.getServiceName())) continue;
                sb.append("### ").append(entry.getKey()).append('\n');
                for (String sig : entry.getValue()) sb.append("  - ").append(sig).append('\n');
            }
            sb.append("\nCIRCULAR DEPENDENCY RULE: if injecting one of these would make two services\n")
              .append("depend on each other, inject that module's *Repository* instead. Spring rejects\n")
              .append("circular bean references and the application will fail to start.\n\n");
        }

        if (collaborators != null && !collaborators.isEmpty()) {
            sb.append("## MODULE DEPENDENCIES (from the project's entity relationships)\n");
            for (var c : collaborators) {
                sb.append("- ").append(module.getEntityClassName())
                  .append(c.outgoing() ? " -> " : " <- ").append(c.entityClassName());
                if (c.cardinality() != null && !c.cardinality().isBlank()) {
                    sb.append(" (").append(c.cardinality()).append(')');
                }
                sb.append(", owned by module \"").append(c.moduleName())
                  .append("\" via ").append(c.serviceName());
                if (c.owningSide()) sb.append(" — this module owns the foreign key");
                sb.append('\n');
            }
            sb.append('\n');
        }

        if (sharedRules != null && !sharedRules.isEmpty()) {
            sb.append("## RULES SHARED WITH OTHER MODULES\n")
              .append("These rules are being implemented on BOTH sides, in a separate request you\n")
              .append("cannot see. Implement only this module's half, and call the named service for\n")
              .append("the rest rather than duplicating it.\n");
            for (var r : sharedRules) {
                sb.append("- ").append(r.description())
                  .append("  (other side: module \"").append(r.otherModule())
                  .append("\", ").append(r.otherService()).append(")\n");
            }
            sb.append('\n');
        }
    }

    public String systemPromptForCompileFix() {
        return """
               You are a senior Java compiler-error fixer for Spring Boot 3.3 / Java 17.

               Return ONLY a JSON object (no prose, no markdown fences) with this shape:
               {
                 "fixedSource": "<full corrected Java source of the broken file>",
                 "additionalFiles": {
                   "<path relative to src/main/java/>": "<full Java source of a NEW type>"
                 }
               }

               HARD RULES:
               1. If the error is "cannot find symbol" for a DTO / enum / exception you introduced
                  or that is referenced but missing, you MUST create it in "additionalFiles".
                  Example key: "com/example/app/dto/GoodsReceivedDto.java"
               2. Prefer plain classes (with getters/setters or Lombok @Data) over records unless
                  the surrounding code already uses records.
               3. Preserve the broken file's package, class name, and public method signatures
                  unless a signature change is required to fix the compile error AND you also
                  update every caller via additionalFiles.
               4. Do NOT reference a type that is neither a JDK/Spring type nor present in
                  fixedSource / additionalFiles.
               5. If no new files are needed, set "additionalFiles" to {} or omit it.
               """;
    }

    public String userPromptForCompileFix(String currentSource, List<String> errorLines,
                                          String packagePathHint) {
        return userPromptForCompileFix(currentSource, errorLines, packagePathHint, java.util.Map.of());
    }

    /**
     * @param relatedSources read-only context keyed by a descriptive label — typically the
     *                       interface the file must satisfy and any other files failing in the same
     *                       round. Supplying these is what allows a signature mismatch to be fixed
     *                       coherently instead of ping-ponging between two files.
     */
    public String userPromptForCompileFix(String currentSource, List<String> errorLines,
                                          String packagePathHint,
                                          java.util.Map<String, String> relatedSources) {
        StringBuilder sb = new StringBuilder();
        sb.append("## COMPILE ERRORS\n");
        for (String e : errorLines) sb.append("- ").append(e).append('\n');
        if (packagePathHint != null && !packagePathHint.isBlank()) {
            sb.append("\n## PACKAGE PATH (use this prefix for additionalFiles keys)\n")
              .append(packagePathHint.replace('.', '/'))
              .append("/dto/YourType.java\n");
        }

        if (relatedSources != null && !relatedSources.isEmpty()) {
            sb.append("\n## RELATED FILES (read-only context — you are NOT editing these)\n")
              .append("Your fix must remain compatible with them. If the only way to satisfy the\n")
              .append("errors is to change one of these files instead, say so in \"notes\" and\n")
              .append("leave \"fixedSource\" unchanged rather than guessing.\n\n");
            for (var entry : relatedSources.entrySet()) {
                sb.append("### ").append(entry.getKey()).append("\n```java\n")
                  .append(truncate(entry.getValue(), 3000)).append("\n```\n");
            }
        }

        sb.append("\n## CURRENT FILE (this is the one to fix)\n").append(currentSource);
        sb.append("\n\nReturn the JSON fix now.");
        return sb.toString();
    }

    // ── Frontend (TypeScript / Angular) compile-fix prompts ──────────────────

    public String systemPromptForTsFix() {
        return """
               You are a senior Angular 21 / TypeScript engineer fixing type-check errors in a
               single generated source file (standalone components, signals, no NgModules).

               Return ONLY the full corrected source of the file — raw TypeScript, no prose, no
               markdown fences, no JSON wrapper.

               HARD RULES:
               1. Fix every listed error. Do not introduce new ones.
               2. Change ONLY what is needed to make the file type-check; preserve the component's
                  selector, class name, exported members and public API.
               3. Do not invent imports from packages that are not already used in the project.
               4. Keep the existing import style and formatting.
               5. Output the ENTIRE file, not a diff or a fragment.
               """;
    }

    /**
     * Angular template (.html) repair. Kept separate from {@link #systemPromptForTsFix()} because
     * template errors ({@code NG8001} and friends) report a {@code .html} location — asking for
     * "the corrected TypeScript file" while handing over HTML makes the model rewrite it as a
     * component class and destroy the template.
     */
    public String systemPromptForAngularTemplateFix() {
        return """
               You are a senior Angular 21 engineer fixing errors in a single component TEMPLATE
               file (.html). Templates use the modern control-flow syntax (@if / @for / @switch).

               Return ONLY the full corrected HTML template — no prose, no markdown fences, and no
               TypeScript. The output must remain an Angular template, not a component class.

               HARD RULES:
               1. Fix every listed error without introducing new bindings or elements.
               2. Preserve all existing structure, CSS classes, bindings and control-flow blocks
                  that are not implicated in an error.
               3. Do NOT rename or invent component inputs, outputs or template variables.
               4. If an element is unknown, prefer removing the offending attribute over deleting
                  the element, unless the error says the element itself is unknown.
               5. Output the ENTIRE template file, not a fragment or a diff.
               """;
    }

    public String userPromptForAngularTemplateFix(String currentSource, List<String> errorLines,
                                                  String relativePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("## TEMPLATE FILE\n").append(relativePath == null ? "(unknown)" : relativePath).append("\n\n");
        sb.append("## ERRORS\n");
        for (String e : errorLines) sb.append("- ").append(e).append('\n');
        sb.append("\n## CURRENT TEMPLATE\n").append(currentSource);
        sb.append("\n\nReturn the full corrected HTML template now.");
        return sb.toString();
    }

    public String userPromptForTsFix(String currentSource, List<String> errorLines,
                                     String relativePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("## FILE\n").append(relativePath == null ? "(unknown)" : relativePath).append("\n\n");
        sb.append("## TYPE-CHECK ERRORS\n");
        for (String e : errorLines) sb.append("- ").append(e).append('\n');
        sb.append("\n## CURRENT FILE\n").append(currentSource);
        sb.append("\n\nReturn the full corrected TypeScript file now.");
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
