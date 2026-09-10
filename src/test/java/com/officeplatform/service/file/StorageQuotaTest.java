package com.officeplatform.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import com.officeplatform.dto.request.UploadFileRequest;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.exception.StorageQuotaExceededException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.service.version.FileVersionService;

/**
 * The per-project storage quota.
 *
 * <p>Object storage has no opinion about who fills it: without a ceiling, one project ingesting a
 * shared drive can consume the disk every other project depends on, and the first symptom is
 * everybody failing to upload at once. These cases pin the ceiling itself, and the two decisions
 * that keep it from becoming a nuisance: a project nobody configured stays unlimited, and the trash
 * does not count against the quota.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageQuotaTest {

    private static final Long PROJECT = 3L;
    private static final long MB = 1024L * 1024L;

    @Mock private FileRepository fileRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private StorageService storageService;
    @Mock private FileVersionService fileVersionService;
    @Mock private ActivityLogRecorder activityLogRecorder;

    private FileServiceImpl service;
    private ApiKeyEntity project;

    @BeforeEach
    void setUp() {
        project = new ApiKeyEntity();
        project.setId(PROJECT);
        project.setName("Proyecto X");

        lenient().when(apiKeyRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        lenient().when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> {
            FileEntity f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        service = new FileServiceImpl(fileRepository, apiKeyRepository, folderRepository,
                sharePermissionRepository, storageService, fileVersionService, activityLogRecorder,
                "office-platform",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /** Sube un .docx del tamaño pedido, con el tipo que la validación de MIME acepta. */
    private void upload(int sizeBytes) {
        MockMultipartFile file = new MockMultipartFile("file", "informe.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[sizeBytes]);
        UploadFileRequest request = new UploadFileRequest();
        request.setOriginalFileName("informe.docx");
        service.uploadFile(file, request, PROJECT, null, "111", "Ana", "shared");
    }

    @Test
    @DisplayName("a project with no quota configured keeps uploading without a ceiling")
    void anUnconfiguredProjectHasNoCeiling() {
        // Es como funcionaron todos hasta ahora: imponer un tope retroactivo habria dejado gente
        // sin poder subir sin entender por que.
        assertThatCode(() -> upload(10 * (int) MB)).doesNotThrowAnyException();
        verify(fileRepository, never()).sumSizeByApiKeyIdAndDeletedAtIsNull(anyLong());
    }

    @Test
    @DisplayName("an upload that fits in the remaining space goes through")
    void allowsAnUploadThatFits() {
        project.setStorageQuotaBytes(100 * MB);
        lenient().when(fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT)).thenReturn(40 * MB);

        assertThatCode(() -> upload(10 * (int) MB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an upload that would cross the ceiling is refused")
    void refusesAnUploadThatWouldCrossTheCeiling() {
        project.setStorageQuotaBytes(100 * MB);
        lenient().when(fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT)).thenReturn(95 * MB);

        assertThatThrownBy(() -> upload(10 * (int) MB))
                .isInstanceOf(StorageQuotaExceededException.class)
                .hasMessageContaining("Cuota de almacenamiento excedida")
                .hasMessageContaining("Contacte al administrador");
    }

    @Test
    @DisplayName("nothing is written to storage when the quota refuses the upload")
    void writesNothingWhenRefused() {
        project.setStorageQuotaBytes(10 * MB);
        lenient().when(fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT)).thenReturn(9 * MB);

        assertThatThrownBy(() -> upload(5 * (int) MB))
                .isInstanceOf(StorageQuotaExceededException.class);

        // Validar despues de escribir dejaria el binario huerfano en MinIO ocupando justo la
        // cuota que se acaba de negar.
        verify(storageService, never()).store(anyString(), any(), anyLong(), anyString());
        verify(fileRepository, never()).save(any(FileEntity.class));
    }

    @Test
    @DisplayName("filling the quota exactly is still allowed")
    void allowsFillingTheQuotaExactly() {
        project.setStorageQuotaBytes(100 * MB);
        lenient().when(fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT)).thenReturn(90 * MB);

        // El limite es "no pasarse", no "no llegar": rechazar el archivo que encaja justo seria
        // regalar el ultimo bloque de una cuota que alguien pago.
        assertThatCode(() -> upload(10 * (int) MB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("one byte over the quota is refused")
    void refusesOneByteOver() {
        project.setStorageQuotaBytes(100 * MB);
        lenient().when(fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT)).thenReturn(100 * MB);

        assertThatThrownBy(() -> upload(1)).isInstanceOf(StorageQuotaExceededException.class);
    }

    @Test
    @DisplayName("a quota of zero is read as no quota instead of blocking the project")
    void treatsZeroAsUnlimited() {
        // Cero bytes bloquearia el proyecto entero. Nadie quiere decir eso al escribir un cero.
        project.setStorageQuotaBytes(0L);

        assertThatCode(() -> upload((int) MB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the refusal message says how much is used and how much fits")
    void explainsTheNumbers() {
        project.setStorageQuotaBytes(100 * MB);
        lenient().when(fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT)).thenReturn(99 * MB);

        // Un "no se pudo subir" a secas deja a la persona sin saber si borrar un archivo alcanza.
        assertThatThrownBy(() -> upload(5 * (int) MB))
                .hasMessageContaining("99.00 MB")
                .hasMessageContaining("100.00 MB")
                .hasMessageContaining("5.00 MB");
    }

    @Test
    @DisplayName("the quota is measured against live files, never against the trash")
    void measuresOnlyLiveFiles() {
        project.setStorageQuotaBytes(100 * MB);
        lenient().when(fileRepository.sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT)).thenReturn(10 * MB);

        upload(10 * (int) MB);

        // Cobrar la papelera dejaria a la gente sin poder subir hasta que otro la vacie: un
        // castigo raro por haber borrado algo.
        verify(fileRepository).sumSizeByApiKeyIdAndDeletedAtIsNull(PROJECT);
        assertThat(project.getStorageQuotaBytes()).isEqualTo(100 * MB);
    }

}
