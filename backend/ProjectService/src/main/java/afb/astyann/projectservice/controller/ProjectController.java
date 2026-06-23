package afb.astyann.projectservice.controller;

import afb.astyann.projectservice.dto.*;
import afb.astyann.projectservice.service.IProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDTO>> createProject(
            @RequestHeader("X-User-Id") String rawUserId,
            @Valid @RequestBody CreateProjectDTO dto) {
        ProjectDTO data = projectService.createProject(parseUserId(rawUserId), dto);
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

    private UUID parseUserId(String rawUserId) {
        return UUID.fromString(rawUserId);
    }
}
