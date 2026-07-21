package afb.astyann.documentservice.service;

import afb.astyann.documentservice.domain.DocumentType;
import afb.astyann.documentservice.service.schema.DocumentSchema;
import afb.astyann.documentservice.service.schema.DocumentSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DocumentPromptTemplates {

    private DocumentPromptTemplates() {}

    public static final String MODEL = "minimax-m3:cloud";

    public static final String SYSTEM_PROMPT = """
            You are a senior technical writer producing enterprise software documentation.
            Return ONLY valid JSON matching the schema given in the user prompt. No preamble,
            no markdown code fences, no explanation. Use the exact field names given.
            """;

    public static String ragQuery(DocumentType type) {
        return type.name().replace('_', ' ').toLowerCase() + " " + DocumentSchemas.get(type).promptHint();
    }

    public static String userPrompt(DocumentType type, String context, ObjectMapper mapper) {
        DocumentSchema schema = DocumentSchemas.get(type);
        ObjectNode example = buildExampleJson(schema, mapper);
        return "Document type: " + type + "\n" + schema.promptHint()
                + "\n\nProject context:\n" + context
                + "\n\nReturn ONLY valid JSON matching this exact schema — use these exact field names:\n"
                + example.toPrettyString()
                + "\n\nFor every repeating list, emit one real entry per relevant item found in the "
                + "project context (do not invent generic placeholder rows, and do not leave a list "
                + "empty if the context has data for it). No preamble. No code fences. No explanation.";
    }

    /**
     * Documents are schema-driven JSON merged into a .docx template — there's no reverse mapping
     * from a previously-merged .docx back into that JSON, so a true incremental edit (the way
     * diagrams feed back their flat PlantUML source) isn't feasible. This reuses the exact same
     * generation prompt and appends the stored change instructions as an explicit directive,
     * producing a full fresh regeneration guided by the feedback rather than a literal edit.
     */
    public static String userPromptWithFeedback(DocumentType type, String context, ObjectMapper mapper, String instructions) {
        return userPrompt(type, context, mapper)
                + "\n\nAdditionally, make sure the generated content incorporates this requested change: "
                + instructions;
    }

    static ObjectNode buildExampleJson(DocumentSchema schema, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        schema.scalars().forEach(f -> setDottedPath(root, f.path(), "<" + f.path() + ">"));
        schema.groups().forEach(g -> {
            ObjectNode item = mapper.createObjectNode();
            g.fields().forEach(f -> item.put(f, "<" + f + ">"));
            root.putArray(g.jsonKey()).add(item);
        });
        schema.nestedBlocks().forEach(nb -> {
            ArrayNode outerArr = root.has(nb.outerJsonKey())
                    ? (ArrayNode) root.get(nb.outerJsonKey())
                    : root.putArray(nb.outerJsonKey());
            ObjectNode outerItem = (ObjectNode) outerArr.get(0);
            ObjectNode innerItem = mapper.createObjectNode();
            nb.rowFields().forEach(f -> innerItem.put(f, "<" + f + ">"));
            outerItem.putArray(nb.innerJsonField()).add(innerItem);
        });
        // Vertical/paragraph blocks are just another array-of-objects from the AI's perspective —
        // how the merge engine physically lays them out in the .docx doesn't change the JSON shape.
        schema.verticalBlocks().forEach(g -> {
            ObjectNode item = mapper.createObjectNode();
            g.fields().forEach(f -> item.put(f, "<" + f + ">"));
            root.putArray(g.jsonKey()).add(item);
        });
        schema.paragraphBlocks().forEach(g -> {
            ObjectNode item = mapper.createObjectNode();
            g.fields().forEach(f -> item.put(f, "<" + f + ">"));
            root.putArray(g.jsonKey()).add(item);
        });
        return root;
    }

    private static void setDottedPath(ObjectNode root, String path, String value) {
        String[] parts = path.split("\\.");
        ObjectNode cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            cur = cur.has(parts[i]) && cur.get(parts[i]).isObject()
                    ? (ObjectNode) cur.get(parts[i])
                    : cur.putObject(parts[i]);
        }
        cur.put(parts[parts.length - 1], value);
    }
}
