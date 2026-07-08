package afb.astyann.diagramgeneratorservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${app.upload-dir:uploads/diagrams}")
    private String uploadDir;

    public String saveImage(UUID projectId, UUID diagramId, String format, byte[] bytes) {
        try {
            Path dir = Paths.get(uploadDir).resolve(projectId.toString());
            Files.createDirectories(dir);
            String ext = "PNG".equalsIgnoreCase(format) ? "png" : "svg";
            String filename = diagramId + "_" + UUID.randomUUID() + "." + ext;
            Path destination = dir.resolve(filename);
            Files.write(destination, bytes);
            return destination.toString();
        } catch (IOException ex) {
            throw new RuntimeException("Could not store rendered diagram.", ex);
        }
    }

    public byte[] loadImage(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException ex) {
            throw new RuntimeException("Could not read stored diagram.", ex);
        }
    }
}
