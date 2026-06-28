package afb.astyann.projectservice.controller;

import afb.astyann.projectservice.dto.*;
import afb.astyann.projectservice.service.IProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final IProjectService projectService;

    /**
     * POST /api/v1/projects
     * Create a new project for the authenticated user.
     * Accepts multipart/form-data: title, description (optional), document (PDF or DOCX).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectDTO>> createProject(
            @RequestHeader("X-User-Id") String rawUserId,
            @RequestPart("title") String title,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart("document") MultipartFile document) {
        CreateProjectDTO dto = new CreateProjectDTO();
        dto.setTitle(title);
        dto.setDescription(description);
        ProjectDTO data = projectService.createProject(parseUserId(rawUserId), dto, document);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProjectDTO>builder()
                        .status(201)
                        .message("Project created successfully.")
                        .data(data)
                        .build());
    }

    /**
     * GET /api/v1/projects/{projectId}
     * Retrieve a project by its ID.
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDTO>> getProjectById(
            @PathVariable UUID projectId) {
        ProjectDTO data = projectService.getProjectById(projectId);
        return ResponseEntity.ok(ApiResponse.<ProjectDTO>builder()
                .status(200)
                .message("Project retrieved successfully.")
                .data(data)
                .build());
    }

    /**
     * GET /api/v1/projects/search?query=
     * Search the authenticated user's projects by title.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> searchProjects(
            @RequestHeader("X-User-Id") String rawUserId,
            @RequestParam(required = false) String query) {
        List<ProjectDTO> data = projectService.searchProjects(parseUserId(rawUserId), query);
        return ResponseEntity.ok(ApiResponse.<List<ProjectDTO>>builder()
                .status(200)
                .message("Projects retrieved successfully.")
                .data(data)
                .build());
    }

    /**
     * PUT /api/v1/projects/{projectId}
     * Update an existing project's fields.
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDTO>> updateProject(
            @PathVariable UUID projectId,
            @RequestBody UpdateProjectDTO dto) {
        ProjectDTO data = projectService.updateProject(projectId, dto);
        return ResponseEntity.ok(ApiResponse.<ProjectDTO>builder()
                .status(200)
                .message("Project updated successfully.")
                .data(data)
                .build());
    }

    /**
     * DELETE /api/v1/projects/{projectId}
     * Delete a project and all its guided questions.
     */
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/projects/{projectId}/generate
     * Trigger a generation pipeline for the project.
     */
    @PostMapping("/{projectId}/generate")
    public ResponseEntity<ApiResponse<Void>> triggerGeneration(
            @PathVariable UUID projectId,
            @Valid @RequestBody GenerationRequestDTO dto) {
        projectService.triggerGeneration(projectId, dto.getType());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<Void>builder()
                        .status(202)
                        .message("Generation triggered successfully.")
                        .data(null)
                        .build());
    }

    /**
     * GET /api/v1/projects/{projectId}/guided-questions
     * Get the AI-generated guided questions for a project.
     */
    @GetMapping("/{projectId}/guided-questions")
    public ResponseEntity<ApiResponse<List<GuidedQuestionDTO>>> getGuidedQuestions(
            @PathVariable UUID projectId) {
        List<GuidedQuestionDTO> data = projectService.getGuidedQuestions(projectId);
        return ResponseEntity.ok(ApiResponse.<List<GuidedQuestionDTO>>builder()
                .status(200)
                .message("Guided questions retrieved successfully.")
                .data(data)
                .build());
    }

    /**
     * POST /api/v1/projects/{projectId}/guided-questions/answers
     * Submit answers to guided questions. AI merges them with the document context
     * to produce the complete project description.
     */
    @PostMapping("/{projectId}/guided-questions/answers")
    public ResponseEntity<ApiResponse<ProjectDTO>> submitGuidedAnswers(
            @PathVariable UUID projectId,
            @RequestBody SubmitAnswersDTO dto) {
        ProjectDTO data = projectService.submitGuidedAnswers(projectId, dto);
        return ResponseEntity.ok(ApiResponse.<ProjectDTO>builder()
                .status(200)
                .message("Answers submitted. Project context updated.")
                .data(data)
                .build());
    }

    private UUID parseUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id header is missing");
        }
        String s = rawUserId.trim();
        // Strip 0x prefix if present
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        // Insert dashes if raw 32-char hex (no dashes)
        if (s.length() == 32 && !s.contains("-")) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                    + s.substring(12, 16) + "-" + s.substring(16, 20) + "-"
                    + s.substring(20);
        }
        return UUID.fromString(s);
    }
}
