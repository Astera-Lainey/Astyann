package afb.astyann.codegeneration.service;

import afb.astyann.codegeneration.domain.CodeLayer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${app.upload-dir:uploads/code}")
    private String uploadDir;

    public void deleteProjectDirectory(UUID projectId) {
        Path dir = Paths.get(uploadDir).resolve(projectId.toString());
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (IOException | UncheckedIOException ex) {
            throw new RuntimeException("Could not delete stored code for project " + projectId, ex);
        }
    }

    public String saveZip(UUID projectId, CodeLayer layer, byte[] bytes) {
        try {
            Path dir = Paths.get(uploadDir).resolve(projectId.toString());
            Files.createDirectories(dir);
            String filename = layer.name().toLowerCase() + "_" + UUID.randomUUID() + ".zip";
            Path destination = dir.resolve(filename);
            Files.write(destination, bytes);
            return destination.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store generated code archive.", ex);
        }
    }

    public byte[] loadZip(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException ex) {
            throw new RuntimeException("Could not read stored code archive.", ex);
        }
    }
}
