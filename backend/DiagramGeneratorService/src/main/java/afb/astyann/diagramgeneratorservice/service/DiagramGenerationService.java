package afb.astyann.diagramgeneratorservice.service;

import afb.astyann.diagramgeneratorservice.client.AIServiceClient;
import afb.astyann.diagramgeneratorservice.client.KrokiClient;
import afb.astyann.diagramgeneratorservice.client.RAGServiceClient;
import afb.astyann.diagramgeneratorservice.client.RequirementServiceClient;
import afb.astyann.diagramgeneratorservice.domain.DiagramStatus;
import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import afb.astyann.diagramgeneratorservice.domain.UMLDiagram;
import afb.astyann.diagramgeneratorservice.dto.ApiResponse;
import afb.astyann.diagramgeneratorservice.dto.GenerateDiagramsRequest;
import afb.astyann.diagramgeneratorservice.exception.DiagramNotFoundException;
import afb.astyann.diagramgeneratorservice.exception.DownstreamServiceException;
import afb.astyann.diagramgeneratorservice.exception.InvalidRenderFormatException;
import afb.astyann.diagramgeneratorservice.exception.PcsfNotApprovedException;
import afb.astyann.diagramgeneratorservice.repository.UMLDiagramRepository;
import afb.astyann.diagramgeneratorservice.util.PlantUmlCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiagramGenerationService {

    private static final Set<String> VALID_FORMATS = Set.of("SVG", "PNG");

    private final UMLDiagramRepository repository;
    private final RequirementServiceClient requirementServiceClient;
    private final RAGServiceClient ragServiceClient;
    private final AIServiceClient aiServiceClient;
    private final KrokiClient krokiClient;
    private final StorageService storageService;

    @Qualifier("diagramExecutor")
    private final Executor diagramExecutor;

    public List<UMLDiagram> generateDiagrams(UUID projectId, GenerateDiagramsRequest request) {
        verifyPcsfApproved(projectId);

        String format = normalizeFormat(request.getRenderFormat());
        List<DiagramType> types = (request.getDiagramTypes() == null || request.getDiagramTypes().isEmpty())
                ? List.of(DiagramType.values())
                : request.getDiagramTypes();

        List<CompletableFuture<UMLDiagram>> futures = types.stream()
                .map(type -> CompletableFuture.supplyAsync(() -> generateOne(projectId, type, format), diagramExecutor))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    public List<UMLDiagram> getDiagrams(UUID projectId, DiagramType type, DiagramStatus status) {
        return repository.findByProjectId(projectId).stream()
                .filter(d -> type == null || d.getType() == type)
                .filter(d -> status == null || d.getStatus() == status)
                .toList();
    }

    public byte[] renderDiagram(UUID projectId, UUID diagramId, String format) {
        String normalized = normalizeFormat(format);
        UMLDiagram diagram = repository.findById(diagramId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DiagramNotFoundException(projectId, diagramId));

        if (normalized.equalsIgnoreCase(diagram.getRenderFormat()) && diagram.getGeneratedImagePath() != null) {
            return storageService.loadImage(diagram.getGeneratedImagePath());
        }
        // Requested format differs from what's cached on disk — re-render on the fly, don't persist.
        return krokiClient.render(diagram.getSourceCode(), normalized);
    }

    private UMLDiagram generateOne(UUID projectId, DiagramType type, String format) {
        String context;
        try {
            context = ragServiceClient.getContext(projectId, DiagramPromptTemplates.ragQuery(type), 10);
        } catch (Exception ex) {
            throw new DownstreamServiceException("Could not retrieve RAG context for " + type, ex);
        }

        String userPrompt = DiagramPromptTemplates.userPrompt(type, context);
        String rawContent;
        try {
            var response = aiServiceClient.infer(new AIServiceClient.InferBody(
                    DiagramPromptTemplates.MODEL, DiagramPromptTemplates.SYSTEM_PROMPT, userPrompt));
            rawContent = response.content();
        } catch (Exception ex) {
            throw new DownstreamServiceException("AI generation failed for " + type, ex);
        }

        String source = PlantUmlCleaner.clean(rawContent);

        byte[] image;
        try {
            image = krokiClient.render(source, format);
        } catch (DownstreamServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DownstreamServiceException("Rendering failed for " + type, ex);
        }

        UMLDiagram diagram = repository.findByProjectIdAndType(projectId, type).orElseGet(UMLDiagram::new);
        diagram.setProjectId(projectId);
        diagram.setType(type);
        diagram.setStatus(DiagramStatus.PENDING_APPROVAL);
        diagram.setSourceCode(source);
        diagram.setRenderFormat(format);
        UMLDiagram saved = repository.save(diagram);

        String path = storageService.saveImage(projectId, saved.getDiagramId(), format, image);
        saved.setGeneratedImagePath(path);
        return repository.save(saved);
    }

    private void verifyPcsfApproved(UUID projectId) {
        ApiResponse<RequirementServiceClient.PcsfStatusPayload> response;
        try {
            response = requirementServiceClient.getPcsfStatus(projectId);
        } catch (Exception ex) {
            throw new DownstreamServiceException("Could not reach RequirementService.", ex);
        }
        if (response == null || response.getData() == null
                || !"APPROVED".equals(response.getData().pcsfStatus())) {
            throw new PcsfNotApprovedException(projectId);
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return "SVG";
        String upper = format.trim().toUpperCase();
        if (!VALID_FORMATS.contains(upper)) {
            throw new InvalidRenderFormatException(format);
        }
        return upper;
    }
}
