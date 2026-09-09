package com.officeplatform.service.folder;

import java.util.List;

import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;

public interface FolderService {

    FolderEntity createFolder(String name, Long parentId, Long apiKeyId, String userId, String userName, String scope);

    FolderEntity renameFolder(Long folderId, String newName, Long apiKeyId, String userId, String userName);

    /**
     * Deletes a folder and all of its descendant sub-folders. Any files found inside the
     * deleted tree are soft-deleted (moved to trash) rather than purged, consistent with
     * how individual file deletion already works.
     */
    void deleteFolder(Long folderId, Long apiKeyId, String userId, String userName);

    /** Devuelve una carpeta y su subarbol desde la papelera a su ubicacion anterior. */
    FolderEntity restoreFolder(Long folderId, Long apiKeyId, String userId, String userName);

    /** Elimina definitivamente una carpeta de la papelera, con su subarbol y sus binarios. */
    void purgeFolder(Long folderId, Long apiKeyId, String userId, String userName);

    List<FolderEntity> listFolders(Long parentId, Long apiKeyId, String userId, String scope);

    /** Searches folders by name (LIKE %term%) across all parents, scoped to private or shared. */
    List<FolderEntity> searchFolders(Long apiKeyId, String term, String userId, String scope);

    FileEntity moveFile(Long fileId, Long folderId, Long apiKeyId, String userId, String userName, String scope);

    FolderEntity getFolder(Long folderId, Long apiKeyId);

    /**
     * Recursively collects every file inside the folder (and its sub-folders, at any depth)
     * and packages them into a ZIP that mirrors the original folder structure.
     */
    byte[] downloadFolderAsZip(Long folderId, Long apiKeyId, String userId, String userName);

    /**
     * Moves a folder under a new parent (null = root). Rejects moving a folder into itself
     * or into one of its own descendants, which would otherwise create a cycle.
     */
    FolderEntity moveFolder(Long folderId, Long newParentId, Long apiKeyId, String userId, String userName, String scope);

}
