package com.officeplatform.service.project;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.officeplatform.dto.response.ProjectResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.repository.ApiKeyRepository;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ApiKeyRepository apiKeyRepository;

    public ProjectServiceImpl(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    public List<ProjectResponse> listProjects(Long excludeApiKeyId) {
        return toResponses(apiKeyRepository.findAllByActiveTrue(), excludeApiKeyId);
    }

    @Override
    public List<ProjectResponse> searchProjects(String term, Long excludeApiKeyId) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return toResponses(apiKeyRepository.findAllByActiveTrueAndNameContainingIgnoreCase(term.trim()), excludeApiKeyId);
    }

    private List<ProjectResponse> toResponses(List<ApiKeyEntity> keys, Long excludeApiKeyId) {
        return keys.stream()
                .filter(key -> !Objects.equals(key.getId(), excludeApiKeyId))
                .map(key -> new ProjectResponse(key.getId(),
                        (key.getName() != null && !key.getName().isBlank()) ? key.getName().trim() : "Proyecto #" + key.getId()))
                .toList();
    }

}
