package com.officeplatform.service.share;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.dto.request.ShareRequest;
import com.officeplatform.dto.response.FileResponse;
import com.officeplatform.dto.response.FolderResponse;
import com.officeplatform.dto.response.SharePermissionResponse;
import com.officeplatform.dto.response.SharedResourceResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.exception.FolderNotFoundException;
import com.officeplatform.exception.ShareAccessDeniedException;
import com.officeplatform.exception.SharePermissionNotFoundException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.entity.ActivityAction;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.activity.ActivityLogRecorder;

@Service
public class ShareServiceImpl implements ShareService {

    private static final String ROLE_ADMIN = "admin";

    private final SharePermissionRepository sharePermissionRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final KnownUserRepository knownUserRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ActivityLogRecorder activityLogRecorder;

    public ShareServiceImpl(
            SharePermissionRepository sharePermissionRepository,
            FileRepository fileRepository,
            FolderRepository folderRepository,
            KnownUserRepository knownUserRepository,
            ApiKeyRepository apiKeyRepository,
            ActivityLogRecorder activityLogRecorder) {
        this.sharePermissionRepository = sharePermissionRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.knownUserRepository = knownUserRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.activityLogRecorder = activityLogRecorder;
    }

    @Override
    @Transactional
    public SharePermissionResponse share(ShareRequest request, ApiKeyPrincipal principal) {
        Long sourceApiKeyId = principal.getApiKeyId();
        assertCanManage(request.getResourceType(), request.getResourceId(), principal);

        String targetUserId = null;
        Long targetApiKeyId = null;
        if (request.getTargetType() == TargetType.USER) {
            if (request.getTargetUserId() == null || request.getTargetUserId().isBlank()) {
                throw new IllegalArgumentException("targetUserId es obligatorio cuando targetType = USER");
            }
            targetUserId = request.getTargetUserId().trim();
        } else {
            if (request.getTargetApiKeyId() == null) {
                throw new IllegalArgumentException("targetApiKeyId es obligatorio cuando targetType = PROJECT");
            }
            targetApiKeyId = request.getTargetApiKeyId();
        }

        // Deduplicate: a grant to the same resource + same target updates the level instead of piling up.
        final String tUser = targetUserId;
        final Long tApiKey = targetApiKeyId;
        SharePermissionEntity entity = sharePermissionRepository
                .findAllByResourceTypeAndResourceIdAndSourceApiKeyId(
                        request.getResourceType(), request.getResourceId(), sourceApiKeyId)
                .stream()
                .filter(p -> p.getTargetType() == request.getTargetType()
                        && Objects.equals(p.getTargetUserId(), tUser)
                        && Objects.equals(p.getTargetApiKeyId(), tApiKey))
                .findFirst()
                .orElse(null);

        if (entity != null) {
            entity.setPermissionLevel(request.getPermissionLevel());
            entity.setExpiresAt(request.getExpiresAt());
            entity.setNotes(request.getNotes());
            entity.setCanShare(Boolean.TRUE.equals(request.getCanShare()));
            // Volver a compartir con alguien que lo tenia descartado se lo devuelve a la vista:
            // dejarlo en su papelera haria que el nuevo permiso pareciera no haber llegado.
            entity.setDeletedAt(null);
        } else {
            entity = SharePermissionEntity.builder()
                    .resourceType(request.getResourceType())
                    .resourceId(request.getResourceId())
                    .sourceApiKeyId(sourceApiKeyId)
                    .targetType(request.getTargetType())
                    .targetUserId(targetUserId)
                    .targetApiKeyId(targetApiKeyId)
                    .permissionLevel(request.getPermissionLevel())
                    .sharedByUserId(principal.resolveUserId(null))
                    .sharedByName(principal.resolveUserName(null))
                    .notes(request.getNotes())
                    .canShare(Boolean.TRUE.equals(request.getCanShare()))
                    .build();
        }

        SharePermissionEntity saved = sharePermissionRepository.save(entity);

        // Record trace in activity log
        try {
            String resourceName = "Recurso #" + request.getResourceId();
            Long fileId = null;
            Long folderId = null;
            if (request.getResourceType() == ResourceType.FILE) {
                fileId = request.getResourceId();
                FileEntity f = fileRepository.findById(fileId).orElse(null);
                if (f != null) {
                    resourceName = f.getOriginalFileName();
                    folderId = f.getFolderId();
                }
            } else if (request.getResourceType() == ResourceType.FOLDER) {
                folderId = request.getResourceId();
                FolderEntity fold = folderRepository.findById(folderId).orElse(null);
                if (fold != null) {
                    resourceName = fold.getName();
                }
            }

            String targetDesc;
            if (request.getTargetType() == TargetType.USER) {
                String targetUserName = null;
                if (targetUserId != null) {
                    targetUserName = knownUserRepository.findByApiKeyIdAndUserId(sourceApiKeyId, targetUserId)
                            .map(u -> u.getDisplayName())
                            .orElse(null);
                }
                targetDesc = (targetUserName != null ? targetUserName : ("Usuario " + targetUserId));
            } else {
                String targetProjectName = null;
                if (targetApiKeyId != null) {
                    targetProjectName = apiKeyRepository.findById(targetApiKeyId).map(ApiKeyEntity::getName).orElse(null);
                }
                targetDesc = (targetProjectName != null ? targetProjectName : ("Proyecto #" + targetApiKeyId));
            }

            String details = "Compartido con " + targetDesc + " (" + request.getPermissionLevel() + ")";
            if (request.getNotes() != null && !request.getNotes().isBlank()) {
                details += " — Nota: " + request.getNotes();
            }

            activityLogRecorder.record(
                    sourceApiKeyId,
                    principal.resolveUserId(null),
                    principal.resolveUserName(null),
                    ActivityAction.SHARE,
                    fileId,
                    resourceName,
                    details,
                    folderId);
        } catch (Exception ignored) {}

        return toPermissionResponse(saved);
    }

