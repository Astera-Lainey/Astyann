package afb.astyann.projectservice.service;

import afb.astyann.projectservice.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface IProjectService {

    /** Create a new project. Validates and stores the uploaded document, then triggers AI analysis. */
    ProjectDTO createProject(UUID userId, CreateProjectDTO dto, MultipartFile document);

    /** Update an existing project's mutable fields. */
    ProjectDTO updateProject(UUID projectId, UpdateProjectDTO dto);

    /** Delete a project and all its guided questions. */
    void deleteProject(UUID projectId);

    /** Search projects by title for the given user. Returns all projects if query is blank. */
    List<ProjectDTO> searchProjects(UUID userId, String query);

    /** Retrieve a single project by its ID. */
    ProjectDTO getProjectById(UUID projectId);

}
