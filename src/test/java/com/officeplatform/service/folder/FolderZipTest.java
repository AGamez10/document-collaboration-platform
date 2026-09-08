package com.officeplatform.service.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;

/**
 * Packaging a folder as a ZIP.
 *
 * <p>Both queries used to be scoped to the caller's project, which emptied the archive for any
 * folder shared across projects: a shared folder holds subfolders and files belonging to several
 * api_key_ids at once, so filtering by the caller's returned nothing and produced a valid but
 * empty 22-byte ZIP. These cases pin the contents, the nesting, and the empty directories that
 * would otherwise vanish without trace.
 */
@ExtendWith(MockitoExtension.class)
class FolderZipTest {

    private static final Long CALLER_API_KEY = 7L;
    private static final Long OTHER_API_KEY = 99L;

    @Mock private FolderRepository folderRepository;
    @Mock private FileRepository fileRepository;
    @Mock private ActivityLogRecorder activityLogRecorder;
    @Mock private StorageService storageService;

    private FolderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FolderServiceImpl(folderRepository, fileRepository,
                activityLogRecorder, storageService);
        lenient().when(storageService.retrieve(anyString()))
                .thenAnswer(inv -> new ByteArrayInputStream(
                        ("contenido de " + inv.getArgument(0)).getBytes(StandardCharsets.UTF_8)));
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
        f.setFolderId(folderId);
        f.setOriginalFileName(name);
        f.setObjectName("obj-" + id);
        f.setApiKeyId(apiKeyId);
        return f;
    }

    /** Reads back the entries of the produced archive. */
    private List<ZipEntry> entriesOf(byte[] zip) throws Exception {
        List<ZipEntry> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream sink = new ByteArrayOutputStream();
                in.transferTo(sink);
                entries.add(entry);
                in.closeEntry();
            }
        }
        return entries;
    }

    private List<String> namesOf(byte[] zip) throws Exception {
        return entriesOf(zip).stream().map(ZipEntry::getName).toList();
    }

    @Test
    @DisplayName("files and nested subfolders are packaged with their paths")
    void packagesTheWholeTree() throws Exception {
        FolderEntity root = folder(1L, null, "Raiz", CALLER_API_KEY);
        FolderEntity sub = folder(2L, 1L, "Sub", CALLER_API_KEY);

        when(folderRepository.findById(1L)).thenReturn(Optional.of(root));
        when(folderRepository.findAllByParentId(1L)).thenReturn(List.of(sub));
        when(folderRepository.findAllByParentId(2L)).thenReturn(List.of());
        when(fileRepository.findAllByFolderIdAndDeletedAtIsNull(1L))
                .thenReturn(List.of(file(10L, 1L, "informe.docx", CALLER_API_KEY)));
        when(fileRepository.findAllByFolderIdAndDeletedAtIsNull(2L))
                .thenReturn(List.of(file(11L, 2L, "hoja.xlsx", CALLER_API_KEY)));

        List<String> names = namesOf(service.downloadFolderAsZip(1L, CALLER_API_KEY, "111", "Ana"));

        assertThat(names).contains("Raiz/informe.docx", "Raiz/Sub/hoja.xlsx");
    }

    @Test
    @DisplayName("a folder shared across projects still packages its contents")
    void packagesContentBelongingToAnotherProject() throws Exception {
        FolderEntity root = folder(1L, null, "Compartida", OTHER_API_KEY);
        FolderEntity sub = folder(2L, 1L, "SubAjena", OTHER_API_KEY);

        when(folderRepository.findById(1L)).thenReturn(Optional.of(root));
        when(folderRepository.findAllByParentId(1L)).thenReturn(List.of(sub));
        when(folderRepository.findAllByParentId(2L)).thenReturn(List.of());
        // Files belong to yet another project — exactly what sharing across projects produces.
        when(fileRepository.findAllByFolderIdAndDeletedAtIsNull(1L))
                .thenReturn(List.of(file(10L, 1L, "ajeno.docx", OTHER_API_KEY)));
        when(fileRepository.findAllByFolderIdAndDeletedAtIsNull(2L))
                .thenReturn(List.of(file(11L, 2L, "otro.xlsx", CALLER_API_KEY)));

        // The caller belongs to a different project and must still get everything.
        List<String> names = namesOf(service.downloadFolderAsZip(1L, CALLER_API_KEY, "222", "Beto"));

        assertThat(names).contains("Compartida/ajeno.docx", "Compartida/SubAjena/otro.xlsx");
    }

    @Test
    @DisplayName("an empty subfolder appears as a directory entry instead of vanishing")
    void keepsEmptyFolders() throws Exception {
        FolderEntity root = folder(1L, null, "Raiz", CALLER_API_KEY);
        FolderEntity empty = folder(2L, 1L, "Vacia", CALLER_API_KEY);

        when(folderRepository.findById(1L)).thenReturn(Optional.of(root));
        when(folderRepository.findAllByParentId(1L)).thenReturn(List.of(empty));
        when(folderRepository.findAllByParentId(2L)).thenReturn(List.of());
        when(fileRepository.findAllByFolderIdAndDeletedAtIsNull(anyLong())).thenReturn(List.of());

        List<String> names = namesOf(service.downloadFolderAsZip(1L, CALLER_API_KEY, "111", "Ana"));

        // Without the directory entry the user believes the folder was lost.
        assertThat(names).contains("Raiz/", "Raiz/Vacia/");
    }

    @Test
    @DisplayName("file contents are actually written, not just their names")
    void writesTheBytes() throws Exception {
        FolderEntity root = folder(1L, null, "Raiz", CALLER_API_KEY);
        when(folderRepository.findById(1L)).thenReturn(Optional.of(root));
        when(folderRepository.findAllByParentId(1L)).thenReturn(List.of());
        when(fileRepository.findAllByFolderIdAndDeletedAtIsNull(1L))
                .thenReturn(List.of(file(10L, 1L, "informe.docx", CALLER_API_KEY)));

        byte[] zip = service.downloadFolderAsZip(1L, CALLER_API_KEY, "111", "Ana");

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().endsWith("informe.docx")) {
                    ByteArrayOutputStream sink = new ByteArrayOutputStream();
                    in.transferTo(sink);
                    assertThat(sink.toString(StandardCharsets.UTF_8)).isEqualTo("contenido de obj-10");
                    return;
                }
            }
        }
        throw new AssertionError("El archivo no quedó dentro del ZIP");
    }

    @Test
    @DisplayName("a cycle in the parent chain does not hang the export")
    void survivesACycle() throws Exception {
        FolderEntity a = folder(1L, null, "A", CALLER_API_KEY);
        FolderEntity b = folder(2L, 1L, "B", CALLER_API_KEY);

        when(folderRepository.findById(1L)).thenReturn(Optional.of(a));
        // Corrupted chain: B claims A as a child again.
        when(folderRepository.findAllByParentId(1L)).thenReturn(List.of(b));
        when(folderRepository.findAllByParentId(2L)).thenReturn(List.of(a));
        when(fileRepository.findAllByFolderIdAndDeletedAtIsNull(anyLong())).thenReturn(List.of());

        List<String> names = namesOf(service.downloadFolderAsZip(1L, CALLER_API_KEY, "111", "Ana"));

        assertThat(names).contains("A/", "A/B/");
    }

}
