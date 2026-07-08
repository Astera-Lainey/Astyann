package afb.astyann.diagramgeneratorservice.service;

import afb.astyann.diagramgeneratorservice.domain.DiagramType;

import java.util.Map;

public final class DiagramPromptTemplates {

    private DiagramPromptTemplates() {}

    public static final String MODEL = "qwen2.5-coder:7b";

    public static final String SYSTEM_PROMPT = """
            You are a senior software architect generating PlantUML diagrams for enterprise applications.
            Return ONLY the PlantUML source code, starting with @startuml and ending with @enduml.
            Do not include markdown code fences, explanations, or any text outside the @startuml/@enduml block.
            """;

    private static final Map<DiagramType, String> HINTS = Map.ofEntries(
            Map.entry(DiagramType.USE_CASE, "Model actors and use cases with include/extend relationships based on the project's modules and use-cases."),
            Map.entry(DiagramType.BUSINESS_CLASS, "Model the domain/business entities and their relationships (no technical/persistence details)."),
            Map.entry(DiagramType.DESIGN_CLASS, "Model the technical class design: entities, attributes with types, and relationships suitable for a JPA implementation."),
            Map.entry(DiagramType.ACTIVITY, "Model the primary business process flow as an activity diagram with decision points."),
            Map.entry(DiagramType.BUSINESS_SEQUENCE, "Model a business-level sequence diagram for the primary use case, showing actor-to-system interactions."),
            Map.entry(DiagramType.DESIGN_SEQUENCE, "Model a technical sequence diagram showing controller/service/repository interactions for the primary use case."),
            Map.entry(DiagramType.COMPONENT, "Model the system's components/modules and their dependencies."),
            Map.entry(DiagramType.DEPLOYMENT, "Model the deployment topology: nodes, artifacts, and communication protocols."),
            Map.entry(DiagramType.PACKAGE, "Model the package/module structure and their dependencies."),
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
}
