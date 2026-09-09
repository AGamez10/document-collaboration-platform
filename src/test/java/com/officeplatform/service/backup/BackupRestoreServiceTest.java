package com.officeplatform.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeplatform.dto.response.BackupRestoreResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.service.storage.StorageService;

/**
 * Restoring a backup package.
 *
 * <p>Two failures motivated these cases, both reported from a real restore. The first is proven by
 * the stack trace it left: a folder whose api key could not be mapped had {@code api_key_id} set to
 * null, PostgreSQL rejected the update against its NOT NULL constraint, and the single transaction
 * that wraps the whole restore rolled back — so no folder came back and every file surfaced flat at
 * the root. The second is that binaries used to be located by the {@code files/} prefix alone, which
 * finds nothing in a package that stores them under a readable hierarchy.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupRestoreServiceTest {

    private static final String BUCKET = "office-platform";

    @Mock private FileRepository fileRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private KnownUserRepository knownUserRepository;
    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private StorageService storageService;
    @Mock private PlatformTransactionManager transactionManager;

    private ObjectMapper objectMapper;
    private BackupRestoreService service;

    /** Rows the fake repositories hold, so a restore can be observed after the fact. */
    private final Map<String, FolderEntity> foldersByUuid = new LinkedHashMap<>();
    private final Map<String, FileEntity> filesByUuid = new LinkedHashMap<>();
    private final List<ApiKeyEntity> apiKeys = new ArrayList<>();
    private final Map<String, byte[]> stored = new LinkedHashMap<>();
    private final List<SharePermissionEntity> savedGrants = new ArrayList<>();
    private final List<KnownUserEntity> savedUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        AtomicLong folderSeq = new AtomicLong(1000);
        AtomicLong fileSeq = new AtomicLong(2000);

        lenient().when(folderRepository.findByUuid(anyString()))
                .thenAnswer(i -> Optional.ofNullable(foldersByUuid.get(i.<String>getArgument(0))));
        lenient().when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> {
            FolderEntity f = i.getArgument(0);
            if (f.getId() == null) {
                f.setId(folderSeq.incrementAndGet());
            }
            foldersByUuid.put(f.getUuid(), f);
            return f;
        });

        lenient().when(fileRepository.findByUuid(anyString()))
                .thenAnswer(i -> Optional.ofNullable(filesByUuid.get(i.<String>getArgument(0))));
        lenient().when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> {
            FileEntity f = i.getArgument(0);
            if (f.getId() == null) {
                f.setId(fileSeq.incrementAndGet());
            }
            filesByUuid.put(f.getUuid(), f);
            return f;
        });

        lenient().when(apiKeyRepository.findByApiKey(anyString())).thenAnswer(i -> apiKeys.stream()
                .filter(k -> i.getArgument(0).equals(k.getApiKey())).findFirst());
        lenient().when(apiKeyRepository.findAll()).thenAnswer(i -> new ArrayList<>(apiKeys));
        lenient().when(apiKeyRepository.existsById(anyLong())).thenAnswer(i -> apiKeys.stream()
                .anyMatch(k -> i.getArgument(0).equals(k.getId())));
        lenient().when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenAnswer(i -> {
            ApiKeyEntity k = i.getArgument(0);
            if (k.getId() == null) {
                k.setId(7L);
            }
            apiKeys.removeIf(existing -> k.getApiKey().equals(existing.getApiKey()));
            apiKeys.add(k);
            return k;
        });

        lenient().doAnswer(i -> {
            stored.put(i.getArgument(0), i.<ByteArrayInputStream>getArgument(1).readAllBytes());
            return null;
        }).when(storageService).store(anyString(), any(), anyLong(), anyString());

        lenient().when(knownUserRepository.findByApiKeyIdAndUserId(anyLong(), anyString()))
                .thenAnswer(i -> savedUsers.stream()
                        .filter(u -> i.getArgument(0).equals(u.getApiKeyId()))
                        .filter(u -> i.getArgument(1).equals(u.getUserId()))
                        .findFirst());
        lenient().when(knownUserRepository.save(any(KnownUserEntity.class))).thenAnswer(i -> {
            KnownUserEntity u = i.getArgument(0);
            savedUsers.add(u);
            return u;
        });

        AtomicLong grantSeq = new AtomicLong(3000);
        lenient().when(sharePermissionRepository.save(any(SharePermissionEntity.class))).thenAnswer(i -> {
            SharePermissionEntity g = i.getArgument(0);
            if (g.getId() == null) {
                g.setId(grantSeq.incrementAndGet());
                savedGrants.add(g);
            }
            return g;
        });
        lenient().when(sharePermissionRepository
                .findAllByResourceTypeAndResourceIdAndSourceApiKeyId(any(), anyLong(), anyLong()))
                .thenAnswer(i -> savedGrants.stream()
                        .filter(g -> g.getResourceType() == i.getArgument(0))
                        .filter(g -> i.getArgument(1).equals(g.getResourceId()))
                        .filter(g -> i.getArgument(2).equals(g.getSourceApiKeyId()))
                        .toList());

        service = new BackupRestoreService(fileRepository, folderRepository, apiKeyRepository,
                knownUserRepository, sharePermissionRepository, storageService, objectMapper,
                transactionManager, BUCKET);
    }

    // ── package builders ─────────────────────────────────────────────────────

    private Map<String, Object> apiKeyNode(long id, String key, String name) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("apiKey", key);
        n.put("name", name);
        n.put("active", true);
        return n;
    }

    private Map<String, Object> folderNode(long id, String uuid, String name, Long parentId, Long apiKeyId) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("uuid", uuid);
        n.put("name", name);
        n.put("parentId", parentId);
        n.put("apiKeyId", apiKeyId);
        return n;
    }

    private Map<String, Object> fileNode(long id, String uuid, String name, Long folderId,
                                         Long apiKeyId, String objectName, String zipEntryPath) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("uuid", uuid);
        n.put("originalFileName", name);
        n.put("fileName", objectName);
        n.put("objectName", objectName);
        n.put("bucket", BUCKET);
        n.put("folderId", folderId);
        n.put("apiKeyId", apiKeyId);
        n.put("mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        n.put("size", 12L);
        if (zipEntryPath != null) {
            n.put("zipEntryPath", zipEntryPath);
        }
        return n;
    }

    /** Assembles a package in memory with the given manifest and binary entries. */
    private byte[] packageOf(Map<String, Object> manifest, Map<String, String> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(objectMapper.writeValueAsBytes(manifest));
            zos.closeEntry();
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** A package with a project, a nested folder pair and one file inside the child. */
    private Map<String, Object> hierarchicalManifest() {
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("layoutVersion", 2);
        manifest.put("apiKeysMetadata", List.of(apiKeyNode(3L, "clave-proyecto-x", "Proyecto X")));
        manifest.put("knownUsersMetadata", List.of());
        manifest.put("foldersMetadata", List.of(
                // El hijo va primero a propósito: el segundo pase existe justo para ese caso.
                folderNode(21L, "uuid-hijo", "Semana 36", 20L, 3L),
                folderNode(20L, "uuid-padre", "Informes", null, 3L)));
        manifest.put("filesMetadata", List.of(fileNode(31L, "uuid-archivo", "informe.docx", 21L, 3L,
                "obj-31.docx", "archivos/Proyecto X/Informes/Semana 36/informe.docx")));
        return manifest;
    }

    // ── cases ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("nested folders come back with their hierarchy and the file inside its own folder")
    void restoresTheHierarchy() throws Exception {
        byte[] zip = packageOf(hierarchicalManifest(), Map.of(
                "archivos/Proyecto X/Informes/Semana 36/informe.docx", "contenido"));

        BackupRestoreResponse result = service.restore(new ByteArrayInputStream(zip), "paquete.zip");

        FolderEntity parent = foldersByUuid.get("uuid-padre");
        FolderEntity child = foldersByUuid.get("uuid-hijo");
        assertThat(parent.getParentId()).isNull();
        assertThat(child.getParentId()).isEqualTo(parent.getId());

        FileEntity file = filesByUuid.get("uuid-archivo");
        // El síntoma reportado era exactamente este campo en null: todo plano en la raíz.
        assertThat(file.getFolderId()).isEqualTo(child.getId());
        assertThat(result.getFoldersCreated()).isEqualTo(2);
        assertThat(result.getFilesCreated()).isEqualTo(1);
    }

    @Test
    @DisplayName("an existing folder keeps its project instead of being nulled into a constraint error")
    void preservesApiKeyIdOfAnExistingFolder() throws Exception {
        FolderEntity existing = new FolderEntity();
        existing.setId(2L);
        existing.setUuid("uuid-padre");
        existing.setName("Informes");
        existing.setApiKeyId(9L);
        foldersByUuid.put("uuid-padre", existing);

        // El respaldo referencia un proyecto que no viaja en el paquete: mapped() da null.
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("apiKeysMetadata", List.of());
        manifest.put("knownUsersMetadata", List.of());
        manifest.put("foldersMetadata", List.of(folderNode(20L, "uuid-padre", "Informes", null, 3L)));
        manifest.put("filesMetadata", List.of());

        service.restore(new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        // Escribir null acá abortaba la transacción entera y no se restauraba nada.
        assertThat(foldersByUuid.get("uuid-padre").getApiKeyId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("a new folder with no mappable project falls back instead of writing null")
    void fallsBackToAnExistingProjectForANewFolder() throws Exception {
        ApiKeyEntity anyProject = new ApiKeyEntity();
        anyProject.setId(5L);
        anyProject.setApiKey("clave-existente");
        anyProject.setName("Proyecto Existente");
        apiKeys.add(anyProject);

        Map<String, Object> manifest = new HashMap<>();
        manifest.put("apiKeysMetadata", List.of());
        manifest.put("knownUsersMetadata", List.of());
        manifest.put("foldersMetadata", List.of(folderNode(20L, "uuid-huerfana", "Suelta", null, 99L)));
        manifest.put("filesMetadata", List.of());

        BackupRestoreResponse result =
                service.restore(new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        assertThat(foldersByUuid.get("uuid-huerfana").getApiKeyId()).isEqualTo(5L);
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Suelta"));
    }

    @Test
    @DisplayName("a folder that sat at the root in the package does not keep a stale parent")
    void clearsAStaleParent() throws Exception {
        FolderEntity existing = new FolderEntity();
        existing.setId(2L);
        existing.setUuid("uuid-padre");
        existing.setName("Informes");
        existing.setApiKeyId(3L);
        existing.setParentId(77L);
        foldersByUuid.put("uuid-padre", existing);

        Map<String, Object> manifest = new HashMap<>();
        manifest.put("apiKeysMetadata", List.of(apiKeyNode(3L, "clave-proyecto-x", "Proyecto X")));
        manifest.put("knownUsersMetadata", List.of());
        manifest.put("foldersMetadata", List.of(folderNode(20L, "uuid-padre", "Informes", null, 3L)));
        manifest.put("filesMetadata", List.of());

        service.restore(new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        assertThat(foldersByUuid.get("uuid-padre").getParentId()).isNull();
    }

    @Test
    @DisplayName("the binary is located by the hierarchical path the manifest declares")
    void findsBinariesByHierarchicalPath() throws Exception {
        byte[] zip = packageOf(hierarchicalManifest(), Map.of(
                "archivos/Proyecto X/Informes/Semana 36/informe.docx", "contenido"));

        BackupRestoreResponse result = service.restore(new ByteArrayInputStream(zip), "paquete.zip");

        // Se guarda bajo su objectName, no bajo la ruta legible: la ruta es para el humano que
        // abre el ZIP, el objectName es la clave real en el almacenamiento.
        assertThat(stored).containsKey("obj-31.docx");
        assertThat(new String(stored.get("obj-31.docx"), StandardCharsets.UTF_8)).isEqualTo("contenido");
        assertThat(result.getBinariesRestored()).isEqualTo(1);
        assertThat(result.getBinariesSkipped()).isZero();
    }

    @Test
    @DisplayName("a legacy package with files/{objectName} still restores its binaries")
    void findsBinariesOfALegacyPackage() throws Exception {
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("apiKeysMetadata", List.of(apiKeyNode(3L, "clave-proyecto-x", "Proyecto X")));
        manifest.put("knownUsersMetadata", List.of());
        manifest.put("foldersMetadata", List.of());
        // Sin zipEntryPath, como los paquetes anteriores a este formato.
        manifest.put("filesMetadata", List.of(
                fileNode(31L, "uuid-viejo", "informe.docx", null, 3L, "obj-31.docx", null)));

        byte[] zip = packageOf(manifest, Map.of("files/obj-31.docx", "contenido viejo"));

        BackupRestoreResponse result = service.restore(new ByteArrayInputStream(zip), "legado.zip");

        assertThat(stored).containsKey("obj-31.docx");
        assertThat(new String(stored.get("obj-31.docx"), StandardCharsets.UTF_8)).isEqualTo("contenido viejo");
        assertThat(result.getBinariesRestored()).isEqualTo(1);
    }

    @Test
    @DisplayName("a missing binary is reported instead of silently counting as restored")
    void reportsAMissingBinary() throws Exception {
        byte[] zip = packageOf(hierarchicalManifest(), Map.of("archivos/otro/suelto.docx", "x"));

        BackupRestoreResponse result = service.restore(new ByteArrayInputStream(zip), "paquete.zip");

        assertThat(result.getBinariesRestored()).isZero();
        assertThat(result.getBinariesSkipped()).isEqualTo(1);
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("informe.docx"));
        verify(storageService, never()).store(anyString(), any(), anyLong(), anyString());
    }

    // ── permisos ─────────────────────────────────────────────────────────────

    private Map<String, Object> grantNode(String resourceType, long resourceId, long sourceApiKeyId,
                                          String targetType, String targetUserId, Long targetApiKeyId,
                                          String level) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", 500L);
        n.put("resourceType", resourceType);
        n.put("resourceId", resourceId);
        n.put("sourceApiKeyId", sourceApiKeyId);
        n.put("targetType", targetType);
        n.put("targetUserId", targetUserId);
        n.put("targetApiKeyId", targetApiKeyId);
        n.put("permissionLevel", level);
        n.put("sharedByUserId", "111");
        n.put("sharedByName", "Beto");
        n.put("notes", "Solo para revisión");
        n.put("createdAt", "2026-01-15T10:30:00");
        n.put("expiresAt", "2026-12-31T23:59:00");
        return n;
    }

    @Test
    @DisplayName("a grant is re-linked to the id the file actually got in this database")
    void relinksAGrantToTheRestoredFileId() throws Exception {
        Map<String, Object> manifest = hierarchicalManifest();
        // El permiso apunta al id 31 del respaldo; acá el archivo recibe otro id.
        manifest.put("sharePermissionsMetadata", List.of(
                grantNode("FILE", 31L, 3L, "USER", "1004356866", null, "DOWNLOAD")));

        service.restore(new ByteArrayInputStream(packageOf(manifest, Map.of(
                "archivos/Proyecto X/Informes/Semana 36/informe.docx", "contenido"))), "paquete.zip");

        SharePermissionEntity saved = savedGrants.get(0);
        assertThat(saved.getResourceId()).isEqualTo(filesByUuid.get("uuid-archivo").getId());
        assertThat(saved.getResourceType()).isEqualTo(SharePermissionEntity.ResourceType.FILE);
        assertThat(saved.getPermissionLevel()).isEqualTo(SharePermissionEntity.PermissionLevel.DOWNLOAD);
        assertThat(saved.getTargetUserId()).isEqualTo("1004356866");
    }

    @Test
    @DisplayName("notes, expiry and authorship survive the round trip")
    void preservesTheDetailsOfAGrant() throws Exception {
        Map<String, Object> manifest = hierarchicalManifest();
        manifest.put("sharePermissionsMetadata", List.of(
                grantNode("FOLDER", 20L, 3L, "USER", "1004356866", null, "EDIT")));

        BackupRestoreResponse result = service.restore(
                new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        SharePermissionEntity saved = savedGrants.get(0);
        assertThat(saved.getResourceId()).isEqualTo(foldersByUuid.get("uuid-padre").getId());
        assertThat(saved.getNotes()).isEqualTo("Solo para revisión");
        assertThat(saved.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30));
        assertThat(saved.getSharedByName()).isEqualTo("Beto");
        assertThat(result.getSharePermissionsCreated()).isEqualTo(1);
    }

    @Test
    @DisplayName("an existing grant is updated in place instead of duplicated")
    void updatesAnExistingGrantInsteadOfDuplicating() throws Exception {
        Map<String, Object> manifest = hierarchicalManifest();
        manifest.put("sharePermissionsMetadata", List.of(
                grantNode("FILE", 31L, 3L, "USER", "1004356866", null, "EDIT")));

        // Primera restauración crea la regla; la segunda tiene que encontrarla.
        byte[] zip = packageOf(manifest, Map.of());
        service.restore(new ByteArrayInputStream(zip), "paquete.zip");
        BackupRestoreResponse second = service.restore(new ByteArrayInputStream(zip), "paquete.zip");

        assertThat(savedGrants).hasSize(1);
        assertThat(second.getSharePermissionsUpdated()).isEqualTo(1);
        assertThat(second.getSharePermissionsCreated()).isZero();
    }

    @Test
    @DisplayName("a grant over a resource that is not in the package is dropped, not misapplied")
    void dropsAGrantWhoseResourceIsMissing() throws Exception {
        Map<String, Object> manifest = hierarchicalManifest();
        // El id 999 no existe en el paquete: aplicarlo abriría un recurso ajeno.
        manifest.put("sharePermissionsMetadata", List.of(
                grantNode("FILE", 999L, 3L, "USER", "1004356866", null, "EDIT")));

        BackupRestoreResponse result = service.restore(
                new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        assertThat(savedGrants).isEmpty();
        assertThat(result.getSharePermissionsCreated()).isZero();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Permiso omitido"));
    }

    @Test
    @DisplayName("a project-wide grant is re-linked through the api key mapping")
    void relinksAProjectGrant() throws Exception {
        Map<String, Object> manifest = hierarchicalManifest();
        manifest.put("apiKeysMetadata", List.of(
                apiKeyNode(3L, "clave-proyecto-x", "Proyecto X"),
                apiKeyNode(4L, "clave-proyecto-y", "Proyecto Y")));
        manifest.put("sharePermissionsMetadata", List.of(
                grantNode("FILE", 31L, 3L, "PROJECT", null, 4L, "VIEW")));

        service.restore(new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        SharePermissionEntity saved = savedGrants.get(0);
        assertThat(saved.getTargetType()).isEqualTo(SharePermissionEntity.TargetType.PROJECT);
        assertThat(saved.getTargetApiKeyId()).isNotNull();
    }

    @Test
    @DisplayName("a legacy package with no grants restores without touching permissions")
    void restoresALegacyPackageWithNoGrants() throws Exception {
        // Un paquete de la versión 1 no trae sharePermissionsMetadata: no debe fallar ni borrar.
        BackupRestoreResponse result = service.restore(
                new ByteArrayInputStream(packageOf(hierarchicalManifest(), Map.of())), "legado.zip");

        assertThat(savedGrants).isEmpty();
        assertThat(result.getSharePermissionsCreated()).isZero();
        assertThat(result.getFoldersCreated()).isEqualTo(2);
    }

    // ── asimetría deliberada del fallback de proyecto ────────────────────────

    private Map<String, Object> knownUserNode(long apiKeyId, String userId, String displayName) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", 700L);
        n.put("apiKeyId", apiKeyId);
        n.put("userId", userId);
        n.put("displayName", displayName);
        n.put("role", "user");
        return n;
    }

    @Test
    @DisplayName("a known user whose project is gone is skipped, never reassigned to another one")
    void skipsAKnownUserWhoseProjectIsMissing() throws Exception {
        Map<String, Object> manifest = hierarchicalManifest();
        // Hay un proyecto disponible: si el fallback de carpetas y archivos se extendiera acá,
        // esta persona quedaría inscrita en "Proyecto X" sin haber pertenecido nunca.
        manifest.put("knownUsersMetadata", List.of(
                knownUserNode(99L, "1004356866", "Ana Pérez")));

        BackupRestoreResponse result = service.restore(
                new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        assertThat(savedUsers).isEmpty();
        assertThat(result.getKnownUsersCreated()).isZero();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Usuario conocido omitido"));
        // Y la asimetría es real: la carpeta del mismo paquete SÍ se conserva, porque perder un
        // documento es peor que ubicarlo mal, mientras que inventar una pertenencia es peor que
        // perder un nombre para mostrar.
        assertThat(result.getFoldersCreated()).isEqualTo(2);
    }

    @Test
    @DisplayName("a known user whose project does map is restored normally")
    void restoresAKnownUserWhoseProjectMaps() throws Exception {
        Map<String, Object> manifest = hierarchicalManifest();
        manifest.put("knownUsersMetadata", List.of(
                knownUserNode(3L, "1004356866", "Ana Pérez")));

        BackupRestoreResponse result = service.restore(
                new ByteArrayInputStream(packageOf(manifest, Map.of())), "paquete.zip");

        assertThat(savedUsers).hasSize(1);
        assertThat(savedUsers.get(0).getDisplayName()).isEqualTo("Ana Pérez");
        assertThat(result.getKnownUsersCreated()).isEqualTo(1);
    }

    @Test
    @DisplayName("a package with no manifest is rejected with an explanation")
    void rejectsAPackageWithoutManifest() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("cualquier-cosa.txt"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.restore(new ByteArrayInputStream(out.toByteArray()), "malo.zip"))
                .getMessage()).contains("manifest.json");
    }

}
