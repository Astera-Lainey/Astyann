package afb.astyann.documentservice.service;

import afb.astyann.documentservice.service.schema.DocumentSchema;
import afb.astyann.documentservice.service.schema.NestedGroupBlock;
import afb.astyann.documentservice.service.schema.ParagraphBlock;
import afb.astyann.documentservice.service.schema.RepeatingGroup;
import afb.astyann.documentservice.service.schema.ScalarField;
import afb.astyann.documentservice.service.schema.VerticalBlock;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fills a ${dotted.path}-placeholder .docx template directly against POI's XWPFDocument object
 * model — no templating library involved, since none of the 8 templates carry any loop/repeat
 * markup (no Word comments, no content controls) for the engine to key off of.
 */
@Service
@Slf4j
public class DocxMergeEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([\\w.]+)}");

    public byte[] merge(InputStream template, DocumentSchema schema, JsonNode data) throws IOException {
        try (XWPFDocument document = new XWPFDocument(template)) {
            fillScalars(document, schema, data);
            for (RepeatingGroup group : schema.groups()) {
                mergeFlatGroup(document, group, data.path(group.jsonKey()));
            }
            for (NestedGroupBlock block : schema.nestedBlocks()) {
                mergeNestedBlock(document, block, data.path(block.outerJsonKey()));
            }
            for (VerticalBlock block : schema.verticalBlocks()) {
                mergeVerticalBlock(document, block, data.path(block.jsonKey()));
            }
            for (ParagraphBlock block : schema.paragraphBlocks()) {
                mergeParagraphBlock(document, block, data.path(block.jsonKey()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    // ── Scalar fill ─────────────────────────────────────────────────────────

    /**
     * Only replaces tokens declared as scalar fields in the schema — group-prefixed tokens
     * (e.g. "tbl.tableName") are deliberately left untouched here so mergeFlatGroup/
     * mergeNestedBlock can still find and clone the rows/blocks that contain them afterward.
     */
    private void fillScalars(XWPFDocument document, DocumentSchema schema, JsonNode data) {
        Set<String> scalarPaths = schema.scalars().stream().map(ScalarField::path).collect(Collectors.toSet());
        Function<String, String> resolver = token -> scalarPaths.contains(token)
                ? data.at("/" + token.replace('.', '/')).asText("")
                : null;
        allParagraphs(document).forEach(p -> mergeParagraphPlaceholders(p, resolver));
    }

    private List<XWPFParagraph> allParagraphs(XWPFDocument document) {
        List<XWPFParagraph> out = new ArrayList<>(document.getParagraphs());
        for (XWPFTable t : document.getTables()) collectTableParagraphs(t, out);
        return out;
    }

    private void collectTableParagraphs(XWPFTable table, List<XWPFParagraph> out) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                out.addAll(cell.getParagraphs());
                cell.getTables().forEach(nested -> collectTableParagraphs(nested, out));
            }
        }
    }

    /**
     * Word frequently splits a single ${...} placeholder across multiple runs (spell-check
     * markup mid-token). This concatenates the paragraph's run text, locates full placeholder
     * spans across run boundaries, then rewrites the spanned runs back-to-front (so earlier
     * offsets stay valid while later ones mutate) — the first run keeps its own formatting and
     * absorbs the resolved value, any run(s) in between are blanked (not removed, to avoid
     * index corruption mid-iteration), and the last run keeps its trailing text.
     */
    void mergeParagraphPlaceholders(XWPFParagraph paragraph, Function<String, String> resolver) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) return;

        int[] starts = new int[runs.size()];
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            starts[i] = full.length();
            String t = runs.get(i).getText(0);
            full.append(t == null ? "" : t);
        }
        String fullText = full.toString();
        Matcher m = PLACEHOLDER.matcher(fullText);
        List<int[]> spans = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            tokens.add(m.group(1));
        }
        if (spans.isEmpty()) return;

        for (int i = spans.size() - 1; i >= 0; i--) {
            int start = spans.get(i)[0];
            int end = spans.get(i)[1];
            String replacement = resolver.apply(tokens.get(i));
            if (replacement == null) continue; // resolver declined this token — leave it untouched for a later pass

            int firstIdx = runIndexAt(starts, start);
            int lastIdx = runIndexAt(starts, end - 1);

            XWPFRun firstRun = runs.get(firstIdx);
            String firstText = firstRun.getText(0) == null ? "" : firstRun.getText(0);
            String prefix = firstText.substring(0, start - starts[firstIdx]);

            XWPFRun lastRun = runs.get(lastIdx);
            String lastText = lastRun.getText(0) == null ? "" : lastRun.getText(0);
            String suffix = lastText.substring(end - starts[lastIdx]);

            if (firstIdx == lastIdx) {
                firstRun.setText(prefix + replacement + suffix, 0);
            } else {
                firstRun.setText(prefix + replacement, 0);
                for (int r = firstIdx + 1; r < lastIdx; r++) {
                    runs.get(r).setText("", 0);
                }
                lastRun.setText(suffix, 0);
            }
        }
    }

    private int runIndexAt(int[] starts, int offset) {
        for (int i = starts.length - 1; i >= 0; i--) {
            if (starts[i] <= offset) return i;
        }
        return 0;
    }

    // ── Flat repeating-group row cloning ───────────────────────────────────

    private void mergeFlatGroup(XWPFDocument document, RepeatingGroup group, JsonNode itemsNode) {
        XWPFTable table = findTableForPrefix(document, group.docxPrefix());
        if (table == null) {
            log.warn("No table found for group prefix {}", group.docxPrefix());
            return;
        }
        mergeFlatGroupInTable(table, group, itemsNode);
    }

    /** Same row-cloning routine, but targeting an already-located table directly — used both
     * for top-level groups and for the inner table of a nested block. */
    private void mergeFlatGroupInTable(XWPFTable table, RepeatingGroup group, JsonNode itemsNode) {
        XWPFTableRow templateRow = findTemplateRow(table, group.docxPrefix());
        if (templateRow == null) return;

        CTRow pristine = (CTRow) templateRow.getCtRow().copy();
        int insertPos = table.getRows().indexOf(templateRow) + 1;

        for (JsonNode item : iterable(itemsNode)) {
            // Fill BEFORE addRow(): XWPFTable.addRow() copies the row's XML content into the
            // table's tree at that moment (CTTbl.setTrArray is a copy-by-value operation, not a
            // live reference) — mutating the row afterward only touches a disconnected copy that
            // never reaches the serialized output.
            XWPFTableRow newRow = new XWPFTableRow((CTRow) pristine.copy(), table);
            fillRow(newRow, group, item);
            table.addRow(newRow, insertPos++);
        }
        table.removeRow(table.getRows().indexOf(templateRow));
    }

    private void fillRow(XWPFTableRow row, RepeatingGroup group, JsonNode item) {
        Function<String, String> resolver = token -> {
            String field = token.substring(group.docxPrefix().length() + 1);
            return item.path(field).asText("");
        };
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
                mergeParagraphPlaceholders(p, resolver);
            }
        }
    }

    private XWPFTable findTableForPrefix(XWPFDocument document, String prefix) {
        for (XWPFTable t : document.getTables()) {
            for (XWPFTableRow r : t.getRows()) {
                if (rowText(r).contains("${" + prefix + ".")) return t;
            }
        }
        return null;
    }

    private XWPFTableRow findTemplateRow(XWPFTable table, String prefix) {
        return table.getRows().stream()
                .filter(r -> rowText(r).contains("${" + prefix + "."))
                .findFirst().orElse(null);
    }

    private String rowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        row.getTableCells().forEach(c -> c.getParagraphs().forEach(p -> p.getRuns().forEach(r -> {
            String t = r.getText(0);
            if (t != null) sb.append(t);
        })));
        return sb.toString();
    }

    // ── Nested block (header paragraph + table pair, repeated per outer item) ─

    private void mergeNestedBlock(XWPFDocument document, NestedGroupBlock block, JsonNode outerItems) {
        int headerIdx = findHeaderParagraphIndex(document, block.headerDocxPrefix());
        if (headerIdx < 0 || headerIdx + 1 >= document.getBodyElements().size()
                || !(document.getBodyElements().get(headerIdx + 1) instanceof XWPFTable)) {
            log.warn("Nested block anchor not found for prefix {}", block.headerDocxPrefix());
            return;
        }

        XWPFParagraph originalHeader = (XWPFParagraph) document.getBodyElements().get(headerIdx);
        XWPFTable originalTable = (XWPFTable) document.getBodyElements().get(headerIdx + 1);
        CTP pristineHeader = (CTP) originalHeader.getCTP().copy();
        CTTbl pristineTable = (CTTbl) originalTable.getCTTbl().copy();

        XmlCursor cursor = originalHeader.getCTP().newCursor();
        for (JsonNode outerItem : iterable(outerItems)) {
            // Fill the header/table content OFF-TREE first, then attach via .set() — the same
            // fix as mergeFlatGroupInTable's row-cloning: mutating an XWPFParagraph/XWPFTable
            // AFTER its content has already been copied into the live tree via .set() operates
            // on a stale Java-side model that never reaches the serialized output, because
            // XWPFParagraph/XWPFTable cache their runs/rows at construction time and don't
            // re-parse after an external .set() call replaces their underlying XML.
            CTP headerCopy = (CTP) pristineHeader.copy();
            XWPFParagraph offTreeHeader = new XWPFParagraph(headerCopy, document);
            mergeParagraphPlaceholders(offTreeHeader, token ->
                    token.equals(block.headerDocxPrefix() + ".tableName") ? outerItem.path("tableName").asText("") : null);

            XWPFParagraph newHeader = document.insertNewParagraph(cursor);
            newHeader.getCTP().set(headerCopy);
            cursor.toCursor(newHeader.getCTP().newCursor());
            cursor.toEndToken();
            cursor.toNextToken();

            CTTbl tableCopy = (CTTbl) pristineTable.copy();
            XWPFTable offTreeTable = new XWPFTable(tableCopy, document);
            mergeFlatGroupInTable(offTreeTable,
                    new RepeatingGroup(block.rowDocxPrefix(), block.innerJsonField(), block.rowFields()),
                    outerItem.path(block.innerJsonField()));

            XWPFTable newTable = document.insertNewTbl(cursor);
            newTable.getCTTbl().set(tableCopy);
            cursor.toCursor(newTable.getCTTbl().newCursor());
            cursor.toEndToken();
            cursor.toNextToken();
        }

        document.removeBodyElement(document.getBodyElements().indexOf(originalHeader));
        document.removeBodyElement(document.getBodyElements().indexOf(originalTable));
    }

    private int findHeaderParagraphIndex(XWPFDocument document, String headerPrefix) {
        List<IBodyElement> body = document.getBodyElements();
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof XWPFParagraph p && paragraphText(p).contains("${" + headerPrefix + ".")) {
                return i;
            }
        }
        return -1;
    }

    private String paragraphText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        p.getRuns().forEach(r -> {
            String t = r.getText(0);
            if (t != null) sb.append(t);
        });
        return sb.toString();
    }

    // ── Vertical block (one item's fields laid out as table rows, whole table cloned per item) ─

    private void mergeVerticalBlock(XWPFDocument document, VerticalBlock block, JsonNode itemsNode) {
        XWPFTable originalTable = findTableForPrefix(document, block.docxPrefix());
        if (originalTable == null) {
            log.warn("No table found for vertical block prefix {}", block.docxPrefix());
            return;
        }

        CTTbl pristine = (CTTbl) originalTable.getCTTbl().copy();
        XmlCursor cursor = originalTable.getCTTbl().newCursor();
        for (JsonNode item : iterable(itemsNode)) {
            // Same off-tree-fill-then-attach fix as mergeFlatGroupInTable/mergeNestedBlock: mutate
            // the cloned table's content BEFORE it's attached to the live document tree.
            CTTbl copy = (CTTbl) pristine.copy();
            XWPFTable offTree = new XWPFTable(copy, document);
            fillVerticalTable(offTree, block, item);

            XWPFTable newTable = document.insertNewTbl(cursor);
            newTable.getCTTbl().set(copy);
            cursor.toCursor(newTable.getCTTbl().newCursor());
            cursor.toEndToken();
            cursor.toNextToken();
        }
        document.removeBodyElement(document.getBodyElements().indexOf(originalTable));
    }

    private void fillVerticalTable(XWPFTable table, VerticalBlock block, JsonNode item) {
        Function<String, String> resolver = token -> {
            if (!token.startsWith(block.docxPrefix() + ".")) return null;
            String field = token.substring(block.docxPrefix().length() + 1);
            return block.fields().contains(field) ? item.path(field).asText("") : null;
        };
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    mergeParagraphPlaceholders(p, resolver);
                }
            }
        }
    }

    // ── Paragraph block (one item's fields spread across a span of paragraphs, span cloned per item) ─

    private void mergeParagraphBlock(XWPFDocument document, ParagraphBlock block, JsonNode itemsNode) {
        List<XWPFParagraph> originals = findParagraphSpan(document, block.docxPrefix());
        if (originals.isEmpty()) {
            log.warn("No paragraph span found for block prefix {}", block.docxPrefix());
            return;
        }

        List<CTP> pristine = originals.stream().map(p -> (CTP) p.getCTP().copy()).toList();

        XmlCursor cursor = originals.get(0).getCTP().newCursor();
        for (JsonNode item : iterable(itemsNode)) {
            Function<String, String> itemResolver = token -> {
                if (!token.startsWith(block.docxPrefix() + ".")) return null;
                String field = token.substring(block.docxPrefix().length() + 1);
                return block.fields().contains(field) ? item.path(field).asText("") : null;
            };
            for (CTP pristineP : pristine) {
                CTP copy = (CTP) pristineP.copy();
                XWPFParagraph offTree = new XWPFParagraph(copy, document);
                mergeParagraphPlaceholders(offTree, itemResolver);

                XWPFParagraph newP = document.insertNewParagraph(cursor);
                newP.getCTP().set(copy);
                cursor.toCursor(newP.getCTP().newCursor());
                cursor.toEndToken();
                cursor.toNextToken();
            }
        }
        for (XWPFParagraph original : originals) {
            document.removeBodyElement(document.getBodyElements().indexOf(original));
        }
    }

    /** The contiguous run of body-level paragraphs spanning the first through the last
     * occurrence of "${prefix.field}" for this block's declared fields. */
    private List<XWPFParagraph> findParagraphSpan(XWPFDocument document, String prefix) {
        List<IBodyElement> body = document.getBodyElements();
        int first = -1, last = -1;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof XWPFParagraph p && paragraphText(p).contains("${" + prefix + ".")) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0) return List.of();
        List<XWPFParagraph> span = new ArrayList<>();
        for (int i = first; i <= last; i++) {
            span.add((XWPFParagraph) body.get(i));
        }
        return span;
    }

    private Iterable<JsonNode> iterable(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) return List.of();
        Iterator<JsonNode> it = arrayNode.elements();
        return () -> it;
    }
}
