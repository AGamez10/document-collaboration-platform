package com.officeplatform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;

@Repository
public interface SharePermissionRepository extends JpaRepository<SharePermissionEntity, Long> {

    /** Permissions attached to a specific resource within its source project. */
    List<SharePermissionEntity> findAllByResourceTypeAndResourceIdAndSourceApiKeyId(
            ResourceType resourceType, Long resourceId, Long sourceApiKeyId);

    /** All resources shared directly WITH a specific user (by cédula). */
    List<SharePermissionEntity> findAllByTargetTypeAndTargetUserId(TargetType targetType, String targetUserId);

    /** All resources shared WITH a specific project (by target API key id). */
    List<SharePermissionEntity> findAllByTargetTypeAndTargetApiKeyId(TargetType targetType, Long targetApiKeyId);

}
