package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import com.officeplatform.entity.SharePermissionEntity.PermissionLevel;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A resource made visible to the caller through a share permission (cross-project or cross-user).
 * It is a REFERENCE: the resource still lives in {@code sourceApiKeyId}'s project, not a copy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedResourceResponse {

    private Long permissionId;

    private ResourceType resourceType;

    private Long resourceId;

    private String resourceName;

    /** MIME type for files; null for folders. */
    private String mimeType;

    private PermissionLevel permissionLevel;

    /** Owner project where the resource actually lives. */
    private Long sourceApiKeyId;

    /** Human-readable name of the owner project (resolved from sourceApiKeyId). */
    private String sourceProjectName;

    private String sharedByUserId;

    private String sharedByName;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

}
