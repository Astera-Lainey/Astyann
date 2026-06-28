package afb.astyann.aiorchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

@Service
@Slf4j
public class DocumentParserService {

    public String extractText(MultipartFile file) {
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        String filename    = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";

        try {
            if (contentType.equals("application/pdf") || filename.toLowerCase().endsWith(".pdf")) {
                return extractFromPdf(file.getBytes());
            } else if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || filename.toLowerCase().endsWith(".docx")) {
                return extractFromDocx(file.getBytes());
            } else {
                throw new IllegalArgumentException("Unsupported file type: only PDF and DOCX are accepted.");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to extract text from document", ex);
            throw new RuntimeException("Could not parse the uploaded document.", ex);
        }
    }

    private String extractFromPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.debug("PDF extracted: {} characters", text.length());
            return text;
        }
    }

    private String extractFromDocx(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(p -> {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            });
            document.getTables().forEach(table ->
                    table.getRows().forEach(row ->
                            row.getTableCells().forEach(cell -> {
                                String text = cell.getText();
                                if (text != null && !text.isBlank()) {
                                    sb.append(text).append(" ");
                                }
                            })));
            String text = sb.toString();
            log.debug("DOCX extracted: {} characters", text.length());
            return text;
        }
    }
}
