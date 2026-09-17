package com.officeplatform.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
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
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.repository.ActivityLogRepository;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.EditorSessionRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.admin.AdminServiceImpl;
import com.officeplatform.service.backup.BackupService;
import com.officeplatform.onlyoffice.service.OnlyOfficeService;
import com.officeplatform.service.search.FileIndexingService;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.service.user.KnownUserService;
import com.officeplatform.service.version.FileVersionService;

/**
 * La papelera de dos niveles.
 *
 * <p>"Eliminar permanentemente" borraba la fila y el binario en el acto, y con ellos el archivo
 * desaparecia tambien del panel de administracion: el error mas caro del sistema era el mas facil
 * de cometer y no dejaba nada que auditar ni que recuperar. Estos casos fijan el reparto de
 * potestades que lo corrige — el usuario oculta, el administrador borra — y las dos formas de
 * esquivarlo que habria que cerrar: vaciar dos veces el mismo archivo, y purgar la carpeta que lo
 * contiene en vez del archivo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrashRetentionTest {

    private static final Long PROJECT = 3L;
    private static final Long FILE_ID = 10L;
    private static final String ME = "111";

    @Mock private FileRepository fileRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private StorageService storageService;
    @Mock private FileIndexingService fileIndexingService;
    @Mock private ActivityLogRecorder activityLogRecorder;

    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private EditorSessionRepository editorSessionRepository;
    @Mock private OnlyOfficeService onlyOfficeService;
    @Mock private KnownUserService knownUserService;
    @Mock private KnownUserRepository knownUserRepository;
    @Mock private BackupService backupService;
    @Mock private FileVersionService fileVersionService;

    private FileServiceImpl userService;
    private AdminServiceImpl adminService;
    private FileEntity file;

    @BeforeEach
    void setUp() {
        file = FileEntity.builder()
                .id(FILE_ID)
                .uuid("uuid-10")
                .fileName("informe.docx")
                .originalFileName("informe.docx")
                .bucket("office-platform")
                .objectName("obj-10")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .size(1024L)
                .apiKeyId(PROJECT)
                .createdByUserId(ME)
                .deletedAt(LocalDateTime.now())
                .build();

        lenient().when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));
        // La papelera del usuario y la del administrador miran la misma fila con distinto filtro.
        lenient().when(fileRepository.findByIdAndApiKeyIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(
                        FILE_ID, PROJECT))
                .thenAnswer(i -> file.getUserPurgedAt() == null ? Optional.of(file) : Optional.empty());
        lenient().when(fileRepository.findByIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(FILE_ID))
                .thenAnswer(i -> file.getUserPurgedAt() == null ? Optional.of(file) : Optional.empty());
        lenient().when(fileRepository.findByIdAndDeletedAtIsNotNull(FILE_ID))
                .thenAnswer(i -> file.getDeletedAt() == null ? Optional.empty() : Optional.of(file));
        lenient().when(fileRepository.findAllByCreatedByUserIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(ME))
                .thenAnswer(i -> file.getUserPurgedAt() == null ? List.of(file) : List.of());
        lenient().when(fileRepository.findAllByUserIdAndDeletedAtIsNotNullAndUserPurgedAtIsNull(ME))
                .thenReturn(List.of());

        userService = new FileServiceImpl(fileRepository, apiKeyRepository, folderRepository,
                sharePermissionRepository, storageService, fileIndexingService, activityLogRecorder,
                "office-platform",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        adminService = new AdminServiceImpl(fileRepository, apiKeyRepository, activityLogRepository,
                folderRepository, storageService, editorSessionRepository, onlyOfficeService,
                knownUserService, knownUserRepository, activityLogRecorder, backupService,
                fileIndexingService, fileVersionService, 20, 10_000_000_000L);
    }

    // ── el usuario oculta ────────────────────────────────────────────────────

    @Test
    @DisplayName("emptying the trash hides the file without destroying anything")
    void userPurgeKeepsTheRowAndTheBinary() {
        userService.purgeFile(FILE_ID, PROJECT, ME, "Ana");

        assertThat(file.getUserPurgedAt()).isNotNull();
        // Lo que antes se perdia para siempre: la fila y el objeto de MinIO.
        verify(fileRepository, never()).delete(any(FileEntity.class));
        verify(storageService, never()).delete(any());
        // Y el historial de versiones, que sin el archivo restaurado no serviria de nada.
        verify(fileVersionService, never()).purgeVersions(any());
    }

    @Test
    @DisplayName("what the user emptied disappears from their own trash")
    void userPurgeHidesItFromItsOwnersTrash() {
        assertThat(userService.listTrash(PROJECT, ME, "Ana", null)).hasSize(1);

        userService.purgeFile(FILE_ID, PROJECT, ME, "Ana");

        assertThat(userService.listTrash(PROJECT, ME, "Ana", null)).isEmpty();
    }

    @Test
    @DisplayName("an already emptied file cannot be emptied or restored again by its owner")
    void theOwnerCannotActOnWhatTheyAlreadyEmptied() {
        userService.purgeFile(FILE_ID, PROJECT, ME, "Ana");

        // Devolver 404 y no un exito silencioso: para esta persona el archivo ya no existe, y
        // fingir que la operacion tuvo efecto sobre algo invisible es peor que decir que no esta.
        assertThatThrownBy(() -> userService.purgeFile(FILE_ID, PROJECT, ME, "Ana"))
                .isInstanceOf(FileNotFoundException.class);
        assertThatThrownBy(() -> userService.restoreFile(FILE_ID, PROJECT, ME, "Ana"))
                .isInstanceOf(FileNotFoundException.class);
    }

    // ── el administrador ve, devuelve y borra ────────────────────────────────

    @Test
    @DisplayName("the admin trash still lists a file its owner emptied")
    void theAdminTrashStillSeesIt() {
        userService.purgeFile(FILE_ID, PROJECT, ME, "Ana");

        // El listado del panel filtra solo por deletedAt, asi que la fila vaciada sigue estando.
        lenient().when(fileRepository.findAllByDeletedAtIsNotNull()).thenReturn(List.of(file));
        lenient().when(apiKeyRepository.findAll()).thenReturn(List.of());
        lenient().when(folderRepository.findAll()).thenReturn(List.of());

        var listado = adminService.listFiles(null, true);

        assertThat(listado).hasSize(1);
        assertThat(listado.get(0).getUserPurgedAt())
                .as("el panel tiene que poder decir por que su dueño ya no lo ve")
                .isNotNull();
    }

    @Test
    @DisplayName("the admin restore clears both levels of the trash")
    void theAdminRestoreBringsItAllTheWayBack() {
        userService.purgeFile(FILE_ID, PROJECT, ME, "Ana");

        adminService.restoreFile(FILE_ID);

        assertThat(file.getDeletedAt()).isNull();
        assertThat(file.getUserPurgedAt()).isNull();
    }

    @Test
    @DisplayName("a restored file whose folder is gone lands at the root instead of nowhere")
    void theAdminRestoreReanchorsAnOrphanFile() {
        // Una carpeta borrada no aparece en ningun listado, y un archivo vivo dentro de ella
        // tampoco: restaurarlo sin mover nada seria devolverlo a un lugar invisible.
        FolderEntity trashed = FolderEntity.builder()
                .id(7L).name("Semana 36").deletedAt(LocalDateTime.now()).build();
        file.setFolderId(7L);
        lenient().when(folderRepository.findById(7L)).thenReturn(Optional.of(trashed));

        adminService.restoreFile(FILE_ID);

        assertThat(file.getFolderId()).isNull();
        assertThat(file.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("the admin purge is the only one that really deletes")
    void theAdminPurgeIsTheRealDeletion() {
        userService.purgeFile(FILE_ID, PROJECT, ME, "Ana");

        adminService.purgeFile(FILE_ID);

        verify(fileVersionService).purgeVersions(FILE_ID);
        verify(storageService).delete("obj-10");
        verify(fileRepository).delete(file);
    }

    @Test
    @DisplayName("a binary already gone from storage does not block the admin purge")
    void theAdminPurgeSurvivesAMissingBinary() {
        org.mockito.Mockito.doThrow(new RuntimeException("no está en MinIO"))
                .when(storageService).delete("obj-10");

        adminService.purgeFile(FILE_ID);

        // Si un objeto perdido pudiera frenar la purga, la papelera del panel quedaria imposible
        // de vaciar, que es el sintoma que ya se reporto una vez sobre las carpetas.
        verify(fileRepository).delete(file);
    }
}
