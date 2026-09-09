package com.officeplatform.service.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
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
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;

/**
 * The folder trash.
 *
 * <p>Deleting a folder used to remove it from the database and clear {@code folder_id} on every file
 * inside, because a file pointing at a row that no longer existed became invisible: absent from the
 * root listing and from every folder listing at once. The cost was that the original location was
 * destroyed with the folder, so restoring a file always dropped it at the root and the folder itself
 * could never come back. These cases pin the soft delete that preserves the location, the restore
 * that uses it, and the one situation the restore cannot take literally — a parent that is still in
 * the trash.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FolderTrashTest {

    private static final Long API_KEY = 7L;

    @Mock private FolderRepository folderRepository;
    @Mock private FileRepository fileRepository;
    @Mock private ActivityLogRecorder activityLogRecorder;
    @Mock private StorageService storageService;

    private FolderServiceImpl service;

    private final Map<Long, FolderEntity> folders = new LinkedHashMap<>();
    private final Map<Long, FileEntity> files = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        service = new FolderServiceImpl(folderRepository, fileRepository,
                activityLogRecorder, storageService);

        lenient().when(folderRepository.findById(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(folders.get(i.<Long>getArgument(0))));
        lenient().when(folderRepository.findByIdAndApiKeyId(anyLong(), anyLong()))
                .thenAnswer(i -> Optional.ofNullable(folders.get(i.<Long>getArgument(0)))
                        .filter(f -> i.getArgument(1).equals(f.getApiKeyId())));
        lenient().when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> {
            FolderEntity f = i.getArgument(0);
            folders.put(f.getId(), f);
            return f;
        });
        lenient().when(folderRepository.saveAll(any())).thenAnswer(i -> {
            Iterable<FolderEntity> all = i.getArgument(0);
            all.forEach(f -> folders.put(f.getId(), f));
            return all;
        });
        lenient().when(folderRepository.findAllByParentId(anyLong()))
                .thenAnswer(i -> childrenOf(i.getArgument(0), null));
        lenient().when(folderRepository.findAllByParentIdAndDeletedAtIsNotNull(anyLong()))
                .thenAnswer(i -> childrenOf(i.getArgument(0), true));
        lenient().when(folderRepository.findAllByApiKeyIdAndParentIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenAnswer(i -> childrenOf(i.getArgument(1), false));

        lenient().when(fileRepository.findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenAnswer(i -> filesOf(i.getArgument(1), false));
        lenient().when(fileRepository.findAllByFolderIdAndDeletedAtIsNotNull(anyLong()))
                .thenAnswer(i -> filesOf(i.getArgument(0), true));
        lenient().when(fileRepository.findAllByFolderId(anyLong()))
                .thenAnswer(i -> filesOf(i.getArgument(0), null));
        lenient().when(fileRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** @param deleted null = cualquiera, true = solo en papelera, false = solo vivas */
    private List<FolderEntity> childrenOf(Long parentId, Boolean deleted) {
        List<FolderEntity> out = new ArrayList<>();
        for (FolderEntity f : folders.values()) {
            if (!parentId.equals(f.getParentId())) continue;
            if (deleted == null || deleted == (f.getDeletedAt() != null)) out.add(f);
        }
        return out;
    }

    private List<FileEntity> filesOf(Long folderId, Boolean deleted) {
        List<FileEntity> out = new ArrayList<>();
        for (FileEntity f : files.values()) {
            if (!folderId.equals(f.getFolderId())) continue;
            if (deleted == null || deleted == (f.getDeletedAt() != null)) out.add(f);
        }
        return out;
    }

    private FolderEntity folder(Long id, Long parentId, String name) {
        FolderEntity f = new FolderEntity();
        f.setId(id);
        f.setParentId(parentId);
        f.setName(name);
        f.setApiKeyId(API_KEY);
        f.setUuid("uuid-" + id);
        folders.put(id, f);
        return f;
    }

    private FileEntity file(Long id, Long folderId, String name) {
        FileEntity f = new FileEntity();
        f.setId(id);
        f.setFolderId(folderId);
        f.setOriginalFileName(name);
        f.setObjectName("obj-" + id);
        f.setApiKeyId(API_KEY);
        files.put(id, f);
        return f;
    }

    // ── borrado lógico ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleting a folder marks the whole subtree instead of destroying it")
    void softDeletesTheSubtree() {
        folder(1L, null, "Informes");
        folder(2L, 1L, "Semana 36");

        service.deleteFolder(1L, API_KEY, "111", "Ana");

        assertThat(folders.get(1L).getDeletedAt()).isNotNull();
        assertThat(folders.get(2L).getDeletedAt()).isNotNull();
        assertThat(folders.get(1L).getDeletedByUserId()).isEqualTo("111");
        // La fila sobrevive: es lo que permite restaurar la carpeta y saber dónde vivían sus
        // archivos. El borrado físico destruía esa información para siempre.
        verify(folderRepository, org.mockito.Mockito.never()).deleteAll(any());
    }

    @Test
    @DisplayName("a file sent to the trash with its folder keeps remembering where it lived")
    void keepsTheOriginalFolderOnTheFile() {
        folder(1L, null, "Informes");
        file(10L, 1L, "informe.docx");

        service.deleteFolder(1L, API_KEY, "111", "Ana");

        assertThat(files.get(10L).getDeletedAt()).isNotNull();
        // Este campo se ponía en null: por eso restaurar dejaba todo en la raíz.
        assertThat(files.get(10L).getFolderId()).isEqualTo(1L);
    }

    // ── restauración ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("restoring a folder brings back its subtree and the files inside it")
    void restoresTheSubtreeAndItsFiles() {
        folder(1L, null, "Informes");
        folder(2L, 1L, "Semana 36");
        file(10L, 2L, "informe.docx");
        service.deleteFolder(1L, API_KEY, "111", "Ana");

        service.restoreFolder(1L, API_KEY, "111", "Ana");

        assertThat(folders.get(1L).getDeletedAt()).isNull();
        assertThat(folders.get(2L).getDeletedAt()).isNull();
        assertThat(folders.get(2L).getParentId()).isEqualTo(1L);
        // El archivo vuelve con su carpeta: fue a la papelera por arrastre, no por decisión propia.
        assertThat(files.get(10L).getDeletedAt()).isNull();
        assertThat(files.get(10L).getFolderId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("a folder whose parent is still in the trash anchors to the first living ancestor")
    void anchorsToTheFirstLivingAncestor() {
        folder(1L, null, "Raiz");
        folder(2L, 1L, "Intermedia");
        folder(3L, 2L, "Hoja");
        // Se elimina desde la raíz: las tres quedan en la papelera.
        service.deleteFolder(1L, API_KEY, "111", "Ana");

        // Se restaura SOLO la hoja. Colgarla de un padre que sigue eliminado la haría invisible.
        service.restoreFolder(3L, API_KEY, "111", "Ana");

        assertThat(folders.get(3L).getDeletedAt()).isNull();
        assertThat(folders.get(3L).getParentId()).isNull();
        assertThat(folders.get(1L).getDeletedAt()).isNotNull();
        assertThat(folders.get(2L).getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("restoring a folder whose parent is alive keeps it exactly where it was")
    void keepsTheOriginalParentWhenItIsAlive() {
        folder(1L, null, "Raiz");
        folder(2L, 1L, "Sub");
        // Solo la subcarpeta va a la papelera; la raíz sigue viva.
        service.deleteFolder(2L, API_KEY, "111", "Ana");

        service.restoreFolder(2L, API_KEY, "111", "Ana");

        assertThat(folders.get(2L).getParentId()).isEqualTo(1L);
        assertThat(folders.get(2L).getDeletedAt()).isNull();
    }

    // ── purga ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("purging a folder removes its subtree, its files and their binaries")
    void purgesTheWholeTree() {
        folder(1L, null, "Informes");
        folder(2L, 1L, "Semana 36");
        file(10L, 2L, "informe.docx");
        service.deleteFolder(1L, API_KEY, "111", "Ana");

        service.purgeFolder(1L, API_KEY, "111", "Ana");

        verify(storageService).delete("obj-10");
        verify(fileRepository).delete(files.get(10L));
        verify(folderRepository).deleteAll(any());
    }

    @Test
    @DisplayName("a binary already gone from storage does not block emptying the trash")
    void purgesEvenIfTheBinaryIsAlreadyGone() {
        folder(1L, null, "Informes");
        file(10L, 1L, "perdido.docx");
        service.deleteFolder(1L, API_KEY, "111", "Ana");
        org.mockito.Mockito.doThrow(new RuntimeException("no está en MinIO"))
                .when(storageService).delete("obj-10");

        service.purgeFolder(1L, API_KEY, "111", "Ana");

        // La fila se va igual: si un objeto perdido pudiera frenar la purga, la papelera
        // quedaría imposible de vaciar, que es exactamente el síntoma reportado.
        verify(fileRepository).delete(files.get(10L));
        verify(folderRepository).deleteAll(any());
    }

    @Test
    @DisplayName("a cycle in the parent chain does not hang the restore")
    void survivesACycleWhenRestoring() {
        FolderEntity a = folder(1L, null, "A");
        FolderEntity b = folder(2L, 1L, "B");
        a.setDeletedAt(LocalDateTime.now());
        b.setDeletedAt(LocalDateTime.now());
        // Cadena corrupta: A dice que su padre es B, y B que su padre es A.
        a.setParentId(2L);

        service.restoreFolder(1L, API_KEY, "111", "Ana");

        assertThat(folders.get(1L).getDeletedAt()).isNull();
    }

}
