package com.officeplatform.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.ProjectResponse;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.project.ProjectService;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list(
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<ProjectResponse> data = projectService.listProjects(principal.getApiKeyId());

        ApiResponse<List<ProjectResponse>> response = ApiResponse.<List<ProjectResponse>>builder()
                .success(true)
                .message("Proyectos listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> search(
            @RequestParam("q") String q,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<ProjectResponse> data = projectService.searchProjects(q, principal.getApiKeyId());

        ApiResponse<List<ProjectResponse>> response = ApiResponse.<List<ProjectResponse>>builder()
                .success(true)
                .message("Búsqueda de proyectos completada")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

}
