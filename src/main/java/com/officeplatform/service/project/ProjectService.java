package com.officeplatform.service.project;

import java.util.List;

import com.officeplatform.dto.response.ProjectResponse;

public interface ProjectService {

    /** All active projects except {@code excludeApiKeyId} (the caller's own project). */
    List<ProjectResponse> listProjects(Long excludeApiKeyId);

    /** Active projects whose name matches the term, excluding the caller's own project. */
    List<ProjectResponse> searchProjects(String term, Long excludeApiKeyId);

}
