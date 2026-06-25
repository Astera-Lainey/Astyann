package afb.astyann.projectservice.client;

import afb.astyann.projectservice.dto.ProjectAnalysisResponseDTO;
import afb.astyann.projectservice.pcsf.dto.InferenceResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "ai-service", url = "${services.ai.url:http://localhost:8089}")
public interface AIServiceClient {

    @PostMapping(value = "/api/v1/ai/projects/{projectId}/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProjectAnalysisResponseDTO analyzeProjectInformation(
            @PathVariable UUID projectId,
            @RequestPart("document") MultipartFile document);

    @PostMapping("/api/v1/ai/projects/{projectId}/merge")
    ProjectAnalysisResponseDTO mergeDocumentAndAnswers(
            @PathVariable UUID projectId,
            @RequestBody MergeRequestBody body);

    @PostMapping("/api/v1/ai/infer")
    InferenceResponseDTO infer(@RequestBody InferBody body);

    record MergeRequestBody(UUID projectId, String documentContext, List<AnswerItem> answers) {
        public record AnswerItem(String question, String answer) {}
    }

    record InferBody(String model, String systemPrompt, String userPrompt) {}
}