    @Override
    public List<SharePermissionResponse> listResourcePermissions(
            ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal) {
        assertCanManage(resourceType, resourceId, principal);
        return sharePermissionRepository
                .findAllByResourceTypeAndResourceIdAndSourceApiKeyId(resourceType, resourceId, principal.getApiKeyId())
                .stream()
                .map(this::toPermissionResponse)
                .toList();
    }

    /**
     * Quita el acceso de una persona sobre un recurso, sin tocar el recurso ni el de los demás.
     *
     * <p>No exige ser dueño ni administrador, y es intencional: la única acción que habilita es
     * renunciar al propio acceso. Alguien que retira su propia concesión no le quita nada a nadie,
     * y exigir permisos acá era justo lo que dejaba la papelera imposible de vaciar.
     */
    @Override
    @Transactional
    public int revokeAccessForUser(ResourceType resourceType, Long resourceId, String targetUserId) {
        if (resourceId == null || targetUserId == null || targetUserId.isBlank()) {
            return 0;
        }
        List<SharePermissionEntity> grants = sharePermissionRepository
                .findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
                        resourceType, resourceId, TargetType.USER, targetUserId.trim());
        if (grants.isEmpty()) {
            return 0;
        }
        sharePermissionRepository.deleteAll(grants);
        return grants.size();
    }

    @Override
    @Transactional
    public void revoke(Long permissionId, ApiKeyPrincipal principal) {
        SharePermissionEntity permission = sharePermissionRepository.findById(permissionId)
                .orElseThrow(() -> new SharePermissionNotFoundException(permissionId));
        // Don't leak the existence of permissions in other projects.
        if (!Objects.equals(permission.getSourceApiKeyId(), principal.getApiKeyId())) {
            throw new SharePermissionNotFoundException(permissionId);
        }
        assertCanManage(permission.getResourceType(), permission.getResourceId(), principal);
        sharePermissionRepository.delete(permission);

        try {
            String resourceName = "Recurso #" + permission.getResourceId();
            Long fileId = permission.getResourceType() == ResourceType.FILE ? permission.getResourceId() : null;
            Long folderId = permission.getResourceType() == ResourceType.FOLDER ? permission.getResourceId() : null;
            if (fileId != null) {
                FileEntity f = fileRepository.findById(fileId).orElse(null);
                if (f != null) {
                    resourceName = f.getOriginalFileName();
                }
            } else if (folderId != null) {
                FolderEntity fold = folderRepository.findById(folderId).orElse(null);
                if (fold != null) {
                    resourceName = fold.getName();
                }
            }

            String targetDesc = permission.getTargetType() == TargetType.USER
                    ? ("Usuario " + permission.getTargetUserId())
                    : ("Proyecto #" + permission.getTargetApiKeyId());

            activityLogRecorder.record(
                    permission.getSourceApiKeyId(),
                    principal.resolveUserId(null),
                    principal.resolveUserName(null),
                    ActivityAction.REVOKE_SHARE,
                    fileId,
                    resourceName,
                    "Revocó acceso a " + targetDesc,
                    folderId);
        } catch (Exception ignored) {}
    }

    @Override
    public List<SharedResourceResponse> sharedWithMe(ApiKeyPrincipal principal) {
        String userId = principal.resolveUserId(null);
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        // Lo descartado no se lista acá: vive en la papelera de esta persona hasta que decida
        // restaurarlo o purgarlo, sin que el autor ni los demás destinatarios noten nada.
        return resolveShared(sharePermissionRepository
                .findAllByTargetTypeAndTargetUserIdAndDeletedAtIsNull(TargetType.USER, userId));
    }

    @Override
    public List<SharedResourceResponse> trashedSharedWithMe(ApiKeyPrincipal principal) {
        String userId = principal.resolveUserId(null);
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return resolveShared(sharePermissionRepository
                .findAllByTargetTypeAndTargetUserIdAndDeletedAtIsNotNull(TargetType.USER, userId));
    }

    @Override
    @Transactional
    public int discardForUser(ResourceType resourceType, Long resourceId, String targetUserId) {
        return markGrants(resourceType, resourceId, targetUserId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public int restoreForUser(ResourceType resourceType, Long resourceId, String targetUserId) {
        return markGrants(resourceType, resourceId, targetUserId, null);
    }

    /** Escribe (o limpia) la marca de papelera sobre las concesiones de una persona. */
    private int markGrants(ResourceType resourceType, Long resourceId, String targetUserId,
                           LocalDateTime deletedAt) {
        if (resourceId == null || targetUserId == null || targetUserId.isBlank()) {
            return 0;
        }
        List<SharePermissionEntity> grants = sharePermissionRepository
                .findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
                        resourceType, resourceId, TargetType.USER, targetUserId.trim());
        if (grants.isEmpty()) {
            return 0;
        }
        grants.forEach(g -> g.setDeletedAt(deletedAt));
        sharePermissionRepository.saveAll(grants);
        return grants.size();
    }

    @Override
    public List<SharedResourceResponse> sharedWithProject(ApiKeyPrincipal principal) {
        return resolveShared(
                sharePermissionRepository.findAllByTargetTypeAndTargetApiKeyId(TargetType.PROJECT, principal.getApiKeyId()));
    }

    @Override
    public List<FileResponse> filterFilesByPermissions(List<FileResponse> files, ApiKeyPrincipal principal) {
        if (files.isEmpty()) {
            return files;
        }
        Long apiKeyId = principal.getApiKeyId();
        String viewerUserId = principal.resolveUserId(null);
        boolean admin = isProjectAdmin(apiKeyId, viewerUserId);

        List<FileResponse> visible = new ArrayList<>();
        for (FileResponse file : files) {
            List<SharePermissionEntity> perms = sharePermissionRepository
                    .findAllByResourceTypeAndResourceIdAndSourceApiKeyId(ResourceType.FILE, file.getId(), apiKeyId);
            if (perms.isEmpty()) {
                visible.add(file);
            } else if (admin || isCreator(file.getCreatedByUserId(), file.getCreatedByName(), principal)) {
                file.setRestricted(true);
                visible.add(file);
            }
        }
        return visible;
    }

    @Override
    public List<FolderResponse> filterFoldersByPermissions(List<FolderResponse> folders, ApiKeyPrincipal principal) {
        if (folders.isEmpty()) {
            return folders;
        }
        Long apiKeyId = principal.getApiKeyId();
        String viewerUserId = principal.resolveUserId(null);
        boolean admin = isProjectAdmin(apiKeyId, viewerUserId);

        List<FolderResponse> visible = new ArrayList<>();
        for (FolderResponse folder : folders) {
            List<SharePermissionEntity> perms = sharePermissionRepository
                    .findAllByResourceTypeAndResourceIdAndSourceApiKeyId(ResourceType.FOLDER, folder.getId(), apiKeyId);
            if (perms.isEmpty()) {
                visible.add(folder);
            } else if (admin || isCreator(folder.getCreatedByUserId(), folder.getCreatedByName(), principal)) {
                folder.setRestricted(true);
                visible.add(folder);
            }
        }
        return visible;
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Authorizes permission management on a resource: the resource must belong to the caller's project,
     * and the caller must be its creator or a project admin. Throws {@link ShareAccessDeniedException}
     * otherwise, or a not-found if the resource does not exist in the project.
     */
    @Override
    public SharePermissionEntity.PermissionLevel getEffectivePermission(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal) {
        Long apiKeyId = principal.getApiKeyId();
        String userId = principal.resolveUserId(null);
        boolean admin = isProjectAdmin(apiKeyId, userId);

        Long resourceApiKeyId = null;
        String creatorUserId = null;
        String creatorName = null;
        Long parentFolderId = null;
        String resourceUserId = null;

        if (resourceType == ResourceType.FILE) {
            // Includes trashed files on purpose: restore and purge act on files whose
            // deletedAt is not null, so restricting this lookup to live files made every
            // restore/purge request resolve to a null permission and fail with 403.
            FileEntity file = fileRepository.findById(resourceId).orElse(null);
            if (file == null) {
                return null;
            }
            resourceApiKeyId = file.getApiKeyId();
            creatorUserId = file.getCreatedByUserId();
            creatorName = file.getCreatedByName();
            parentFolderId = file.getFolderId();
            resourceUserId = file.getUserId();
        } else {
            FolderEntity folder = folderRepository.findById(resourceId).orElse(null);
            if (folder == null) {
                return null;
            }
            resourceApiKeyId = folder.getApiKeyId();
            creatorUserId = folder.getCreatedByUserId();
            creatorName = folder.getCreatedByName();
            parentFolderId = folder.getParentId();
            resourceUserId = folder.getUserId();
        }

        boolean sameProject = Objects.equals(resourceApiKeyId, apiKeyId);

        // Project admins have full EDIT permissions on resources in their own project
        if (admin && sameProject) {
            return SharePermissionEntity.PermissionLevel.EDIT;
        }

        // Creator always has full EDIT permissions
        if (isCreator(creatorUserId, creatorName, principal)) {
            return SharePermissionEntity.PermissionLevel.EDIT;
        }

        // Private items: If the viewer is the owner, they have EDIT access
        if (resourceUserId != null && userId != null && resourceUserId.trim().equalsIgnoreCase(userId.trim())) {
            return SharePermissionEntity.PermissionLevel.EDIT;
        }

        // Check direct permissions on the resource itself across all projects
        List<SharePermissionEntity> perms = sharePermissionRepository
                .findAllByResourceTypeAndResourceId(resourceType, resourceId);
        
        SharePermissionEntity.PermissionLevel directLvl = getMatchingPermission(perms, userId, apiKeyId);
        if (directLvl != null) {
            return directLvl;
        }

        // Inherit from parent folder:
        if (parentFolderId != null) {
            return getEffectivePermission(ResourceType.FOLDER, parentFolderId, principal);
        }

        // If it's a public space item (resourceUserId == null) of the same project with no permissions set, default is EDIT
        if (resourceUserId == null && sameProject && perms.isEmpty()) {
            return SharePermissionEntity.PermissionLevel.EDIT;
        }

        return null;
    }

    private SharePermissionEntity.PermissionLevel getMatchingPermission(List<SharePermissionEntity> perms, String userId, Long apiKeyId) {
        LocalDateTime now = LocalDateTime.now();
        SharePermissionEntity.PermissionLevel highest = null;
        for (SharePermissionEntity p : perms) {
            if (p.getExpiresAt() != null && p.getExpiresAt().isBefore(now)) {
                continue;
            }
            boolean match = false;
            if (p.getTargetType() == TargetType.USER && userId != null && userId.equals(p.getTargetUserId())) {
                match = true;
            } else if (p.getTargetType() == TargetType.PROJECT && apiKeyId.equals(p.getTargetApiKeyId())) {
                match = true;
            }
            if (match) {
                if (highest == null || p.getPermissionLevel().ordinal() > highest.ordinal()) {
                    highest = p.getPermissionLevel();
                }
            }
        }
        return highest;
    }

    @Override
    public boolean isOwnerOrAdmin(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal) {
        return isOwnerOrAdmin(resourceType, resourceId, principal, null, null);
    }

    /**
     * Ownership check that accepts the identity carried by the request.
     *
     * <p>A caller authenticated with the classic {@code X-Api-Key} header has no cédula on the
     * principal, and passes it as request parameters instead. Resolving identity only from the
     * principal made every such call look anonymous, so the legitimate author of a file was denied
     * with "no eres el propietario". The explicit values take precedence, exactly as
     * {@code softDeleteFile} and the other service calls already do.
     */
    @Override
    public boolean isOwnerOrAdmin(ResourceType resourceType, Long resourceId,
                                  ApiKeyPrincipal principal, String requestUserId, String requestUserName) {
        Long apiKeyId = principal.getApiKeyId();
        String userId = normalizeIdentity(principal.resolveUserId(requestUserId));
        String userName = normalizeIdentity(principal.resolveUserName(requestUserName));

        if (isProjectAdmin(apiKeyId, userId)) {
            return true;
        }

        String creatorUserId;
        String creatorName;

        if (resourceType == ResourceType.FILE) {
            FileEntity file = fileRepository.findByIdAndApiKeyId(resourceId, apiKeyId).orElse(null);
            if (file == null) {
                // Decentralized "Mis archivos": a private file this caller owns can sit under
                // another project. Restricted to user_id == caller, so shared files never match.
                file = fileRepository.findById(resourceId)
                        .filter(f -> f.getUserId() != null && userId != null
                                && f.getUserId().trim().equalsIgnoreCase(userId.trim()))
                        .orElse(null);
            }
            if (file == null) {
                return false;
            }
            creatorUserId = file.getCreatedByUserId();
            creatorName = file.getCreatedByName();
        } else {
            FolderEntity folder = folderRepository.findByIdAndApiKeyId(resourceId, apiKeyId).orElse(null);
            if (folder == null) {
                // Decentralized "Mis archivos": a private folder this caller owns can sit under
                // another project. Restricted to user_id == caller, so shared folders never match.
                final String owner = userId;
                folder = folderRepository.findById(resourceId)
                        .filter(f -> f.getUserId() != null && owner != null
                                && f.getUserId().trim().equalsIgnoreCase(owner))
                        .orElse(null);
            }
            if (folder == null) {
                return false;
            }
            creatorUserId = folder.getCreatedByUserId();
            creatorName = folder.getCreatedByName();
        }

        return isCreator(creatorUserId, creatorName, userId, userName);
    }

    /**
     * Quién puede repartir el acceso a un recurso.
     *
     * <p>Antes bastaba con tener nivel EDIT, y eso abría un agujero: un archivo sin restricciones
     * resuelve EDIT para <b>cualquier</b> integrante del proyecto, así que cualquiera podía
     * re-compartir lo ajeno. Ahora reparte el acceso quien lo creó, un administrador del proyecto,
     * o un destinatario a quien le delegaron explícitamente esa facultad con {@code canShare}.
     *
     * <p>El nivel de permiso y la facultad de re-compartir son cosas distintas: EDIT dice qué puede
     * hacerle al documento, {@code canShare} dice si puede dárselo a alguien más.
     */
    private void assertCanManage(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal) {
        if (isOwnerOrAdmin(resourceType, resourceId, principal)) {
            return;
        }
        if (hasDelegatedShare(resourceType, resourceId, principal)) {
            return;
        }
        throw new ShareAccessDeniedException(
                "No tienes permisos para compartir este recurso. Solo su autor, un administrador del "
                        + "proyecto o alguien con la facultad de re-compartir puede hacerlo.");
    }

    /** Si a esta persona le compartieron el recurso permitiéndole volver a compartirlo. */
    private boolean hasDelegatedShare(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal) {
        String userId = normalizeIdentity(principal.resolveUserId(null));
        if (userId == null || resourceId == null) {
            return false;
        }
        return sharePermissionRepository
                .findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
                        resourceType, resourceId, TargetType.USER, userId)
                .stream()
                .anyMatch(SharePermissionEntity::canReshare);
    }

    /**
     * Creator match. Primary (strong) signal is {@code createdByUserId} (the creator's cédula, set on
     * every new resource). Legacy resources created before that column existed have it null, so we fall
     * back to the creator's display name (createdByName == JWT name).
     */
    private boolean isCreator(String creatorUserId, String creatorName, ApiKeyPrincipal principal) {
        return isCreator(creatorUserId, creatorName,
                normalizeIdentity(principal.resolveUserId(null)),
                normalizeIdentity(principal.resolveUserName(null)));
    }

    /**
     * Same rule as above but on identity already resolved by the caller, so a request that carries
     * its cédula as a parameter is judged on that value instead of on an empty principal.
     */
    private boolean isCreator(String creatorUserId, String creatorName,
                              String viewerUserId, String viewerName) {
        String storedUserId = normalizeIdentity(creatorUserId);

        // Strong signal: both sides carry an identifier, so it decides in both directions.
        // A mismatch here must NOT fall through to the display-name check — that fallback is
        // weaker and would let a namesake claim authorship over the stored identifier.
        if (storedUserId != null && viewerUserId != null) {
            return storedUserId.equalsIgnoreCase(viewerUserId);
        }

        // Fallback, reached only when one of the two identifiers is missing: rows created before
        // created_by_user_id existed, and callers that authenticated without an identifier.
        // Comparing on the display name is the best signal available for those.
        String storedName = normalizeIdentity(creatorName);
        return storedName != null && viewerName != null && storedName.equalsIgnoreCase(viewerName);
    }

    /**
     * Trims surrounding whitespace and collapses blank values to {@code null}.
     *
     * <p>Identity reaches the database raw — {@code created_by_user_id} is written straight from
     * the token claim — so a stray space on either side would otherwise turn a legitimate author
     * into a 403. Comparisons on the normalized value are case-insensitive.
     */
    private static String normalizeIdentity(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isProjectAdmin(Long apiKeyId, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return knownUserRepository.findByApiKeyIdAndUserId(apiKeyId, userId)
                .map(user -> ROLE_ADMIN.equalsIgnoreCase(user.getRole()))
                .orElse(false);
    }

    private boolean isVisible(
            List<SharePermissionEntity> perms, String creatorUserId, String creatorName,
            String viewerUserId, String viewerName, Long apiKeyId, boolean admin) {
        if (perms.isEmpty()) {
            return true; // No restrictions: visible to everyone in the project, exactly as today.
        }
        if (admin) {
            return true;
        }
        // Creator check: strong match by createdByUserId, fallback to name for legacy resources.
        if (creatorUserId != null && viewerUserId != null && creatorUserId.equals(viewerUserId)) {
            return true;
        }
        if (creatorName != null && viewerName != null && creatorName.equals(viewerName)) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SharePermissionEntity permission : perms) {
            if (permission.getExpiresAt() != null && permission.getExpiresAt().isBefore(now)) {
                continue;
            }
            if (permission.getTargetType() == TargetType.USER
                    && viewerUserId != null && viewerUserId.equals(permission.getTargetUserId())) {
                return true;
            }
            if (permission.getTargetType() == TargetType.PROJECT
                    && apiKeyId.equals(permission.getTargetApiKeyId())) {
                return true;
            }
        }
        return false;
    }

    private List<SharedResourceResponse> resolveShared(List<SharePermissionEntity> perms) {
        LocalDateTime now = LocalDateTime.now();
        List<SharedResourceResponse> out = new ArrayList<>();
        for (SharePermissionEntity permission : perms) {
            if (permission.getExpiresAt() != null && permission.getExpiresAt().isBefore(now)) {
                continue;
            }
            String resourceName;
            String mimeType;
            if (permission.getResourceType() == ResourceType.FILE) {
                FileEntity file = fileRepository
                        .findByIdAndApiKeyIdAndDeletedAtIsNull(permission.getResourceId(), permission.getSourceApiKeyId())
                        .orElse(null);
                if (file == null) {
                    continue;
                }
                resourceName = file.getOriginalFileName();
                mimeType = file.getMimeType();
            } else {
                FolderEntity folder = folderRepository
                        .findByIdAndApiKeyId(permission.getResourceId(), permission.getSourceApiKeyId())
                        .orElse(null);
                if (folder == null) {
                    continue;
                }
                resourceName = folder.getName();
                mimeType = null;
            }
            String sourceProjectName = apiKeyRepository.findById(permission.getSourceApiKeyId())
                    .map(ApiKeyEntity::getName)
                    .orElse(null);
            out.add(new SharedResourceResponse(
                    permission.getId(),
                    permission.getResourceType(),
                    permission.getResourceId(),
                    resourceName,
                    mimeType,
                    permission.getPermissionLevel(),
                    permission.getSourceApiKeyId(),
                    sourceProjectName,
                    permission.getSharedByUserId(),
                    permission.getSharedByName(),
                    permission.getNotes(),
                    permission.getCreatedAt(),
                    // El gestor lo usa para mostrar u ocultar el botón de compartir: sin este dato
                    // ofrecería una acción que el backend va a rechazar.
                    permission.canReshare(),
                    permission.getExpiresAt()));
        }
        return out;
    }

    private SharePermissionResponse toPermissionResponse(SharePermissionEntity entity) {
        return new SharePermissionResponse(
                entity.getId(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getSourceApiKeyId(),
                entity.getTargetType(),
                entity.getTargetUserId(),
                entity.getTargetApiKeyId(),
                entity.getPermissionLevel(),
                entity.getSharedByUserId(),
                entity.getSharedByName(),
                entity.canReshare(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getExpiresAt());
    }

}
