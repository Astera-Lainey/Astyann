package afb.astyann.diagramgeneratorservice.service;

import afb.astyann.diagramgeneratorservice.domain.DiagramType;

import java.util.Map;

public final class DiagramPromptTemplates {

    private DiagramPromptTemplates() {}

    public static final String MODEL = "minimax-m3:cloud";

    public static final String SYSTEM_PROMPT = """
            You are a senior software architect generating PlantUML diagrams for enterprise applications.
            Return ONLY the PlantUML source code, starting with @startuml and ending with @enduml.
            Do not include markdown code fences, explanations, or any text outside the @startuml/@enduml block.
            Use only real PlantUML keywords: package, namespace, folder, class, interface, abstract, enum, component,
            node, database, actor, usecase, participant, state, object, artifact. Never invent a keyword that does
            not exist in PlantUML (for example, there is no "module" keyword — use "package" or "component" instead).
            Every relationship line between two elements must include an explicit connector such as --, -->, ..>, or
            ..|> — never place two element names on the same line without a connector between them.
            """;

    private static final Map<DiagramType, String> HINTS = Map.ofEntries(
            Map.entry(DiagramType.USE_CASE, "Model actors and use cases with include/extend relationships based on the project's modules and use-cases."),
            Map.entry(DiagramType.BUSINESS_CLASS, "Model the domain/business entities and their relationships (no technical/persistence details)."),
            Map.entry(DiagramType.DESIGN_CLASS, "Model the technical class design: entities, attributes with types, and relationships suitable for a JPA implementation."),
            Map.entry(DiagramType.ACTIVITY, "Model the primary business process flow as an activity diagram with decision points."),
            Map.entry(DiagramType.BUSINESS_SEQUENCE, "Model a business-level sequence diagram for the primary use case, showing actor-to-system interactions where there is only one actor and one lifeline as system"),
            Map.entry(DiagramType.DESIGN_SEQUENCE, "Model a technical sequence diagram showing controller/service/repository interactions for the primary use case."),
            Map.entry(DiagramType.COMPONENT, "Model the system's components and their dependencies using PlantUML's `component \"Name\"` declarations or [Name] bracket syntax. Group related components with `package \"Name\" { ... }` if needed — do not use a \"module\" keyword, it does not exist in PlantUML."),
            Map.entry(DiagramType.DEPLOYMENT, "Model the deployment topology: nodes, artifacts, and communication protocols."),
            Map.entry(DiagramType.PACKAGE, "Model the package structure and their dependencies using `package \"Name\" { ... }` blocks — do not use a \"module\" keyword, it does not exist in PlantUML."),
            Map.entry(DiagramType.ENTITY_RELATIONSHIP, "Model the entity-relationship diagram using PlantUML's entity syntax, showing tables, columns, and cardinalities.")
    );

    public static String ragQuery(DiagramType type) {
        return type.name().replace('_', ' ').toLowerCase() + " " + HINTS.get(type);
    }

    public static String userPrompt(DiagramType type, String context) {
        return "Diagram type: " + type + "\n" + HINTS.get(type)
                + "\n\nProject context:\n" + context
                + "\n\nGenerate the PlantUML diagram now. Return only the PlantUML code.";
    }

    public static String fixPrompt(DiagramType type, String brokenSource, String rendererError) {
        return "The following PlantUML source for a " + type + " diagram failed to render with this error "
                + "from the PlantUML renderer:\n" + rendererError
                + "\n\nPlantUML source:\n" + brokenSource
                + "\n\nFix the syntax error and return ONLY the corrected, complete PlantUML source (starting "
                + "with @startuml and ending with @enduml). Do not change the diagram's content or intent — "
                + "only fix the syntax.";
    }

    public static String changeRequestPrompt(DiagramType type, String currentSource, String context, String instructions) {
        return "You are revising an existing PlantUML " + type + " diagram based on requested changes.\n\n"
                + "Current PlantUML source:\n" + currentSource
                + "\n\nRequested changes:\n" + instructions
                + "\n\nProject context (for reference, may include information relevant to the change):\n" + context
                + "\n\nApply the requested changes to the diagram while preserving everything else that is still "
                + "correct and relevant. Return ONLY the complete, corrected PlantUML source (starting with "
                + "@startuml and ending with @enduml).";
    }
}
