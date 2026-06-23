package afb.astyann.projectservice.service;

import afb.astyann.projectservice.domain.GenerationType;
import afb.astyann.projectservice.dto.CreateProjectDTO;
import afb.astyann.projectservice.dto.ProjectDTO;
import afb.astyann.projectservice.dto.UpdateProjectDTO;

import java.util.List;
import java.util.UUID;

public interface IProjectService {

    /** Create a new project for the given user. */
    ProjectDTO createProject(UUID userId, CreateProjectDTO dto);

    /** Update an existing project's mutable fields. */
    ProjectDTO updateProject(UUID projectId, UpdateProjectDTO dto);

    /** Delete a project and all its guided questions. */
    void deleteProject(UUID projectId);

    /** Search projects by title for the given user. Returns all projects if query is blank. */
    List<ProjectDTO> searchProjects(UUID userId, String query);

    /** Retrieve a single project by its ID. */
    ProjectDTO getProjectById(UUID projectId);

    /** Trigger a generation pipeline for the given project. */
    void triggerGeneration(UUID projectId, GenerationType type);
}
