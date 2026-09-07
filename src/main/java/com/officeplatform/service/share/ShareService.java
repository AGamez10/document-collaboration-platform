package com.officeplatform.service.share;

import java.util.List;

import com.officeplatform.dto.request.ShareRequest;
import com.officeplatform.dto.response.FileResponse;
import com.officeplatform.dto.response.FolderResponse;
import com.officeplatform.dto.response.SharePermissionResponse;
import com.officeplatform.dto.response.SharedResourceResponse;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.security.model.ApiKeyPrincipal;

public interface ShareService {

    /** Shares a resource with a user or project. Upserts if a grant to the same target already exists. */
    SharePermissionResponse share(ShareRequest request, ApiKeyPrincipal principal);

    /** Lists the permissions attached to a resource (creator/admin only). */
    List<SharePermissionResponse> listResourcePermissions(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal);

    /** Revokes a permission by id (creator/admin only, and only within the caller's project). */
    void revoke(Long permissionId, ApiKeyPrincipal principal);

    /** Resources shared directly with the calling user. */
    List<SharedResourceResponse> sharedWithMe(ApiKeyPrincipal principal);

    /** Resources shared with the calling user's project. */
    List<SharedResourceResponse> sharedWithProject(ApiKeyPrincipal principal);

    /**
     * Additive visibility filter applied AFTER the existing "Compartidos" listing (does not touch the
     * listing queries). Keeps a file if it has no permission rows (visible to all, as today) or if the
     * caller is the creator, a project admin, or an explicit target.
     */
    List<FileResponse> filterFilesByPermissions(List<FileResponse> files, ApiKeyPrincipal principal);

    List<FolderResponse> filterFoldersByPermissions(List<FolderResponse> folders, ApiKeyPrincipal principal);

    com.officeplatform.entity.SharePermissionEntity.PermissionLevel getEffectivePermission(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal);

    /**
     * Whether the caller owns the resource or administers the project.
     *
     * <p>Stricter than {@link #getEffectivePermission}: an unrestricted resource in the shared
     * space grants EDIT to every member of the project, which is the right rule for editing but
     * the wrong one for destructive operations. Deleting, restoring and purging must stay with
     * the author or a project admin.
     *
     * @return {@code true} when the caller created the resource or holds the admin role;
     *         {@code false} when the resource does not exist or belongs to somebody else
     */
    boolean isOwnerOrAdmin(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal);

    /**
     * Same check, judged on the identity carried by the request.
     *
     * <p>Callers authenticated with the classic {@code X-Api-Key} header have no cédula on the
     * principal and pass it as request parameters; resolving identity only from the principal
     * denied those callers even when they were the author.
     *
     * @param requestUserId   cédula supplied by the request, or {@code null} to use the principal
     * @param requestUserName display name supplied by the request, or {@code null}
     */
    boolean isOwnerOrAdmin(ResourceType resourceType, Long resourceId, ApiKeyPrincipal principal,
                           String requestUserId, String requestUserName);

}
