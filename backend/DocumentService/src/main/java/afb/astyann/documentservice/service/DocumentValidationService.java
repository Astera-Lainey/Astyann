package afb.astyann.documentservice.service;

import afb.astyann.documentservice.domain.Document;
import afb.astyann.documentservice.dto.ValidationCheckDTO;
import afb.astyann.documentservice.dto.ValidationReportDTO;
import afb.astyann.documentservice.exception.DocumentValidationFailedException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DocumentValidationService {

    private static final int MIN_CONTENT_LENGTH = 200;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");

    private final StorageService storageService;

    /**
     * Runs automated consistency checks against the document's stored .docx. Throws
     * DocumentValidationFailedException (blocking) if a literal, unresolved placeholder remains
     * — a document with visible "${...}" text is broken and must not be approved. Other checks
     * are soft/informational: they contribute to the score but don't block approval.
     */
    public ValidationReportDTO validate(Document document) {
        String text = extractText(document);

        Matcher m = PLACEHOLDER.matcher(text);
        if (m.find()) {
            throw new DocumentValidationFailedException(document.getDocumentId(),
                    "document still contains an unresolved placeholder (e.g. \"" + m.group() + "\")");
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
