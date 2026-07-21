package afb.astyann.documentservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${app.upload-dir:uploads/documents}")
    private String uploadDir;

    public String saveDocument(UUID projectId, UUID documentId, byte[] bytes) {
        try {
            Path dir = Paths.get(uploadDir).resolve(projectId.toString());
            Files.createDirectories(dir);
            String filename = documentId + "_" + UUID.randomUUID() + ".docx";
            Path destination = dir.resolve(filename);
            Files.write(destination, bytes);
            return destination.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store generated document.", ex);
        }
    }

    public byte[] loadDocument(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException ex) {
            throw new RuntimeException("Could not read stored document.", ex);
        }
    }
}
