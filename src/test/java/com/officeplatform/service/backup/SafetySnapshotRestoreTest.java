package com.officeplatform.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.officeplatform.dto.response.BackupRestoreResponse;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FileVersionEntity;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FileVersionRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.search.FileIndexingService;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.service.version.FileVersionServiceImpl;

/**
 * Restaurar un respaldo sobre un sistema en uso no puede borrar el trabajo de nadie.
 *
 * <p>El respaldo es de las 11:00 y alguien guardó cambios a las 11:50. Restaurarlo pisaba ese
 * trabajo sin dejar rastro: no iba a la papelera, no quedaba en el historial, no existía en ningún
 * lado. La persona volvía de almorzar con su documento retrocedido una hora y sin explicación.
 *
 * <p>Estos casos recorren el camino completo: se congela el estado vivo como versión, el archivo
 * queda con lo que trae el respaldo, y desde el historial se recupera lo perdido con un clic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SafetySnapshotRestoreTest {

    private static final String BUCKET = "office-platform";
    private static final String UUID_ARCHIVO = "uuid-archivo";
    private static final String OBJETO = "obj-31.docx";
    private static final String TRABAJO_DE_LAS_11_50 = "lo que escribio el usuario a las 11:50";
    private static final String ESTADO_DEL_RESPALDO = "el estado congelado a las 11:00";

    @Mock private FileRepository fileRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private KnownUserRepository knownUserRepository;
    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private FileVersionRepository fileVersionRepository;
    @Mock private FileIndexingService fileIndexingService;
    @Mock private StorageService storageService;
    @Mock private ActivityLogRecorder activityLogRecorder;
    @Mock private PlatformTransactionManager transactionManager;

    private final Map<String, byte[]> almacenamiento = new LinkedHashMap<>();
    private final List<FileVersionEntity> versiones = new ArrayList<>();

    private BackupRestoreService service;
    private FileVersionServiceImpl versionService;
    private FileEntity archivoVivo;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        // El archivo vivo: el usuario lo guardó a las 11:50, después de la hora del respaldo.
        archivoVivo = new FileEntity();
        archivoVivo.setId(31L);
        archivoVivo.setUuid(UUID_ARCHIVO);
        archivoVivo.setOriginalFileName("informe.docx");
        archivoVivo.setFileName(OBJETO);
        archivoVivo.setObjectName(OBJETO);
        archivoVivo.setBucket(BUCKET);
        archivoVivo.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        archivoVivo.setSize((long) TRABAJO_DE_LAS_11_50.length());
        archivoVivo.setApiKeyId(3L);
        archivoVivo.setCreatedByName("Ana");
        archivoVivo.setCreatedByUserId("111");
        archivoVivo.setUpdatedByName("Ana");
        archivoVivo.setUpdatedAt(LocalDateTime.of(2026, 9, 22, 11, 50));
        archivoVivo.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        almacenamiento.put(OBJETO, TRABAJO_DE_LAS_11_50.getBytes(StandardCharsets.UTF_8));

        lenient().when(fileRepository.findByUuid(anyString()))
                .thenAnswer(i -> UUID_ARCHIVO.equals(i.getArgument(0))
                        ? Optional.of(archivoVivo) : Optional.empty());
        lenient().when(fileRepository.findById(anyLong())).thenReturn(Optional.of(archivoVivo));
        lenient().when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(fileRepository.findIdsPendingIndexing()).thenReturn(List.of());
        lenient().when(apiKeyRepository.findAll()).thenReturn(List.of());

        lenient().when(storageService.retrieve(anyString())).thenAnswer(i -> {
            byte[] b = almacenamiento.get(i.<String>getArgument(0));
            if (b == null) {
                throw new IllegalStateException("no existe: " + i.getArgument(0));
            }
            return new ByteArrayInputStream(b);
        });
        lenient().doAnswer(i -> {
            almacenamiento.put(i.getArgument(0), i.<InputStream>getArgument(1).readAllBytes());
            return null;
        }).when(storageService).store(anyString(), any(), anyLong(), anyString());

        AtomicLong versionSeq = new AtomicLong(500);
        lenient().when(fileVersionRepository.save(any(FileVersionEntity.class))).thenAnswer(i -> {
            FileVersionEntity v = i.getArgument(0);
            if (v.getId() == null) {
                v.setId(versionSeq.incrementAndGet());
                versiones.add(v);
            }
            return v;
        });
        lenient().when(fileVersionRepository.findFirstByFileIdOrderByVersionNumberDesc(anyLong()))
                .thenAnswer(i -> versiones.stream()
                        .filter(v -> i.getArgument(0).equals(v.getFileId()))
                        .max((a, b) -> a.getVersionNumber() - b.getVersionNumber()));
        lenient().when(fileVersionRepository.findByFileIdAndVersionNumber(anyLong(), anyInt()))
                .thenAnswer(i -> versiones.stream()
                        .filter(v -> i.getArgument(0).equals(v.getFileId()))
                        .filter(v -> i.getArgument(1).equals(v.getVersionNumber()))
                        .findFirst());
        lenient().when(fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(anyLong()))
                .thenAnswer(i -> versiones.stream()
                        .filter(v -> i.getArgument(0).equals(v.getFileId()))
                        .sorted((a, b) -> b.getVersionNumber() - a.getVersionNumber())
                        .toList());

        service = new BackupRestoreService(fileRepository, folderRepository, apiKeyRepository,
                knownUserRepository, sharePermissionRepository, fileVersionRepository,
                fileIndexingService, storageService, objectMapper, transactionManager, BUCKET);

        versionService = new FileVersionServiceImpl(fileVersionRepository, fileRepository,
                storageService, activityLogRecorder, 20);
    }

    /** Paquete con el archivo tal como estaba a las 11:00. */
    private byte[] paqueteDeLas11(String objectNameEnElRespaldo) throws Exception {
        Map<String, Object> archivo = new HashMap<>();
        archivo.put("id", 31L);
        archivo.put("uuid", UUID_ARCHIVO);
        archivo.put("originalFileName", "informe.docx");
        archivo.put("fileName", objectNameEnElRespaldo);
        archivo.put("objectName", objectNameEnElRespaldo);
        archivo.put("bucket", BUCKET);
        archivo.put("mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        archivo.put("size", ESTADO_DEL_RESPALDO.length());
        archivo.put("apiKeyId", 3L);
        archivo.put("createdByName", "Ana");
        archivo.put("updatedAt", "2026-09-22T11:00:00");

        Map<String, Object> manifest = new HashMap<>();
        manifest.put("layoutVersion", 4);
        manifest.put("apiKeysMetadata", List.of());
        manifest.put("knownUsersMetadata", List.of());
        manifest.put("foldersMetadata", List.of());
        manifest.put("filesMetadata", List.of(archivo));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(new ObjectMapper().writeValueAsBytes(manifest));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("files/" + objectNameEnElRespaldo));
            zos.write(ESTADO_DEL_RESPALDO.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return out.toByteArray();
    }

    private String contenidoDe(String objectName) {
        return new String(almacenamiento.get(objectName), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the live work is frozen as a version instead of being overwritten")
    void freezesTheLiveStateBeforeApplyingTheBackup() throws Exception {
        BackupRestoreResponse resultado = service.restore(
                new ByteArrayInputStream(paqueteDeLas11(OBJETO)), "backup_manual_1100.zip");

        // a) El archivo principal queda con lo que traia el respaldo.
        assertThat(contenidoDe(archivoVivo.getObjectName())).isEqualTo(ESTADO_DEL_RESPALDO);

        // b) El trabajo de las 11:50 existe como version, y dice por que existe.
        assertThat(resultado.getSafetySnapshotsCreated()).isEqualTo(1);
        assertThat(versiones).hasSize(1);
        FileVersionEntity resguardo = versiones.get(0);
        assertThat(resguardo.getComment())
                .contains("Resguardo local previo a restaurar")
                .contains("backup_manual_1100.zip");
        assertThat(contenidoDe(resguardo.getObjectName())).isEqualTo(TRABAJO_DE_LAS_11_50);
        // La autoria y la fecha son las del trabajo que se salva, no las del momento de restaurar.
        assertThat(resguardo.getCreatedByName()).isEqualTo("Ana");
        assertThat(resguardo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 11, 50));
    }

    @Test
    @DisplayName("one click on Restore brings the lost work back")
    void restoringTheSnapshotRecoversTheWork() throws Exception {
        service.restore(new ByteArrayInputStream(paqueteDeLas11(OBJETO)), "backup_manual_1100.zip");
        FileVersionEntity resguardo = versiones.get(0);

        versionService.restoreVersion(31L, resguardo.getVersionNumber(), 3L, "111", "Ana");

        // c) El archivo principal vuelve a tener el trabajo de las 11:50, que es el punto de todo.
        assertThat(contenidoDe(archivoVivo.getObjectName())).isEqualTo(TRABAJO_DE_LAS_11_50);
    }

    @Test
    @DisplayName("an identical file is not duplicated into the history")
    void skipsTheSnapshotWhenNothingChanged() throws Exception {
        // Mismo tamaño, misma clave y sin edicion posterior: no hay trabajo que salvar, y copiar
        // el binario igual solo gastaria almacenamiento.
        archivoVivo.setSize((long) ESTADO_DEL_RESPALDO.length());
        archivoVivo.setUpdatedAt(LocalDateTime.of(2026, 9, 22, 11, 0));
        almacenamiento.put(OBJETO, ESTADO_DEL_RESPALDO.getBytes(StandardCharsets.UTF_8));

        BackupRestoreResponse resultado = service.restore(
                new ByteArrayInputStream(paqueteDeLas11(OBJETO)), "backup_manual_1100.zip");

        assertThat(resultado.getSafetySnapshotsCreated()).isZero();
        assertThat(versiones).isEmpty();
    }

    @Test
    @DisplayName("the snapshot number clears both the local history and the one in the package")
    void numbersTheSnapshotAboveEverythingElse() throws Exception {
        // Una version local en 2 y el paquete trayendo hasta la 5: el resguardo tiene que quedar
        // en 6, o la version que el propio respaldo restaura despues chocaria con la clave unica.
        versiones.add(FileVersionEntity.builder().id(1L).fileId(31L).versionNumber(2)
                .objectName("versions/" + UUID_ARCHIVO + "/v2.docx").build());

        Map<String, Object> version5 = new HashMap<>();
        version5.put("fileId", 31L);
        version5.put("versionNumber", 5);
        version5.put("objectName", "versions/" + UUID_ARCHIVO + "/v5.docx");

        Map<String, Object> manifest = new ObjectMapper().readValue(
                new String(extraerManifest(paqueteDeLas11(OBJETO)), StandardCharsets.UTF_8), Map.class);
        manifest.put("fileVersionsMetadata", List.of(version5));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(new ObjectMapper().writeValueAsBytes(manifest));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("files/" + OBJETO));
            zos.write(ESTADO_DEL_RESPALDO.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        service.restore(new ByteArrayInputStream(out.toByteArray()), "backup_manual_1100.zip");

        FileVersionEntity resguardo = versiones.stream()
                .filter(v -> v.getComment() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se creó el resguardo"));
        assertThat(resguardo.getVersionNumber()).isEqualTo(6);
    }

    private byte[] extraerManifest(byte[] zip) throws Exception {
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    return in.readAllBytes();
                }
            }
        }
        throw new AssertionError("el paquete no trae manifest");
    }
}
