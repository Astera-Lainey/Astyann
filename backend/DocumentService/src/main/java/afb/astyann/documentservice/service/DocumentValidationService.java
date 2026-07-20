package afb.astyann.documentservice.service;

import afb.astyann.documentservice.domain.Document;
import afb.astyann.documentservice.dto.ValidationCheckDTO;
import afb.astyann.documentservice.dto.ValidationReportDTO;
import afb.astyann.documentservice.exception.DocumentValidationFailedException;
import afb.astyann.documentservice.service.schema.DocumentSchema;
import afb.astyann.documentservice.service.schema.DocumentSchemas;
import afb.astyann.documentservice.service.schema.NestedGroupBlock;
import afb.astyann.documentservice.service.schema.ScalarField;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentValidationService {

    private static final int MIN_CONTENT_LENGTH = 200;
    // Captures the token so it can be checked against the document type's own schema — content
    // the AI legitimately writes (e.g. "${DB_PASSWORD}" as example env-var syntax in a deployment
    // guide) happens to match "${...}" too, but isn't one of our template placeholders.
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([\\w.]+)}");

    private final StorageService storageService;

    /**
     * Runs automated consistency checks against the document's stored .docx. Throws
     * DocumentValidationFailedException (blocking) if a literal, unresolved placeholder remains
     * — a document with visible "${...}" text matching this type's own schema is broken and must
     * not be approved. Other checks are soft/informational: they contribute to the score but
     * don't block approval.
     */
    public ValidationReportDTO validate(Document document) {
        String text = extractText(document);

        DocumentSchema schema = DocumentSchemas.get(document.getType());
        Set<String> scalarPaths = schema.scalars().stream().map(ScalarField::path).collect(Collectors.toSet());
        Set<String> knownPrefixes = new HashSet<>();
        schema.groups().forEach(g -> knownPrefixes.add(g.docxPrefix()));
        schema.verticalBlocks().forEach(g -> knownPrefixes.add(g.docxPrefix()));
        schema.paragraphBlocks().forEach(g -> knownPrefixes.add(g.docxPrefix()));
        for (NestedGroupBlock nb : schema.nestedBlocks()) {
            knownPrefixes.add(nb.headerDocxPrefix());
            knownPrefixes.add(nb.rowDocxPrefix());
        }

        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            String token = m.group(1);
            String prefix = token.contains(".") ? token.substring(0, token.indexOf('.')) : token;
            if (scalarPaths.contains(token) || knownPrefixes.contains(prefix)) {
                throw new DocumentValidationFailedException(document.getDocumentId(),
                        "document still contains an unresolved placeholder (e.g. \"" + m.group() + "\")");
            }
        }

        List<ValidationCheckDTO> checks = new ArrayList<>();
        checks.add(ValidationCheckDTO.builder()
                .name("no_unresolved_placeholders")
                .passed(true)
                .detail("No literal ${...} placeholders remain in the document text.")
                .build());

        boolean longEnough = text.trim().length() > MIN_CONTENT_LENGTH;
        checks.add(ValidationCheckDTO.builder()
                .name("minimum_content_length")
                .passed(longEnough)
                .detail("Extracted text is " + text.trim().length() + " characters (threshold " + MIN_CONTENT_LENGTH + ").")
                .build());

        int score = (int) Math.round(100.0 * checks.stream().filter(ValidationCheckDTO::isPassed).count() / checks.size());
        return ValidationReportDTO.builder().checks(checks).score(score).build();
    }

    private String extractText(Document document) {
        byte[] bytes = storageService.loadDocument(document.getPath());
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             XWPFDocument xwpf = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(xwpf)) {
            return extractor.getText();
        } catch (Exception ex) {
            throw new DocumentValidationFailedException(document.getDocumentId(),
                    "could not read stored document for validation: " + ex.getMessage());
        }
    }
}
