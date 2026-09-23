package com.officeplatform.service.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.officeplatform.dto.request.UploadFileRequest;
import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.exception.FolderNotFoundException;
import com.officeplatform.exception.StorageException;
import com.officeplatform.exception.StorageQuotaExceededException;
import com.officeplatform.exception.UnsupportedFileTypeException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.service.search.FileIndexingService;
import com.officeplatform.util.DocxUtils;
import com.officeplatform.util.FileUtils;
import com.officeplatform.util.MimeUtils;
import com.officeplatform.util.PptxUtils;
import com.officeplatform.util.XlsxUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    private static final String BLANK_DOCUMENT_NAME = "Nuevo documento.docx";
    private static final String BLANK_DOCUMENT_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String BLANK_DOCUMENT_EXTENSION = "docx";

    private static final String BLANK_SPREADSHEET_NAME = "Nueva hoja de cálculo.xlsx";
    private static final String BLANK_SPREADSHEET_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String BLANK_SPREADSHEET_EXTENSION = "xlsx";

    private static final String BLANK_PRESENTATION_NAME = "Nueva presentación.pptx";
    private static final String BLANK_PRESENTATION_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String BLANK_PRESENTATION_EXTENSION = "pptx";

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final FileRepository fileRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final FolderRepository folderRepository;
    private final SharePermissionRepository sharePermissionRepository;
    private final StorageService storageService;
    private final FileIndexingService fileIndexingService;
    private final ActivityLogRecorder activityLogRecorder;
    private final String bucket;
    private final List<String> allowedMimeTypes;

    public FileServiceImpl(
            FileRepository fileRepository,
            ApiKeyRepository apiKeyRepository,
            FolderRepository folderRepository,
            SharePermissionRepository sharePermissionRepository,
            StorageService storageService,
            FileIndexingService fileIndexingService,
            ActivityLogRecorder activityLogRecorder,
            @Value("${office-platform.storage.minio.bucket}") String bucket,
            @Value("${office-platform.storage.allowed-mime-types}") String allowedMimeTypesRaw) {
        this.fileRepository = fileRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.folderRepository = folderRepository;
        this.sharePermissionRepository = sharePermissionRepository;
        this.storageService = storageService;
        this.fileIndexingService = fileIndexingService;
        this.activityLogRecorder = activityLogRecorder;
        this.bucket = bucket;
        this.allowedMimeTypes = Arrays.asList(allowedMimeTypesRaw.split(","));
    }

    /**
     * Corta la subida si el proyecto ya no tiene espacio.
     *
     * <p>Un proyecto sin cuota configurada no tiene tope, que es como funcionaron todos hasta
     * ahora. Se mide contra lo que esta vivo: la papelera no cuenta, porque cobrar bytes que
     * alguien ya decidio borrar dejaria a la gente sin poder subir hasta que otro vacie la
     * papelera, un castigo raro por haber ordenado.
     *
     * <p>Se valida ANTES de escribir en el almacenamiento. Al reves, un archivo rechazado dejaria
     * su binario huerfano en MinIO ocupando la cuota que se acaba de negar.
     */
    private void assertQuotaAllows(Long apiKeyId, long incomingSize) {
        Long quota = apiKeyRepository.findById(apiKeyId)
                .map(ApiKeyEntity::getStorageQuotaBytes)
                .orElse(null);
        if (quota == null || quota <= 0) {
            return;
        }
        long used = fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(apiKeyId);
        if (used + incomingSize > quota) {
            throw new StorageQuotaExceededException(
                    "Cuota de almacenamiento excedida para este proyecto. Contacte al administrador. "
                            + "Usado: " + FileUtils.formatBytes(used)
                            + " de " + FileUtils.formatBytes(quota)
                            + "; este archivo pesa " + FileUtils.formatBytes(incomingSize) + ".");
        }
    }

    private String resolveEffectiveUserId(String userId, Long apiKeyId) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return "key_" + apiKeyId;
    }

    @Override
    public List<FileEntity> listFiles(Long apiKeyId, Long folderId, String userId, String scope) {
        log.info("[FILE-SVC] listFiles scope={}, userId={}, apiKeyId={}, folderId={}",
                scope, userId, apiKeyId, folderId);
        // "Compartidos" stays isolated per project and is evaluated first, so a caller without a
        // cédula can never fall through into somebody else's private files.
        if ("shared".equalsIgnoreCase(scope)) {
            if (folderId != null) {
                FolderEntity folder = folderRepository.findById(folderId).orElse(null);
                Long targetApiKey = (folder != null) ? folder.getApiKeyId() : apiKeyId;
                return fileRepository.findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(targetApiKey, folderId);
            }
            return fileRepository.findAllByApiKeyIdAndFolderIdIsNullAndUserIdIsNullAndDeletedAtIsNull(apiKeyId);
        }

        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        String owner = userId.trim();

        // "Mis archivos" is decentralized: private files follow the person, so the listing is
        // keyed on the cédula alone and the same files show up from every consumer application.
        if (folderId != null) {
            return fileRepository.findAllByUserIdAndFolderIdAndDeletedAtIsNull(owner, folderId);
        }
        return fileRepository.findAllByUserIdAndFolderIdIsNullAndDeletedAtIsNull(owner);
    }

    @Override
    public List<FileEntity> listAllFiles(Long apiKeyId, String userId, String scope) {
        if ("shared".equalsIgnoreCase(scope)) {
            return fileRepository.findAllByApiKeyIdAndUserIdIsNullAndDeletedAtIsNull(apiKeyId);
        }
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        // Decentralized, same rule as listFiles: "Recientes" over my own files from every project.
        return fileRepository.findAllByUserIdAndDeletedAtIsNull(userId.trim());
    }

    /**
     * Trash of the calling user, resolved by ownership instead of by scope.
     *
     * <p>Scope-based filtering broke this listing: a shared file is stored with
     * {@code user_id = null}, so once its author trashed it, the private trash
     * ({@code user_id = cédula}) no longer matched it while the shared trash
     * ({@code user_id IS NULL}) showed it to the whole project. Ownership is the correct
     * criterion — a trashed file belongs in its author's trash and nobody else's.
     *
     * <p>{@code createdByUserId} is the strong match. Rows created before that column existed
     * carry only {@code createdByName}, so those are matched by display name, and legacy private
     * rows are still matched by {@code user_id} to avoid losing anything already in the trash.
     * The {@code scope} parameter is kept for API compatibility and no longer filters.
     */
    @Override
    public List<FileEntity> listTrash(Long apiKeyId, String userId, String scope) {
        return listTrash(apiKeyId, userId, null, scope);
    }

    @Override
    public List<FileEntity> listTrash(Long apiKeyId, String userId, String userName, String scope) {
        String resolvedUserId = (userId != null && !userId.isBlank()) ? userId.trim() : null;
        String resolvedUserName = (userName != null && !userName.isBlank()) ? userName.trim() : null;

        if (resolvedUserId == null && resolvedUserName == null) {
            return List.of();
        }

        Map<Long, FileEntity> byId = new LinkedHashMap<>();

        // Decentralized like "Mis archivos": the trash follows the person across projects, so
        // apiKeyId is not part of the filter. Ownership still is — the queries below only ever
        // match rows this user authored or owns.
        // Las tres consultas excluyen lo que esta persona ya vacio: para ella el archivo dejo de
        // existir. La fila sigue viva y visible en el panel de administracion, que es justamente
        // lo que permite auditarlo o devolverselo si se arrepiente.
        if (resolvedUserId != null) {
            fileRepository.findAllByCreatedByUserIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(resolvedUserId)
                    .forEach(file -> byId.put(file.getId(), file));
            // Legacy private rows: owned through user_id, created before created_by_user_id existed.
            fileRepository.findAllByUserIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(resolvedUserId)
                    .forEach(file -> byId.putIfAbsent(file.getId(), file));
        }

        if (resolvedUserName != null) {
            fileRepository
                    .findAllByCreatedByUserIdIsNullAndCreatedByNameAndDeletedAtIsNotNullAndUserPurgedAtIsNull(
                            resolvedUserName)
                    .forEach(file -> byId.putIfAbsent(file.getId(), file));
        }

        return byId.values().stream()
                .sorted(Comparator.comparing(
                        FileEntity::getDeletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public List<FileEntity> searchFiles(Long apiKeyId, String term, String userId, String scope) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        String query = term.trim();
        String resolvedUserId = (userId != null && !userId.isBlank()) ? userId.trim() : null;

        // Busca por nombre Y por contenido: un procedimiento que nadie recuerda como se llama se
        // encuentra por una frase que si recuerda haber leido adentro.
        if ("shared".equalsIgnoreCase(scope)) {
            return fileRepository.searchSharedByNameOrContent(apiKeyId, query);
        }
        if ("private".equalsIgnoreCase(scope)) {
            return resolvedUserId == null
                    ? List.of()
                    : fileRepository.searchByNameOrContentForUser(apiKeyId, resolvedUserId, query);
        }

        // Sin scope explicito la busqueda abarca todo lo accesible. Antes caia en "lo privado de
        // quien busca", asi que un documento del area no aparecia hasta cambiar de pestaña: para
        // encontrarlo habia que saber de antemano donde estaba guardado, que es justo lo que uno
        // no sabe cuando lo busca.
        if (resolvedUserId == null) {
            // Un llamador sin identidad no tiene espacio privado que mirar; lo compartido, si.
            return fileRepository.searchSharedByNameOrContent(apiKeyId, query);
        }
        return fileRepository.searchAccessibleByNameOrContent(apiKeyId, resolvedUserId, query);
    }

    @Override
    public FileEntity uploadFile(
            MultipartFile file, UploadFileRequest request, Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("El archivo está vacío o no fue enviado");
        }

        String contentType = file.getContentType();
        if (!MimeUtils.isAllowed(contentType, allowedMimeTypes)) {
            throw new UnsupportedFileTypeException("Tipo de archivo no permitido: " + contentType);
        }
        // The declared content type is chosen by the client, so on its own it validates nothing:
        // an executable sent as application/pdf used to be accepted. The extension must agree.
        if (!MimeUtils.matchesExtension(request.getOriginalFileName(), contentType)) {
            throw new UnsupportedFileTypeException(
                    "La extensión del archivo no corresponde al tipo declarado (" + contentType + ")");
        }

        assertQuotaAllows(apiKeyId, file.getSize());

        String uuid = UUID.randomUUID().toString();
        String extension = FileUtils.extractExtension(request.getOriginalFileName());
        String storedFileName = FileUtils.generateStoredFileName(uuid, extension);

        try (InputStream inputStream = file.getInputStream()) {
            storageService.store(storedFileName, inputStream, file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("No se pudo leer el archivo subido", e);
        }

        String fileUserId = null;
        Long effectiveApiKeyId = apiKeyId;
        if (folderId != null) {
            FolderEntity parent = resolveContainingFolder(folderId);
            fileUserId = parent.getUserId();
            effectiveApiKeyId = parent.getApiKeyId();
        } else {
            if ("private".equalsIgnoreCase(scope)) {
                if (userId == null || userId.isBlank()) {
                    throw new StorageException("Se requiere una identidad de usuario autenticado para subir archivos a Mis Archivos.");
                }
                fileUserId = userId.trim();
            }
        }

        String effectiveUserName = (userName != null && !userName.isBlank()) ? userName : "Usuario";

        FileEntity fileEntity = FileEntity.builder()
            .uuid(uuid)
            .fileName(storedFileName)
            .originalFileName(request.getOriginalFileName())
            .bucket(bucket)
            .objectName(storedFileName)
            .mimeType(contentType)
            .extension(extension)
            .size(file.getSize())
            .apiKeyId(effectiveApiKeyId)
            .folderId(folderId)
            .userId(fileUserId)
            .createdByName(effectiveUserName)
            .createdByUserId(userId)
            .updatedByName(effectiveUserName)
            .build();

        FileEntity saved = fileRepository.save(fileEntity);

        // Se indexa despues de tener la fila y fuera del camino de la subida: leer un documento
        // de varios megabytes tarda, y quien acaba de subirlo no tiene por que esperar a que se
        // vuelva buscable para ver que su subida termino.
        fileIndexingService.indexAsync(saved.getId());
        activityLogRecorder.record(apiKeyId, userId, effectiveUserName, ActivityAction.UPLOAD,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
        return saved;
    }

    @Override
    public FileEntity createBlankFile(Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        return createBlank(DocxUtils.emptyDocument(), BLANK_DOCUMENT_NAME, BLANK_DOCUMENT_MIME_TYPE,
                BLANK_DOCUMENT_EXTENSION, apiKeyId, folderId, userId, userName, scope);
    }

    @Override
    public FileEntity createBlankSpreadsheet(Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        return createBlank(XlsxUtils.emptySpreadsheet(), BLANK_SPREADSHEET_NAME, BLANK_SPREADSHEET_MIME_TYPE,
                BLANK_SPREADSHEET_EXTENSION, apiKeyId, folderId, userId, userName, scope);
    }

    @Override
    public FileEntity createBlankPresentation(Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        return createBlank(PptxUtils.emptyPresentation(), BLANK_PRESENTATION_NAME, BLANK_PRESENTATION_MIME_TYPE,
                BLANK_PRESENTATION_EXTENSION, apiKeyId, folderId, userId, userName, scope);
    }

    @Override
    @Transactional
    public FileEntity createFromBytes(byte[] content, String originalFileName, Long apiKeyId,
                                      Long folderId, String userId, String userName, String scope) {
        if (content == null || content.length == 0) {
            throw new StorageException("El archivo '" + originalFileName + "' está vacío.");
        }
        assertQuotaAllows(apiKeyId, content.length);

        String extension = FileUtils.extractExtension(originalFileName);
        String mimeType = MimeUtils.contentTypeForFileName(originalFileName, null);

        FileEntity saved = createBlank(content, originalFileName, mimeType, extension,
                apiKeyId, folderId, userId, userName, scope);
        // Se indexa igual que una subida: si no, lo que entra por acá no se encuentra por su texto
        // y nadie entiende por qué unos archivos aparecen en la búsqueda y otros no.
        fileIndexingService.indexAsync(saved.getId());
        return saved;
    }

    private FileEntity createBlank(
            byte[] content, String name, String mimeType, String extension,
            Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        String uuid = UUID.randomUUID().toString();
        String storedFileName = FileUtils.generateStoredFileName(uuid, extension);

        storageService.store(
                storedFileName,
                new ByteArrayInputStream(content),
                content.length,
                mimeType);

        String fileUserId = null;
        Long effectiveApiKeyId = apiKeyId;
        if (folderId != null) {
            FolderEntity parent = resolveContainingFolder(folderId);
            fileUserId = parent.getUserId();
            effectiveApiKeyId = parent.getApiKeyId();
        } else {
            if ("private".equalsIgnoreCase(scope)) {
                if (userId == null || userId.isBlank()) {
                    throw new StorageException("Se requiere una identidad de usuario autenticado para crear archivos en Mis Archivos.");
                }
                fileUserId = userId.trim();
            }
        }

        String effectiveUserName = (userName != null && !userName.isBlank()) ? userName : "Usuario";

        FileEntity fileEntity = FileEntity.builder()
            .uuid(uuid)
            .fileName(storedFileName)
            .originalFileName(name)
            .bucket(bucket)
            .objectName(storedFileName)
            .mimeType(mimeType)
            .extension(extension)
            .size((long) content.length)
            .apiKeyId(effectiveApiKeyId)
            .folderId(folderId)
            .userId(fileUserId)
            .createdByName(effectiveUserName)
            .createdByUserId(userId)
            .updatedByName(effectiveUserName)
            .build();

        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, effectiveUserName, ActivityAction.CREATE_BLANK,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
        return saved;
    }

    @Override
    public FileEntity getFile(Long fileId, Long apiKeyId) {
        return getFile(fileId, apiKeyId, null);
    }

    /**
     * Resolves a file the caller is allowed to act on.
     *
     * <p>A file belonging to the caller's project resolves as it always did. On top of that, a
     * <b>private</b> file whose {@code user_id} is the caller's cédula also resolves even when it
     * was created from a different consumer application — otherwise a decentralized "Mis archivos"
     * would list files that answer 404 on download, rename, move and open.
     *
     * <p>The second branch requires {@code user_id} to be non-null and to equal the caller, so it
     * never reaches a shared file: those are stored with {@code user_id = null} and stay isolated
     * inside their own project.
     */
    @Override
    public FileEntity getFile(Long fileId, Long apiKeyId, String userId) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId).orElse(null);
        if (file == null) {
            throw new FileNotFoundException(fileId);
        }

        // 1. Same project
        if (apiKeyId != null && apiKeyId.equals(file.getApiKeyId())) {
            return file;
        }

        // 2. Owner of personal file (decentralized "Mis archivos")
        if (userId != null && !userId.isBlank() && file.getUserId() != null
                && file.getUserId().trim().equalsIgnoreCase(userId.trim())) {
            return file;
        }

        // 3. Shared directly with user (cédula) or target project across the platform
        if (hasAccessViaShare(file, apiKeyId, userId)) {
            return file;
        }

        throw new FileNotFoundException(fileId);
    }

    private boolean hasAccessViaShare(FileEntity file, Long apiKeyId, String userId) {
        if (sharePermissionRepository == null) return false;
        String cleanUserId = (userId != null && !userId.isBlank()) ? userId.trim() : null;

        // Direct file permission across projects
        List<SharePermissionEntity> filePerms = sharePermissionRepository
                .findAllByResourceTypeAndResourceId(SharePermissionEntity.ResourceType.FILE, file.getId());
        for (SharePermissionEntity p : filePerms) {
            if (p.getTargetType() == SharePermissionEntity.TargetType.USER
                    && cleanUserId != null && cleanUserId.equalsIgnoreCase(p.getTargetUserId())) {
                return true;
            }
            if (p.getTargetType() == SharePermissionEntity.TargetType.PROJECT
                    && apiKeyId != null && apiKeyId.equals(p.getTargetApiKeyId())) {
                return true;
            }
        }

        // Inherited permission from parent folder across projects
        if (file.getFolderId() != null) {
            List<SharePermissionEntity> folderPerms = sharePermissionRepository
                    .findAllByResourceTypeAndResourceId(SharePermissionEntity.ResourceType.FOLDER, file.getFolderId());
            for (SharePermissionEntity p : folderPerms) {
                if (p.getTargetType() == SharePermissionEntity.TargetType.USER
                        && cleanUserId != null && cleanUserId.equalsIgnoreCase(p.getTargetUserId())) {
                    return true;
                }
                if (p.getTargetType() == SharePermissionEntity.TargetType.PROJECT
                        && apiKeyId != null && apiKeyId.equals(p.getTargetApiKeyId())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public FileEntity getFileIncludingTrashed(Long fileId, Long apiKeyId) {
        return getFileIncludingTrashed(fileId, apiKeyId, null);
    }

    /**
     * Same lookup, restricted to what the caller may legitimately reach.
     *
     * <p>Resuelve tres caminos, y los tres hacen falta: un archivo del proyecto del llamador, uno
     * privado suyo bajo otro proyecto ("Mis archivos" es descentralizado), y uno que alguien le
     * compartió. Este último faltaba, y era el motivo de que quitar de "Compartidos conmigo"
     * respondiera 404: el archivo existía y la persona tenía acceso, pero esta consulta no
     * contemplaba las concesiones y fallaba antes de que el controlador pudiera desvincular.
     *
     * <p>Resolver no es autorizar. Esto solo decide si el recurso es alcanzable para responder 404
     * o no; quién puede destruirlo lo sigue decidiendo {@code isOwnerOrAdmin} en el controlador,
     * que es lo que separa borrar de verdad de renunciar al acceso propio.
     */
    @Override
    public FileEntity getFileIncludingTrashed(Long fileId, Long apiKeyId, String userId) {
        FileEntity sameProject = fileRepository.findByIdAndApiKeyId(fileId, apiKeyId).orElse(null);
        if (sameProject != null) {
            return sameProject;
        }
        if (userId == null || userId.isBlank()) {
            throw new FileNotFoundException(fileId);
        }
        String owner = userId.trim();
        FileEntity file = fileRepository.findById(fileId).orElseThrow(() -> new FileNotFoundException(fileId));

        boolean ownPrivateFile = file.getUserId() != null
                && file.getUserId().trim().equalsIgnoreCase(owner);
        if (ownPrivateFile || hasGrantOver(SharePermissionEntity.ResourceType.FILE, fileId, owner)) {
            return file;
        }
        throw new FileNotFoundException(fileId);
    }

    /** Si a esta persona le concedieron acceso al recurso, aunque lo tenga en su papelera. */
    private boolean hasGrantOver(SharePermissionEntity.ResourceType resourceType, Long resourceId, String userId) {
        return !sharePermissionRepository
                .findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
                        resourceType, resourceId, SharePermissionEntity.TargetType.USER, userId)
                .isEmpty();
    }

    @Override
    @Transactional
    public FileEntity renameFile(Long fileId, Long apiKeyId, String newName, String userId, String userName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new StorageException("El nombre del archivo no puede estar vacío");
        }

        String trimmedName = newName.trim();
        if (trimmedName.length() > MAX_FILE_NAME_LENGTH) {
            throw new StorageException(
                    "El nombre del archivo no puede exceder " + MAX_FILE_NAME_LENGTH + " caracteres");
        }

        FileEntity fileEntity = getFile(fileId, apiKeyId, userId);
        String oldName = fileEntity.getOriginalFileName();
        fileEntity.setOriginalFileName(trimmedName);
        fileEntity.setUpdatedByName(userName);

        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.RENAME,
                saved.getId(), saved.getOriginalFileName(),
                "Renombrado de '" + oldName + "' a '" + trimmedName + "'", saved.getFolderId());
        return saved;
    }

    @Override
    @Transactional
    public void softDeleteFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = getFile(fileId, apiKeyId, userId);
        fileEntity.setDeletedAt(LocalDateTime.now());
        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.DELETE,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
    }

    @Override
    @Transactional
    public FileEntity restoreFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = fileRepository
            .findByIdAndApiKeyIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(fileId, apiKeyId)
            .or(() -> fileRepository.findByIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(fileId))
            .orElseThrow(() -> new FileNotFoundException(fileId));

        fileEntity.setDeletedAt(null);
        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.RESTORE,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
        return saved;
    }

    /**
     * Vacia el archivo de la papelera de su dueño sin destruirlo.
     *
     * <p>Esto era un borrado fisico: se iba la fila y el binario de MinIO en el mismo acto, y con
     * ellos el archivo desaparecia tambien de la papelera del panel de administracion. El error
     * mas caro del sistema era el mas facil de cometer, y no habia forma de auditarlo ni de
     * deshacerlo.
     *
     * <p>Ahora el vaciado es una segunda ocultacion: el archivo sale de la papelera del usuario y
     * queda unicamente a la vista del administrador, con su binario y su historial de versiones
     * intactos. El borrado fisico existe, pero es potestad exclusiva de
     * {@code DELETE /api/admin/files/&#123;id&#125;/purge}.
     */
    @Override
    @Transactional
    public void purgeFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = fileRepository
            .findByIdAndApiKeyIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(fileId, apiKeyId)
            .or(() -> fileRepository.findByIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(fileId))
            .orElseThrow(() -> new FileNotFoundException(fileId));

        fileEntity.setUserPurgedAt(LocalDateTime.now());
        FileEntity saved = fileRepository.save(fileEntity);

        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.PURGE,
                saved.getId(), saved.getOriginalFileName(),
                "Vaciado de la papelera del usuario. El archivo se conserva en la papelera del "
                        + "panel de administracion hasta que un administrador lo borre.",
                saved.getFolderId());
    }

    @Override
    public InputStream downloadFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = getFile(fileId, apiKeyId, userId);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.DOWNLOAD,
                fileEntity.getId(), fileEntity.getOriginalFileName(), null, fileEntity.getFolderId());
        return storageService.retrieve(fileEntity.getObjectName());
    }

    @Override
    public FileEntity getFileByUuid(String uuid) {
        return fileRepository.findByUuid(uuid)
                .orElseThrow(() -> new FileNotFoundException(-1L));
    }

    @Override
    public InputStream downloadByUuid(String uuid) {
        FileEntity fileEntity = getFileByUuid(uuid);
        activityLogRecorder.record(fileEntity.getApiKeyId(), null, null, ActivityAction.DOWNLOAD,
                fileEntity.getId(), fileEntity.getOriginalFileName(), null, fileEntity.getFolderId());
        return storageService.retrieve(fileEntity.getObjectName());
    }


    /**
     * Resolves the containing folder for something being created inside it.
     *
     * <p>Deliberately NOT scoped to the caller's project. Authorisation already happened in the
     * controller, which requires EDIT on the folder; repeating the check here as
     * {@code findByIdAndApiKeyId} was a second, stricter rule that cancelled the first — someone
     * granted EDIT on a folder of another project could not upload a single file into it.
     *
     * <p>The new item belongs to the folder's project and inherits its space, not the caller's:
     * a file dropped into a shared folder has to be reachable by the people who share that folder.
     */
    /**
     * Copia el binario y la fila, sin tocar el original.
     *
     * <p>El binario se duplica de verdad en el almacenamiento en lugar de apuntar las dos filas al
     * mismo objeto: compartir el objeto haría que editar la copia cambiara el original, y que
     * purgar cualquiera de los dos dejara al otro sin contenido.
     *
     * <p>La copia nace sin historial de versiones y sin las restricciones de acceso del original.
     * Es un documento nuevo que arranca en su primera versión, de quien lo copió.
     */
    @Override
    @Transactional
    public FileEntity copyFile(Long fileId, Long targetFolderId, Long apiKeyId, String userId,
                               String userName, String scope) {
        FileEntity source = getFile(fileId, apiKeyId, userId);

        // La copia ocupa espacio propio: cuenta contra la cuota igual que una subida.
        assertQuotaAllows(apiKeyId, source.getSize());

        String uuid = UUID.randomUUID().toString();
        String extension = source.getExtension();
        String storedFileName = FileUtils.generateStoredFileName(uuid, extension);

        try (InputStream original = storageService.retrieve(source.getObjectName())) {
            storageService.store(storedFileName, original, source.getSize(), source.getMimeType());
        } catch (IOException e) {
            throw new StorageException("No se pudo leer el archivo original para copiarlo", e);
        }

        String fileUserId = null;
        Long effectiveApiKeyId = apiKeyId;
        if (targetFolderId != null) {
            FolderEntity parent = resolveContainingFolder(targetFolderId);
            fileUserId = parent.getUserId();
            effectiveApiKeyId = parent.getApiKeyId();
        } else if ("private".equalsIgnoreCase(scope)) {
            if (userId == null || userId.isBlank()) {
                throw new StorageException(
                        "Se requiere una identidad de usuario autenticado para copiar a Mis Archivos.");
            }
            fileUserId = userId.trim();
        }

        String effectiveUserName = (userName != null && !userName.isBlank()) ? userName : "Usuario";

        FileEntity copy = FileEntity.builder()
                .uuid(uuid)
                .fileName(storedFileName)
                .originalFileName(copyName(source.getOriginalFileName()))
                .bucket(bucket)
                .objectName(storedFileName)
                .mimeType(source.getMimeType())
                .extension(extension)
                .size(source.getSize())
                .apiKeyId(effectiveApiKeyId)
                .folderId(targetFolderId)
                .userId(fileUserId)
                .createdByName(effectiveUserName)
                .createdByUserId(userId)
                .updatedByName(effectiveUserName)
                .build();

        FileEntity saved = fileRepository.save(copy);

        fileIndexingService.indexAsync(saved.getId());
        // Se registra como UPLOAD a propósito: activity_log tiene una restricción CHECK sobre la
        // lista de acciones, y agregar un valor nuevo al enum compila y pasa los tests contra H2
        // pero revienta en producción. El detalle dice de dónde salió.
        activityLogRecorder.record(apiKeyId, userId, effectiveUserName, ActivityAction.UPLOAD,
                saved.getId(), saved.getOriginalFileName(),
                "Copia de \"" + source.getOriginalFileName() + "\"", saved.getFolderId());
        return saved;
    }

    /** "informe.docx" se copia como "informe (copia).docx": la extensión tiene que sobrevivir. */
    private static String copyName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "copia";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot <= 0) {
            return originalName + " (copia)";
        }
        return originalName.substring(0, dot) + " (copia)" + originalName.substring(dot);
    }

    private FolderEntity resolveContainingFolder(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
    }

}
