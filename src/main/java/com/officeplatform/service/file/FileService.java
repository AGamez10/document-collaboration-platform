package com.officeplatform.service.file;

import java.io.InputStream;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.officeplatform.dto.request.UploadFileRequest;
import com.officeplatform.entity.FileEntity;

public interface FileService {

    List<FileEntity> listFiles(Long apiKeyId, Long folderId, String userId, String scope);

    /** All active files for the key, regardless of folder. Backs cross-folder views like "recent". */
    List<FileEntity> listAllFiles(Long apiKeyId, String userId, String scope);

    List<FileEntity> listTrash(Long apiKeyId, String userId, String scope);

    /**
     * Trash of the calling user, resolved by ownership instead of by scope.
     *
     * @param userName display name, used to match legacy rows that predate created_by_user_id
     */
    List<FileEntity> listTrash(Long apiKeyId, String userId, String userName, String scope);

    /** Searches active files by name (LIKE %term%) across all folders, scoped to private or shared. */
    List<FileEntity> searchFiles(Long apiKeyId, String term, String userId, String scope);

    FileEntity uploadFile(
            MultipartFile file, UploadFileRequest request, Long apiKeyId, Long folderId, String userId, String userName, String scope);

    FileEntity createBlankFile(Long apiKeyId, Long folderId, String userId, String userName, String scope);

    FileEntity createBlankSpreadsheet(Long apiKeyId, Long folderId, String userId, String userName, String scope);

    FileEntity createBlankPresentation(Long apiKeyId, Long folderId, String userId, String userName, String scope);

    FileEntity getFile(Long fileId, Long apiKeyId);

    /**
     * Lookup that also matches trashed files, so callers can authorize operations on the
     * trash (restore, purge) and still get a 404 — not a 403 — for a file that is simply gone.
     *
     * @throws com.officeplatform.exception.FileNotFoundException if no such file exists in the tenant
     */
    FileEntity getFileIncludingTrashed(Long fileId, Long apiKeyId);

    FileEntity renameFile(Long fileId, Long apiKeyId, String newName, String userId, String userName);

    void softDeleteFile(Long fileId, Long apiKeyId, String userId, String userName);

    FileEntity restoreFile(Long fileId, Long apiKeyId, String userId, String userName);

    void purgeFile(Long fileId, Long apiKeyId, String userId, String userName);

    InputStream downloadFile(Long fileId, Long apiKeyId, String userId, String userName);

    FileEntity getFileByUuid(String uuid);

    InputStream downloadByUuid(String uuid);

}
