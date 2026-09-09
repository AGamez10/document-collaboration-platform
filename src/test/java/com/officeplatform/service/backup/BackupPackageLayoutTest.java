package com.officeplatform.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeplatform.dto.response.BackupInfoResponse;
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
 * The shape of a generated backup package.
 *
 * <p>A backup is opened in Windows Explorer precisely when the platform is unavailable, so a flat
 * {@code files/} directory of UUID names is technically complete and useless to the person who needs
 * their spreadsheet back. These cases pin the readable hierarchy, the {@code zipEntryPath} the
 * restorer relies on to find each binary, and the two ways a path can be destroyed on Windows:
 * characters the filesystem rejects, and two files that share a name in the same folder.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupPackageLayoutTest {

    @Mock private FileRepository fileRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private KnownUserRepository knownUserRepository;
    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private StorageService storageService;
    @Mock private BackupRestoreService backupRestoreService;

    private BackupServiceImpl serviceOn(Path dir, List<FileEntity> files,
                                        List<FolderEntity> folders, List<ApiKeyEntity> keys) {
        return serviceOn(dir, files, folders, keys, List.of(), List.of());
    }

    private BackupServiceImpl serviceOn(Path dir, List<FileEntity> files,
                                        List<FolderEntity> folders, List<ApiKeyEntity> keys,
                                        List<KnownUserEntity> users,
                                        List<SharePermissionEntity> permissions) {
        lenient().when(fileRepository.findAll()).thenReturn(files);
        lenient().when(folderRepository.findAll()).thenReturn(folders);
        lenient().when(apiKeyRepository.findAll()).thenReturn(keys);
        lenient().when(knownUserRepository.findAll()).thenReturn(users);
        lenient().when(sharePermissionRepository.findAll()).thenReturn(permissions);
        lenient().when(storageService.retrieve(anyString())).thenAnswer(i ->
                new ByteArrayInputStream(("bytes de " + i.getArgument(0)).getBytes(StandardCharsets.UTF_8)));

        return new BackupServiceImpl(fileRepository, folderRepository, apiKeyRepository,
                knownUserRepository, sharePermissionRepository, storageService, dir.toString(),
                false, 24, 10, "", false, backupRestoreService);
    }

    private KnownUserEntity user(Long apiKeyId, String userId, String displayName) {
        KnownUserEntity u = new KnownUserEntity();
        u.setApiKeyId(apiKeyId);
        u.setUserId(userId);
        u.setDisplayName(displayName);
        u.setRole("user");
        return u;
    }

    private FileEntity privateFile(Long id, Long folderId, String name, Long apiKeyId, String userId) {
        FileEntity f = file(id, folderId, name, apiKeyId);
        f.setUserId(userId);
        return f;
    }

    private SharePermissionEntity grantToUser(SharePermissionEntity.ResourceType type, Long resourceId,
                                              Long sourceApiKeyId, String targetUserId) {
        return SharePermissionEntity.builder()
                .resourceType(type)
                .resourceId(resourceId)
                .sourceApiKeyId(sourceApiKeyId)
                .targetType(SharePermissionEntity.TargetType.USER)
                .targetUserId(targetUserId)
                .permissionLevel(SharePermissionEntity.PermissionLevel.VIEW)
                .build();
    }

    private ApiKeyEntity key(Long id, String name) {
        ApiKeyEntity k = new ApiKeyEntity();
        k.setId(id);
        k.setApiKey("clave-" + id);
        k.setName(name);
        k.setActive(true);
        return k;
    }

    private FolderEntity folder(Long id, Long parentId, String name, Long apiKeyId) {
        FolderEntity f = new FolderEntity();
        f.setId(id);
        f.setParentId(parentId);
        f.setName(name);
        f.setApiKeyId(apiKeyId);
        return f;
    }

    private FileEntity file(Long id, Long folderId, String name, Long apiKeyId) {
        FileEntity f = new FileEntity();
        f.setId(id);
        f.setUuid("uuid-" + id);
        f.setFolderId(folderId);
        f.setOriginalFileName(name);
        f.setFileName("obj-" + id);
        f.setObjectName("obj-" + id);
        f.setBucket("office-platform");
        f.setApiKeyId(apiKeyId);
        return f;
    }

    private List<String> entriesOf(Path dir, String zipName) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(dir.resolve(zipName).toFile())) {
            zip.stream().map(ZipEntry::getName).forEach(names::add);
        }
        return names;
    }

    private JsonNode manifestOf(Path dir, String zipName) throws IOException {
        try (ZipFile zip = new ZipFile(dir.resolve(zipName).toFile())) {
            return new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json")));
        }
    }

    @Test
    @DisplayName("binaries are written under project and folder names a person can read")
    void writesAReadableHierarchy(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, 21L, "informe.docx", 3L), file(32L, null, "suelto.xlsx", 3L)),
                List.of(folder(20L, null, "Informes", 3L), folder(21L, 20L, "Semana 36", 3L)),
                List.of(key(3L, "Proyecto X")));

        BackupInfoResponse info = service.createBackup("MANUAL");
        List<String> names = entriesOf(dir, info.getFileName());

        assertThat(names).contains(
                "archivos/Proyecto X/Compartidos por Area/Informes/Semana 36/informe.docx",
                "archivos/Proyecto X/Compartidos por Area/suelto.xlsx");
        // Ni un UUID suelto: era exactamente lo que el usuario encontraba al abrir el paquete.
        assertThat(names).noneMatch(n -> n.startsWith("files/"));
        assertThat(info.getFilesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("the manifest declares where each binary landed")
    void recordsTheZipEntryPath(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, 21L, "informe.docx", 3L)),
                List.of(folder(20L, null, "Informes", 3L), folder(21L, 20L, "Semana 36", 3L)),
                List.of(key(3L, "Proyecto X")));

        BackupInfoResponse info = service.createBackup("MANUAL");
        JsonNode manifest = manifestOf(dir, info.getFileName());
        JsonNode file = manifest.get("filesMetadata").get(0);

        assertThat(manifest.get("layoutVersion").asInt()).isEqualTo(3);
        assertThat(file.get("zipEntryPath").asText())
                .isEqualTo("archivos/Proyecto X/Compartidos por Area/Informes/Semana 36/informe.docx");
        // Sin objectName el restaurador no sabría bajo qué clave devolverlo al almacenamiento.
        assertThat(file.get("objectName").asText()).isEqualTo("obj-31");

        JsonNode folder = manifest.get("foldersMetadata").get(1);
        assertThat(folder.get("parentId").asLong()).isEqualTo(20L);
        assertThat(folder.get("uuid")).isNotNull();
    }

    @Test
    @DisplayName("two files sharing a name in one folder both survive the package")
    void keepsBothFilesOnANameCollision(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "informe.docx", 3L), file(32L, null, "informe.docx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")));

        BackupInfoResponse info = service.createBackup("MANUAL");
        List<String> names = entriesOf(dir, info.getFileName());

        // Sin desambiguar, la segunda entrada pisaba a la primera y se perdía un archivo sin aviso.
        assertThat(names).contains(
                "archivos/Proyecto X/Compartidos por Area/informe.docx",
                "archivos/Proyecto X/Compartidos por Area/informe (2).docx");
        assertThat(info.getFilesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("names Windows rejects are sanitised instead of producing an unusable path")
    void sanitisesNamesWindowsRejects(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, 20L, "informe: final?.docx", 3L)),
                List.of(folder(20L, null, "Ventas/Norte", 3L)),
                List.of(key(3L, "Proyecto \"X\"")));

        BackupInfoResponse info = service.createBackup("MANUAL");
        List<String> names = entriesOf(dir, info.getFileName());

        assertThat(names).contains("archivos/Proyecto _X_/Compartidos por Area/Ventas_Norte/informe_ final_.docx");
    }

    @Test
    @DisplayName("a file whose project is missing still gets a place in the package")
    void placesFilesWithNoResolvableProject(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "huerfano.docx", 99L)),
                List.of(), List.of());

        BackupInfoResponse info = service.createBackup("MANUAL");

        assertThat(entriesOf(dir, info.getFileName()))
                .contains("archivos/proyecto-99/Compartidos por Area/huerfano.docx");
    }

    @Test
    @DisplayName("a corrupted parent chain does not hang the backup")
    void survivesACycleInTheFolderChain(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, 20L, "informe.docx", 3L)),
                // Cadena corrupta: cada carpeta dice que la otra es su padre.
                List.of(folder(20L, 21L, "A", 3L), folder(21L, 20L, "B", 3L)),
                List.of(key(3L, "Proyecto X")));

        BackupInfoResponse info = service.createBackup("MANUAL");

        assertThat(info.getFilesCount()).isEqualTo(1);
        assertThat(entriesOf(dir, info.getFileName()))
                .anyMatch(n -> n.startsWith("archivos/Proyecto X/Compartidos por Area/") && n.endsWith("informe.docx"));
    }

    @Test
    @DisplayName("the sanitiser never returns a segment Windows cannot create")
    void sanitiserRejectsTrailingDotsAndBlanks() {
        assertThat(BackupServiceImpl.safeName("Ventas.")).isEqualTo("Ventas");
        assertThat(BackupServiceImpl.safeName("Ventas ")).isEqualTo("Ventas");
        assertThat(BackupServiceImpl.safeName("  ")).isEqualTo("sin-nombre");
        assertThat(BackupServiceImpl.safeName(null)).isEqualTo("sin-nombre");
        assertThat(BackupServiceImpl.safeName("a/b\\c")).isEqualTo("a_b_c");
        assertThat(BackupServiceImpl.safeName("x".repeat(300))).hasSize(120);
    }

    @Test
    @DisplayName("the package a restore reads back is the one this service writes")
    void roundTripsThroughTheRestorer(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, 21L, "informe.docx", 3L)),
                List.of(folder(20L, null, "Informes", 3L), folder(21L, 20L, "Semana 36", 3L)),
                List.of(key(3L, "Proyecto X")));

        BackupInfoResponse info = service.createBackup("MANUAL");
        JsonNode manifest = manifestOf(dir, info.getFileName());
        String declared = manifest.get("filesMetadata").get(0).get("zipEntryPath").asText();

        // La ruta declarada tiene que existir tal cual dentro del ZIP: si el generador y el
        // restaurador se corren un carácter, no queda un solo binario que recuperar.
        assertThat(entriesOf(dir, info.getFileName())).contains(declared);
    }

    @Test
    @DisplayName("a binary missing from storage does not abort the whole package")
    void skipsAnUnreadableBinary(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "bueno.docx", 3L), file(32L, null, "roto.docx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")));
        when(storageService.retrieve("obj-32")).thenThrow(new RuntimeException("no está en MinIO"));

        BackupInfoResponse info = service.createBackup("MANUAL");

        assertThat(info.getFilesCount()).isEqualTo(1);
        assertThat(entriesOf(dir, info.getFileName())).contains("archivos/Proyecto X/Compartidos por Area/bueno.docx");
        // El manifest igual lo declara: la fila existe aunque su binario se haya perdido antes.
        JsonNode files = manifestOf(dir, info.getFileName()).get("filesMetadata");
        assertThat(files).hasSize(2);
    }

    // ── segmentación por persona y por área ──────────────────────────────────

    @Test
    @DisplayName("a file with no owner is packaged under the area's shared segment")
    void packagesAreaFilesUnderTheSharedSegment(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "acta.docx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")));

        assertThat(entriesOf(dir, service.createBackup("MANUAL").getFileName()))
                .contains("archivos/Proyecto X/Compartidos por Area/acta.docx");
    }

    @Test
    @DisplayName("a private file lands in its owner's own folder, named after the person")
    void packagesPrivateFilesUnderTheirOwner(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(privateFile(31L, 20L, "sueldo.xlsx", 3L, "1004356866")),
                List.of(folder(20L, null, "Personal", 3L)),
                List.of(key(3L, "Proyecto X")),
                List.of(user(3L, "1004356866", "Ana Pérez")),
                List.of());

        assertThat(entriesOf(dir, service.createBackup("MANUAL").getFileName())).contains(
                "archivos/Proyecto X/Usuarios/1004356866 - Ana Pérez/Mis Archivos/Personal/sueldo.xlsx");
    }

    @Test
    @DisplayName("an owner with no known name still gets a folder, by cédula alone")
    void packagesPrivateFilesOfAnUnnamedOwner(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(privateFile(31L, null, "nota.docx", 3L, "999")),
                List.of(), List.of(key(3L, "Proyecto X")));

        assertThat(entriesOf(dir, service.createBackup("MANUAL").getFileName()))
                .contains("archivos/Proyecto X/Usuarios/999/Mis Archivos/nota.docx");
    }

    @Test
    @DisplayName("what was shared with a person is copied into their own 'shared with me' folder")
    void copiesSharedFilesIntoTheRecipientFolder(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "presupuesto.xlsx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")),
                List.of(user(3L, "1004356866", "Ana Pérez")),
                List.of(grantToUser(SharePermissionEntity.ResourceType.FILE, 31L, 3L, "1004356866")));

        List<String> names = entriesOf(dir, service.createBackup("MANUAL").getFileName());

        // El original sigue en su lugar y además aparece la copia de contingencia.
        assertThat(names).contains(
                "archivos/Proyecto X/Compartidos por Area/presupuesto.xlsx",
                "archivos/Proyecto X/Usuarios/1004356866 - Ana Pérez/Compartidos conmigo/presupuesto.xlsx");
    }

    @Test
    @DisplayName("a folder shared with a person brings the files inside it along")
    void expandsAFolderGrantIntoItsFiles(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, 21L, "informe.docx", 3L)),
                List.of(folder(20L, null, "Informes", 3L), folder(21L, 20L, "Semana 36", 3L)),
                List.of(key(3L, "Proyecto X")),
                List.of(user(3L, "1004356866", "Ana Pérez")),
                // El permiso apunta a la carpeta padre: quien abre el ZIP busca los documentos,
                // no una carpeta vacía.
                List.of(grantToUser(SharePermissionEntity.ResourceType.FOLDER, 20L, 3L, "1004356866")));

        assertThat(entriesOf(dir, service.createBackup("MANUAL").getFileName())).contains(
                "archivos/Proyecto X/Usuarios/1004356866 - Ana Pérez/Compartidos conmigo/informe.docx");
    }

    @Test
    @DisplayName("the copies never leak into the manifest, so a restore cannot duplicate rows")
    void keepsOnlyTheCanonicalPathInTheManifest(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "presupuesto.xlsx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")),
                List.of(user(3L, "1004356866", "Ana Pérez")),
                List.of(grantToUser(SharePermissionEntity.ResourceType.FILE, 31L, 3L, "1004356866")));

        BackupInfoResponse info = service.createBackup("MANUAL");
        JsonNode files = manifestOf(dir, info.getFileName()).get("filesMetadata");

        assertThat(files).hasSize(1);
        assertThat(files.get(0).get("zipEntryPath").asText())
                .isEqualTo("archivos/Proyecto X/Compartidos por Area/presupuesto.xlsx");
        // El archivo se cuenta una vez aunque su binario se haya escrito dos veces.
        assertThat(info.getFilesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a grant aimed at a whole project does not duplicate the package")
    void ignoresProjectWideGrantsWhenCopying(@TempDir Path dir) throws Exception {
        SharePermissionEntity toProject = SharePermissionEntity.builder()
                .resourceType(SharePermissionEntity.ResourceType.FILE)
                .resourceId(31L)
                .sourceApiKeyId(3L)
                .targetType(SharePermissionEntity.TargetType.PROJECT)
                .targetApiKeyId(4L)
                .permissionLevel(SharePermissionEntity.PermissionLevel.VIEW)
                .build();

        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "presupuesto.xlsx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")), List.of(), List.of(toProject));

        List<String> names = entriesOf(dir, service.createBackup("MANUAL").getFileName());

        assertThat(names).noneMatch(n -> n.contains("Compartidos conmigo"));
        assertThat(names.stream().filter(n -> n.endsWith("presupuesto.xlsx"))).hasSize(1);
    }

    @Test
    @DisplayName("the sharing grants travel in the manifest")
    void recordsSharePermissionsInTheManifest(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "presupuesto.xlsx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")), List.of(),
                List.of(grantToUser(SharePermissionEntity.ResourceType.FILE, 31L, 3L, "1004356866")));

        JsonNode manifest = manifestOf(dir, service.createBackup("MANUAL").getFileName());
        JsonNode grants = manifest.get("sharePermissionsMetadata");

        assertThat(manifest.get("totalSharePermissions").asInt()).isEqualTo(1);
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).get("resourceId").asLong()).isEqualTo(31L);
        assertThat(grants.get(0).get("targetUserId").asText()).isEqualTo("1004356866");
        assertThat(grants.get(0).get("permissionLevel").asText()).isEqualTo("VIEW");
    }

    @Test
    @DisplayName("the folder trash travels in the package with its timestamps")
    void carriesTheFolderTrash(@TempDir Path dir) throws Exception {
        FolderEntity papelera = folder(20L, null, "Eliminada", 3L);
        papelera.setDeletedAt(java.time.LocalDateTime.of(2026, 3, 1, 10, 0));
        papelera.setDeletedByUserId("111");

        BackupServiceImpl service = serviceOn(dir, List.of(), List.of(papelera), List.of(key(3L, "Proyecto X")));
        JsonNode folders = manifestOf(dir, service.createBackup("MANUAL").getFileName()).get("foldersMetadata");

        // Sin esto, restaurar una copia devolveria al espacio activo lo que alguien elimino.
        assertThat(folders.get(0).get("deletedAt").asText()).startsWith("2026-03-01T10:00");
        assertThat(folders.get(0).get("deletedByUserId").asText()).isEqualTo("111");
    }

    @Test
    @DisplayName("the power to re-share and the recipient's own trash travel with the grant")
    void carriesTheReshareFlagAndRecipientTrash(@TempDir Path dir) throws Exception {
        SharePermissionEntity grant = grantToUser(SharePermissionEntity.ResourceType.FILE, 31L, 3L, "1004356866");
        grant.setCanShare(true);
        grant.setDeletedAt(java.time.LocalDateTime.of(2026, 3, 2, 9, 30));

        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "presupuesto.xlsx", 3L)),
                List.of(), List.of(key(3L, "Proyecto X")), List.of(), List.of(grant));
        JsonNode grants = manifestOf(dir, service.createBackup("MANUAL").getFileName())
                .get("sharePermissionsMetadata");

        assertThat(grants.get(0).get("canShare").asBoolean()).isTrue();
        assertThat(grants.get(0).get("deletedAt").asText()).startsWith("2026-03-02T09:30");
    }

    @Test
    @DisplayName("the retention policy is not triggered by generating one package")
    void doesNotDeleteAnythingOnASingleBackup(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir, List.of(), List.of(), List.of());

        service.createBackup("MANUAL");

        assertThat(dir.toFile().listFiles()).hasSize(1);
    }

}
