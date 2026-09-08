package com.officeplatform.service.project;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.officeplatform.dto.response.ProjectResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.EditorSessionRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.KnownUserRepository;

@Service
public class ProjectServiceImpl implements ProjectService {

    /** Ordena respetando acentos y ñ, que un compareTo de String desordena. */
    private static final Collator SPANISH = Collator.getInstance(new Locale("es"));

    private final ApiKeyRepository apiKeyRepository;
    private final FileRepository fileRepository;
    private final EditorSessionRepository editorSessionRepository;
    private final KnownUserRepository knownUserRepository;

    public ProjectServiceImpl(
            ApiKeyRepository apiKeyRepository,
            FileRepository fileRepository,
            EditorSessionRepository editorSessionRepository,
            KnownUserRepository knownUserRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.fileRepository = fileRepository;
        this.editorSessionRepository = editorSessionRepository;
        this.knownUserRepository = knownUserRepository;
        SPANISH.setStrength(Collator.PRIMARY);
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
        return toResponses(
                apiKeyRepository.findAllByActiveTrueAndNameContainingIgnoreCase(term.trim()), excludeApiKeyId);
    }

    /**
     * Projects that already use the platform, resolved in three aggregate queries.
     *
     * <p>Asking per project would be three round trips each: with twenty consumer applications
     * that is sixty queries to paint one dropdown.
     */
    private Set<Long> activeConsumerIds() {
        Set<Long> ids = new HashSet<>();
        ids.addAll(fileRepository.findDistinctApiKeyIds());
        ids.addAll(editorSessionRepository.findDistinctApiKeyIds());
        ids.addAll(knownUserRepository.findDistinctApiKeyIds());
        ids.remove(null);
        return ids;
    }

    /**
     * Active consumers first, then alphabetically.
     *
     * <p>The list mixes real applications with keys created for a test and never used. Someone
     * choosing where to share needs the former, and sorting by name alone buries them among the
     * latter.
     */
    private List<ProjectResponse> toResponses(List<ApiKeyEntity> keys, Long excludeApiKeyId) {
        Set<Long> active = activeConsumerIds();

        return keys.stream()
                .filter(key -> !Objects.equals(key.getId(), excludeApiKeyId))
                .map(key -> new ProjectResponse(
                        key.getId(),
                        (key.getName() != null && !key.getName().isBlank())
                                ? key.getName().trim()
                                : "Proyecto #" + key.getId(),
                        active.contains(key.getId())))
                .sorted(Comparator
                        .comparing(ProjectResponse::isActiveConsumer, Comparator.reverseOrder())
                        .thenComparing(ProjectResponse::getProjectName, SPANISH))
                .toList();
    }

}
