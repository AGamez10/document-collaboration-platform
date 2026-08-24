package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import com.officeplatform.entity.SharePermissionEntity.PermissionLevel;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharePermissionResponse {

    private Long id;

    private ResourceType resourceType;

    private Long resourceId;

    private Long sourceApiKeyId;

    private TargetType targetType;

    private String targetUserId;

    private Long targetApiKeyId;

    private PermissionLevel permissionLevel;

    private String sharedByUserId;

    private String sharedByName;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

}
