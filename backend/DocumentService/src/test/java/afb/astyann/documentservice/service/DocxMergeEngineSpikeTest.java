package afb.astyann.documentservice.service;

import afb.astyann.documentservice.domain.DocumentType;
import afb.astyann.documentservice.service.schema.DocumentSchema;
import afb.astyann.documentservice.service.schema.DocumentSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates DocxMergeEngine against the REAL template files, especially the data-dictionary's
 * nested tables[]/columns[] case — the POI row/paragraph cloning here relies on a non-obvious
 * ordering (fill content before attaching it to the live document tree), so this guards against
 * silently regressing back to a merge that clones the right row/block counts but leaves their
 * content unfilled.
 */
class DocxMergeEngineSpikeTest {

    private final DocxMergeEngine engine = new DocxMergeEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dataDictionary_nestedTablesAndColumns_clonesCorrectly() throws Exception {
        DocumentSchema schema = DocumentSchemas.get(DocumentType.DATA_DICTIONARY);
        ObjectNode data = mapper.createObjectNode();
        ObjectNode project = data.putObject("project");
        project.put("name", "TestProject");
        project.put("organisationName", "Afriland First Bank");
        project.put("date", "2026-07-16");
        project.put("version", "1.0");
        data.put("introduction", "This is the introduction.");
        data.put("conclusion", "This is the conclusion.");
        data.put("tableCount", "2");

        ArrayNode tables = data.putArray("tables");
        ObjectNode t1 = mapper.createObjectNode();
        t1.put("tableName", "users");
        t1.put("description", "Stores user accounts");
        t1.put("primaryKey", "id");
        ArrayNode t1cols = t1.putArray("columns");
        addCol(t1cols, "id", "BIGINT", "-", "Yes", "Primary key", "1");
        addCol(t1cols, "email", "VARCHAR", "255", "Yes", "User email", "a@b.com");
        addCol(t1cols, "created_at", "DATETIME", "-", "Yes", "Creation timestamp", "2026-01-01");
        tables.add(t1);

        ObjectNode t2 = mapper.createObjectNode();
        t2.put("tableName", "orders");
        t2.put("description", "Stores customer orders");
        t2.put("primaryKey", "id");
        ArrayNode t2cols = t2.putArray("columns");
        addCol(t2cols, "id", "BIGINT", "-", "Yes", "Primary key", "1");
        addCol(t2cols, "user_id", "BIGINT", "-", "Yes", "FK to users", "1");
        tables.add(t2);

        ArrayNode constraints = data.putArray("constraints");
        ObjectNode c1 = mapper.createObjectNode();
        c1.put("tableName", "orders");
        c1.put("type", "FOREIGN KEY");
        c1.put("detail", "user_id references users(id)");
        constraints.add(c1);

        byte[] result;
        try (InputStream template = new ClassPathResource(schema.templateResource()).getInputStream()) {
            result = engine.merge(template, schema, data);
        }

        String text = extractText(result);
        assertFalse(text.contains("${"), "No unresolved placeholders should remain");
        assertTrue(text.contains("TestProject"));
        assertTrue(text.contains("users"), "First table name should appear");
        assertTrue(text.contains("orders"), "Second table name should appear");
        assertTrue(text.contains("email"), "Column from first table should appear");
        assertTrue(text.contains("user_id"), "Column from second table should appear");
        assertTrue(text.contains("FOREIGN KEY"), "Constraint row should appear");
    }

    @Test
    void srs_flatRepeatingGroups_cloneCorrectly() throws Exception {
        DocumentSchema schema = DocumentSchemas.get(DocumentType.SRS);
        ObjectNode data = mapper.createObjectNode();
        ObjectNode project = data.putObject("project");
        project.put("name", "TestProject");
        project.put("organisationName", "Afriland First Bank");
        project.put("date", "2026-07-16");
        project.put("version", "1.0");
        ObjectNode intro = data.putObject("introduction");
        intro.put("overview", "Overview text");
        intro.put("purpose", "Purpose text");
        intro.put("scope", "Scope text");
        data.put("generalDescription", "General description");
        data.put("designConstraints", "Constraints text");
        data.put("interfaceRequirements", "Interface reqs");
        ObjectNode appendices = data.putObject("appendices");
        appendices.put("acronyms", "N/A");
        appendices.put("definitions", "N/A");
        appendices.put("references", "N/A");
        ObjectNode nfa = data.putObject("nonFunctionalAttributes");
        for (String f : List.of("compatibility","dataIntegrity","portability","reliability","reusability","scalability","security")) {
            nfa.put(f, f + "-value");
        }
        ObjectNode perf = data.putObject("performanceRequirements");
        for (String f : List.of("dynamicRequirements","errorRate","memoryCapacity","responseTime","staticRequirements")) {
            perf.put(f, f + "-value");
        }
        ObjectNode sched = data.putObject("schedule");
        sched.put("duration", "6 months");
        sched.put("estimatedCost", "100000");

        ArrayNode reqs = data.putArray("req");
        addReq(reqs, "REQ-1", "Login", "User can log in", "High");
        addReq(reqs, "REQ-2", "Logout", "User can log out", "Medium");
        addReq(reqs, "REQ-3", "Reset Password", "User can reset password", "Low");

        ArrayNode milestones = data.putArray("milestone");
        ObjectNode m1 = mapper.createObjectNode();
        m1.put("phase", "Design"); m1.put("duration", "1 month"); m1.put("description", "Design phase");
        milestones.add(m1);

        byte[] result;
        try (InputStream template = new ClassPathResource(schema.templateResource()).getInputStream()) {
            result = engine.merge(template, schema, data);
        }

        String text = extractText(result);
        assertFalse(text.contains("${"), "No unresolved placeholders should remain");
        assertTrue(text.contains("REQ-1") && text.contains("REQ-2") && text.contains("REQ-3"),
                "All 3 requirement rows should be present");
        assertTrue(text.contains("Login") && text.contains("Logout") && text.contains("Reset Password"));
    }

    private void addCol(ArrayNode arr, String fieldName, String dataType, String fieldSize,
                        String required, String description, String example) {
        ObjectNode c = mapper.createObjectNode();
        c.put("fieldName", fieldName);
        c.put("dataType", dataType);
        c.put("fieldSize", fieldSize);
        c.put("required", required);
        c.put("description", description);
        c.put("example", example);
        arr.add(c);
    }

    private void addReq(ArrayNode arr, String id, String title, String description, String priority) {
        ObjectNode r = mapper.createObjectNode();
        r.put("id", id);
        r.put("title", title);
        r.put("description", description);
        r.put("priority", priority);
        arr.add(r);
    }

    private String extractText(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }
}
