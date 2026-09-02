package com.officeplatform.service.folder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.exception.FolderNotFoundException;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FolderServiceImpl implements FolderService {

    private static final int MAX_FOLDER_NAME_LENGTH = 255;

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final ActivityLogRecorder activityLogRecorder;
    private final StorageService storageService;

    public FolderServiceImpl(
            FolderRepository folderRepository, FileRepository fileRepository,
            ActivityLogRecorder activityLogRecorder, StorageService storageService) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.activityLogRecorder = activityLogRecorder;
        this.storageService = storageService;
    }

    private String resolveEffectiveUserId(String userId, Long apiKeyId) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return "key_" + apiKeyId;
    }

    @Override
    @Transactional
    public FolderEntity createFolder(String name, Long parentId, Long apiKeyId, String userId, String userName, String scope) {
        log.info("[FOLDER-SVC] createFolder name={}, scope={}, userId={}, apiKeyId={}, parentId={}",
                name, scope, userId, apiKeyId, parentId);
        String trimmedName = validateName(name);

        String folderUserId = null;
        if (parentId != null) {
            FolderEntity parent = folderRepository.findByIdAndApiKeyId(parentId, apiKeyId)
                    .orElseThrow(() -> new FolderNotFoundException(parentId));
            folderUserId = parent.getUserId();
        } else {
            if ("private".equalsIgnoreCase(scope)) {
                if (userId == null || userId.isBlank()) {
                    throw new StorageException("Se requiere una identidad de usuario autenticado para crear carpetas en Mis Archivos.");
                }
                folderUserId = userId.trim();
            }
        }

        String effectiveUserName = (userName != null && !userName.isBlank()) ? userName : "Usuario";

        FolderEntity folder = FolderEntity.builder()
                .uuid(UUID.randomUUID().toString())
                .name(trimmedName)
                .parentId(parentId)
                .apiKeyId(apiKeyId)
                .userId(folderUserId)
                .createdByName(effectiveUserName)
                .createdByUserId(userId)
                .updatedByName(effectiveUserName)
                .build();

        FolderEntity saved = folderRepository.save(folder);
        activityLogRecorder.record(apiKeyId, userId, effectiveUserName, ActivityAction.CREATE_FOLDER,
                null, saved.getName(), null, parentId);
        return saved;
    }

    @Override
    @Transactional
    public FolderEntity renameFolder(Long folderId, String newName, Long apiKeyId, String userId, String userName) {
        String trimmedName = validateName(newName);

        FolderEntity folder = folderRepository.findByIdAndApiKeyId(folderId, apiKeyId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));

        String oldName = folder.getName();
        folder.setName(trimmedName);
        folder.setUpdatedByName(userName);
        FolderEntity saved = folderRepository.save(folder);

        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.RENAME_FOLDER,
                null, saved.getName(), "Renombrada de '" + oldName + "' a '" + trimmedName + "'", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteFolder(Long folderId, Long apiKeyId, String userId, String userName) {
        FolderEntity folder = folderRepository.findByIdAndApiKeyId(folderId, apiKeyId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));

        List<FolderEntity> tree = collectFolderTree(folder, apiKeyId);

        int filesMoved = 0;
        for (FolderEntity node : tree) {
            List<FileEntity> files = fileRepository.findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(apiKeyId, node.getId());
            for (FileEntity file : files) {
                file.setDeletedAt(LocalDateTime.now());
            }
            fileRepository.saveAll(files);
            filesMoved += files.size();
        }

        folderRepository.deleteAll(tree);

        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.DELETE_FOLDER,
                null, folder.getName(),
                "Carpeta eliminada. Sub-carpetas eliminadas: " + (tree.size() - 1)
                        + ", archivos movidos a la papelera: " + filesMoved,
                folder.getId());
    }

    @Override
    public List<FolderEntity> listFolders(Long parentId, Long apiKeyId, String userId, String scope) {
        log.info("[FOLDER-SVC] listFolders scope={}, userId={}, apiKeyId={}, parentId={}",
                scope, userId, apiKeyId, parentId);
        // "Compartidos" is authoritative and stays isolated per project: ALWAYS user_id IS NULL.
        // Evaluated before the parent lookup so shared navigation keeps its existing behaviour.
        if ("shared".equalsIgnoreCase(scope)) {
            if (parentId != null) {
                folderRepository.findByIdAndApiKeyId(parentId, apiKeyId)
                        .orElseThrow(() -> new FolderNotFoundException(parentId));
                return folderRepository.findAllByApiKeyIdAndParentId(apiKeyId, parentId);
            }
            return folderRepository.findAllByApiKeyIdAndParentIdIsNullAndUserIdIsNull(apiKeyId);
        }

        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        String owner = userId.trim();

        // "Mis archivos" is decentralized: private folders follow the person, so navigation is
        // keyed on the cédula alone. The parent is not validated against apiKeyId any more —
        // doing so would reject a folder this same user created from another application.
        if (parentId != null) {
            return folderRepository.findAllByUserIdAndParentId(owner, parentId);
        }
        return folderRepository.findAllByUserIdAndParentIdIsNull(owner);
    }

    @Override
    public List<FolderEntity> searchFolders(Long apiKeyId, String term, String userId, String scope) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        String query = term.trim();
        if (userId == null || userId.isBlank()) {
            return folderRepository.findAllByApiKeyIdAndNameContainingIgnoreCase(apiKeyId, query);
        }
        if ("private".equalsIgnoreCase(scope)) {
            return folderRepository.findAllByApiKeyIdAndUserIdAndNameContainingIgnoreCase(apiKeyId, userId, query);
        }
        return folderRepository.findAllByApiKeyIdAndUserIdIsNullAndNameContainingIgnoreCase(apiKeyId, query);
    }

    @Override
    @Transactional
    public FileEntity moveFile(Long fileId, Long folderId, Long apiKeyId, String userId, String userName, String scope) {
        FileEntity file = fileRepository.findByIdAndApiKeyIdAndDeletedAtIsNull(fileId, apiKeyId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        if (folderId != null) {
            FolderEntity folder = folderRepository.findByIdAndApiKeyId(folderId, apiKeyId)
                    .orElseThrow(() -> new FolderNotFoundException(folderId));
            file.setUserId(folder.getUserId());
        } else {
            if ("shared".equalsIgnoreCase(scope)) {
                file.setUserId(null);
            } else if ("private".equalsIgnoreCase(scope) && userId != null && !userId.isBlank()) {
                file.setUserId(userId);
            }
        }

        Long previousFolderId = file.getFolderId();
        file.setFolderId(folderId);
        FileEntity saved = fileRepository.save(file);

        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.MOVE_FILE,
                saved.getId(), saved.getOriginalFileName(),
                "Movido de carpeta " + previousFolderId + " a " + folderId, folderId);
        return saved;
    }

    @Override
    public FolderEntity getFolder(Long folderId, Long apiKeyId) {
        return folderRepository.findByIdAndApiKeyId(folderId, apiKeyId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
    }

    @Override
    public byte[] downloadFolderAsZip(Long folderId, Long apiKeyId, String userId, String userName) {
        FolderEntity root = getFolder(folderId, apiKeyId);
        List<FolderEntity> tree = collectFolderTree(root, apiKeyId);

        Map<Long, String> pathByFolderId = new HashMap<>();
        pathByFolderId.put(root.getId(), root.getName());
        for (FolderEntity node : tree) {
            if (node.getId().equals(root.getId())) continue;
            pathByFolderId.computeIfAbsent(node.getId(),
                    id -> pathByFolderId.get(node.getParentId()) + "/" + node.getName());
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Set<String> usedEntryNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (FolderEntity node : tree) {
                String folderPath = pathByFolderId.get(node.getId());
                List<FileEntity> files =
                        fileRepository.findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(apiKeyId, node.getId());
                for (FileEntity file : files) {
                    String entryName = uniqueEntryName(usedEntryNames, folderPath + "/" + file.getOriginalFileName());
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (InputStream content = storageService.retrieve(file.getObjectName())) {
                        content.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el ZIP de la carpeta", e);
        }

        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.DOWNLOAD_FOLDER,
                null, root.getName(), "Carpeta descargada como ZIP", root.getId());

        return buffer.toByteArray();
    }

    @Override
    @Transactional
    public FolderEntity moveFolder(Long folderId, Long newParentId, Long apiKeyId, String userId, String userName, String scope) {
        FolderEntity folder = getFolder(folderId, apiKeyId);

        if (newParentId != null) {
            if (newParentId.equals(folderId)) {
                throw new StorageException("No se puede mover una carpeta dentro de sí misma");
            }
            FolderEntity newParent = folderRepository.findByIdAndApiKeyId(newParentId, apiKeyId)
                    .orElseThrow(() -> new FolderNotFoundException(newParentId));
            if (isSelfOrAncestor(newParent, folderId, apiKeyId)) {
                throw new StorageException("No se puede mover una carpeta dentro de una de sus propias sub-carpetas");
            }
        }

        Long previousParentId = folder.getParentId();
        folder.setParentId(newParentId);

        String newUserId = folder.getUserId(); // default: keep current
        if (newParentId != null) {
            FolderEntity parent = folderRepository.findByIdAndApiKeyId(newParentId, apiKeyId)
                    .orElseThrow(() -> new FolderNotFoundException(newParentId));
            newUserId = parent.getUserId();
        } else {
            if ("shared".equalsIgnoreCase(scope)) {
                newUserId = null;
            } else if ("private".equalsIgnoreCase(scope) && userId != null && !userId.isBlank()) {
                newUserId = userId;
            }
        }

        propagateUserId(folder, newUserId, apiKeyId);
        FolderEntity saved = folderRepository.save(folder);

        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.MOVE_FOLDER,
                null, saved.getName(), "Movida de carpeta " + previousParentId + " a " + newParentId, newParentId);
        return saved;
    }

    private void propagateUserId(FolderEntity folder, String newUserId, Long apiKeyId) {
        folder.setUserId(newUserId);
        folderRepository.save(folder);

        List<FileEntity> files = fileRepository.findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(apiKeyId, folder.getId());
        for (FileEntity file : files) {
            file.setUserId(newUserId);
        }
        fileRepository.saveAll(files);

        List<FolderEntity> children = folderRepository.findAllByApiKeyIdAndParentId(apiKeyId, folder.getId());
        for (FolderEntity child : children) {
            propagateUserId(child, newUserId, apiKeyId);
        }
    }

    /** True if walking up {@code candidate}'s ancestor chain reaches {@code folderId} (or is it). */
    private boolean isSelfOrAncestor(FolderEntity candidate, Long folderId, Long apiKeyId) {
        FolderEntity current = candidate;
        while (current != null) {
            if (current.getId().equals(folderId)) {
                return true;
            }
            if (current.getParentId() == null) {
                return false;
            }
            current = folderRepository.findByIdAndApiKeyId(current.getParentId(), apiKeyId).orElse(null);
        }
        return false;
    }

    private String uniqueEntryName(Set<String> usedEntryNames, String candidate) {
        String result = candidate;
        int suffix = 1;
        while (!usedEntryNames.add(result)) {
            int dot = candidate.lastIndexOf('.');
            String base = dot > 0 ? candidate.substring(0, dot) : candidate;
            String ext = dot > 0 ? candidate.substring(dot) : "";
            result = base + " (" + suffix + ")" + ext;
            suffix++;
        }
        return result;
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new StorageException("El nombre de la carpeta no puede estar vacío");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_FOLDER_NAME_LENGTH) {
            throw new StorageException(
                    "El nombre de la carpeta no puede exceder " + MAX_FOLDER_NAME_LENGTH + " caracteres");
        }
        return trimmed;
    }

    /** Breadth-first collection of a folder and all of its descendants (root included). */
    private List<FolderEntity> collectFolderTree(FolderEntity root, Long apiKeyId) {
        List<FolderEntity> tree = new ArrayList<>();
        Deque<FolderEntity> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            FolderEntity current = queue.poll();
            tree.add(current);
            queue.addAll(folderRepository.findAllByApiKeyIdAndParentId(apiKeyId, current.getId()));
        }

        return tree;
    }

}
