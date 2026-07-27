package afb.astyann.codegeneration.domain.logic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Structured JSON reply expected from the AI Orchestrator when the logic-injection pass sends
 * it a whole module. Missing fields ({@code null}) mean "no change to that file".
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiModuleResponse {

    /** Full source of the updated {@code *ServiceImpl.java}. */
    private String serviceImpl;

    /** Full source of the updated {@code *Repository.java}, if new @Query methods were added. */
    private String repository;

    /** Full source of the updated {@code *Controller.java}, only if new routes were needed. */
    private String controller;

    /**
     * New supporting files the AI needed to declare (enums, exceptions, helper classes).
     * Keys are paths RELATIVE to {@code src/main/java/{packagePath}/}, e.g.
     * {@code "enums/ProductStatus.java"} or {@code "exception/ProductNotFoundException.java"}.
     * Values are the full Java source of each file.
     */
    @Builder.Default
    private Map<String, String> additionalFiles = new HashMap<>();

    /** Short human-readable summary of what the AI implemented. Logged for debugging. */
    private String notes;
}
