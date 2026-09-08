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
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
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
    @Mock private StorageService storageService;
    @Mock private BackupRestoreService backupRestoreService;

    private BackupServiceImpl serviceOn(Path dir, List<FileEntity> files,
                                        List<FolderEntity> folders, List<ApiKeyEntity> keys) {
        lenient().when(fileRepository.findAll()).thenReturn(files);
        lenient().when(folderRepository.findAll()).thenReturn(folders);
        lenient().when(apiKeyRepository.findAll()).thenReturn(keys);
        lenient().when(knownUserRepository.findAll()).thenReturn(List.of());
        lenient().when(storageService.retrieve(anyString())).thenAnswer(i ->
                new ByteArrayInputStream(("bytes de " + i.getArgument(0)).getBytes(StandardCharsets.UTF_8)));

        return new BackupServiceImpl(fileRepository, folderRepository, apiKeyRepository,
                knownUserRepository, storageService, dir.toString(),
                false, 24, 10, "", false, backupRestoreService);
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
                "archivos/Proyecto X/Informes/Semana 36/informe.docx",
                "archivos/Proyecto X/suelto.xlsx");
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

        assertThat(manifest.get("layoutVersion").asInt()).isEqualTo(2);
        assertThat(file.get("zipEntryPath").asText())
                .isEqualTo("archivos/Proyecto X/Informes/Semana 36/informe.docx");
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
                "archivos/Proyecto X/informe.docx",
                "archivos/Proyecto X/informe (2).docx");
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

        assertThat(names).contains("archivos/Proyecto _X_/Ventas_Norte/informe_ final_.docx");
    }

    @Test
    @DisplayName("a file whose project is missing still gets a place in the package")
    void placesFilesWithNoResolvableProject(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir,
                List.of(file(31L, null, "huerfano.docx", 99L)),
                List.of(), List.of());

        BackupInfoResponse info = service.createBackup("MANUAL");

        assertThat(entriesOf(dir, info.getFileName()))
                .contains("archivos/proyecto-99/huerfano.docx");
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
                .anyMatch(n -> n.startsWith("archivos/Proyecto X/") && n.endsWith("informe.docx"));
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
        assertThat(entriesOf(dir, info.getFileName())).contains("archivos/Proyecto X/bueno.docx");
        // El manifest igual lo declara: la fila existe aunque su binario se haya perdido antes.
        JsonNode files = manifestOf(dir, info.getFileName()).get("filesMetadata");
        assertThat(files).hasSize(2);
    }

    @Test
    @DisplayName("the retention policy is not triggered by generating one package")
    void doesNotDeleteAnythingOnASingleBackup(@TempDir Path dir) throws Exception {
        BackupServiceImpl service = serviceOn(dir, List.of(), List.of(), List.of());

        service.createBackup("MANUAL");

        assertThat(dir.toFile().listFiles()).hasSize(1);
    }

}
