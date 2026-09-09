package com.officeplatform.service.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FileVersionEntity;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FileVersionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;

/**
 * The version history of a file.
 *
 * <p>Saving from the editor overwrote the object in storage and the previous content was gone with
 * no trace: a formula deleted by accident at five in the afternoon had no way back. These cases pin
 * the two properties that make the history trustworthy — the previous state is archived <b>before</b>
 * anything overwrites it, and archiving can never take down the save it is protecting.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileVersionServiceTest {

    private static final Long FILE_ID = 31L;
    private static final Long API_KEY = 3L;

    @Mock private FileVersionRepository versionRepository;
    @Mock private FileRepository fileRepository;
    @Mock private StorageService storageService;
    @Mock private ActivityLogRecorder activityLogRecorder;

    private final Map<String, byte[]> objects = new LinkedHashMap<>();
    private final List<FileVersionEntity> versions = new ArrayList<>();
    private FileVersionServiceImpl service;
    private FileEntity file;

    @BeforeEach
    void setUp() {
        file = new FileEntity();
        file.setId(FILE_ID);
        file.setUuid("uuid-31");
        file.setOriginalFileName("presupuesto.xlsx");
        file.setObjectName("obj-31.xlsx");
        file.setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        objects.put("obj-31.xlsx", "contenido vigente".getBytes(StandardCharsets.UTF_8));

        lenient().when(storageService.retrieve(anyString())).thenAnswer(i -> {
            byte[] b = objects.get(i.<String>getArgument(0));
            if (b == null) throw new StorageException("no existe: " + i.getArgument(0));
            return new ByteArrayInputStream(b);
        });
        lenient().doAnswer(i -> {
            objects.put(i.getArgument(0), i.<InputStream>getArgument(1).readAllBytes());
            return null;
        }).when(storageService).store(anyString(), any(), anyLong(), anyString());
        lenient().doAnswer(i -> { objects.remove(i.<String>getArgument(0)); return null; })
                .when(storageService).delete(anyString());

        lenient().when(versionRepository.save(any(FileVersionEntity.class))).thenAnswer(i -> {
            FileVersionEntity v = i.getArgument(0);
            v.setId((long) (versions.size() + 1));
            versions.add(v);
            return v;
        });
        lenient().when(versionRepository.findFirstByFileIdOrderByVersionNumberDesc(anyLong()))
                .thenAnswer(i -> versions.stream()
                        .filter(v -> i.getArgument(0).equals(v.getFileId()))
                        .max((a, b) -> a.getVersionNumber() - b.getVersionNumber()));
        lenient().when(versionRepository.findByFileIdAndVersionNumber(anyLong(), any()))
                .thenAnswer(i -> versions.stream()
                        .filter(v -> i.getArgument(0).equals(v.getFileId()))
                        .filter(v -> i.getArgument(1).equals(v.getVersionNumber()))
                        .findFirst());
        lenient().when(versionRepository.findAllByFileId(anyLong()))
                .thenAnswer(i -> versions.stream()
                        .filter(v -> i.getArgument(0).equals(v.getFileId())).toList());
        lenient().when(fileRepository.findById(anyLong())).thenReturn(Optional.of(file));
        lenient().when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));

        service = new FileVersionServiceImpl(versionRepository, fileRepository,
                storageService, activityLogRecorder);
    }

    private String contentOf(String objectName) {
        return new String(objects.get(objectName), StandardCharsets.UTF_8);
    }

    // ── archivado ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("archiving copies the current content to a version of its own")
    void archivesTheCurrentContent() {
        FileVersionEntity v = service.archiveCurrent(file, "111", "Ana", "guardado");

        assertThat(v.getVersionNumber()).isEqualTo(1);
        assertThat(v.getSize()).isEqualTo("contenido vigente".length());
        assertThat(contentOf(v.getObjectName())).isEqualTo("contenido vigente");
        // El objeto vigente no se toca: archivar es copiar, no mover.
        assertThat(contentOf("obj-31.xlsx")).isEqualTo("contenido vigente");
    }

    @Test
    @DisplayName("version numbers are consecutive per file and never reused")
    void numbersVersionsConsecutively() {
        service.archiveCurrent(file, "111", "Ana", "uno");
        objects.put("obj-31.xlsx", "segundo estado".getBytes(StandardCharsets.UTF_8));
        FileVersionEntity segunda = service.archiveCurrent(file, "111", "Ana", "dos");

        assertThat(segunda.getVersionNumber()).isEqualTo(2);
        assertThat(versions).hasSize(2);
        assertThat(contentOf(versions.get(0).getObjectName())).isEqualTo("contenido vigente");
        assertThat(contentOf(versions.get(1).getObjectName())).isEqualTo("segundo estado");
    }

    @Test
    @DisplayName("a storage failure while archiving never takes down the save it protects")
    void neverBreaksTheSave() {
        lenient().when(storageService.retrieve("obj-31.xlsx"))
                .thenThrow(new StorageException("MinIO caido"));

        // Perder una version es lamentable; perder el trabajo que la persona acaba de guardar es
        // inaceptable. Por eso devuelve null en vez de propagar.
        assertThat(service.archiveCurrent(file, "111", "Ana", "guardado")).isNull();
        assertThat(versions).isEmpty();
    }

    @Test
    @DisplayName("an empty object is not archived, so the history stays free of noise")
    void skipsAnEmptyObject() {
        objects.put("obj-31.xlsx", new byte[0]);

        assertThat(service.archiveCurrent(file, "111", "Ana", "guardado")).isNull();
        assertThat(versions).isEmpty();
    }

    @Test
    @DisplayName("a file with no object yet has nothing to archive")
    void skipsAFileWithNoObject() {
        FileEntity nuevo = new FileEntity();
        nuevo.setId(99L);

        assertThat(service.archiveCurrent(nuevo, "111", "Ana", "x")).isNull();
        assertThat(service.archiveCurrent(null, "111", "Ana", "x")).isNull();
    }

    // ── restauración ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("restoring a version brings its content back as the live one")
    void restoresTheContentOfAVersion() {
        service.archiveCurrent(file, "111", "Ana", "original");
        objects.put("obj-31.xlsx", "alguien borro la formula".getBytes(StandardCharsets.UTF_8));

        service.restoreVersion(FILE_ID, 1, API_KEY, "111", "Ana");

        assertThat(contentOf("obj-31.xlsx")).isEqualTo("contenido vigente");
        assertThat(file.getSize()).isEqualTo("contenido vigente".length());
    }

    @Test
    @DisplayName("restoring archives the state it replaces, so it is itself undoable")
    void restoringIsUndoable() {
        service.archiveCurrent(file, "111", "Ana", "original");
        objects.put("obj-31.xlsx", "estado equivocado".getBytes(StandardCharsets.UTF_8));

        service.restoreVersion(FILE_ID, 1, API_KEY, "111", "Ana");

        // La v2 guarda lo que habia justo antes de restaurar: una restauracion equivocada se
        // deshace restaurando la version que ella misma creo.
        assertThat(versions).hasSize(2);
        assertThat(versions.get(1).getVersionNumber()).isEqualTo(2);
        assertThat(contentOf(versions.get(1).getObjectName())).isEqualTo("estado equivocado");
        assertThat(versions.get(1).getComment()).contains("previo a restaurar");
    }

    @Test
    @DisplayName("restoring a version that does not exist fails without touching the file")
    void failsOnAMissingVersion() {
        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, 7, API_KEY, "111", "Ana"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("versión 7");

        assertThat(contentOf("obj-31.xlsx")).isEqualTo("contenido vigente");
        verify(fileRepository, never()).save(any(FileEntity.class));
    }

    // ── descarga y purga ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a version can be downloaded without disturbing the live file")
    void downloadsAVersion() throws Exception {
        service.archiveCurrent(file, "111", "Ana", "original");
        objects.put("obj-31.xlsx", "estado nuevo".getBytes(StandardCharsets.UTF_8));

        try (InputStream in = service.downloadVersion(FILE_ID, 1)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("contenido vigente");
        }
        assertThat(contentOf("obj-31.xlsx")).isEqualTo("estado nuevo");
    }

    @Test
    @DisplayName("purging a file takes its history and its binaries with it")
    void purgesTheHistory() {
        service.archiveCurrent(file, "111", "Ana", "uno");
        String archivado = versions.get(0).getObjectName();

        service.purgeVersions(FILE_ID);

        // Dejar el historial huerfano acumularia binarios que nadie puede volver a alcanzar.
        assertThat(objects).doesNotContainKey(archivado);
        verify(versionRepository).deleteAll(any());
    }

    @Test
    @DisplayName("a binary already gone from storage does not block purging the history")
    void purgesEvenIfTheBinaryIsGone() {
        service.archiveCurrent(file, "111", "Ana", "uno");
        lenient().doThrow(new StorageException("no esta")).when(storageService).delete(anyString());

        service.purgeVersions(FILE_ID);

        verify(versionRepository).deleteAll(any());
    }

}
