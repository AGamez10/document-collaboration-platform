package com.officeplatform.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.ActivityLogRepository;
import com.officeplatform.repository.AdminUserRepository;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.EditorSessionRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.onlyoffice.service.OnlyOfficeService;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.admin.AdminServiceImpl;
import com.officeplatform.service.backup.BackupService;
import com.officeplatform.service.search.FileIndexingService;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.service.version.FileVersionService;

/**
 * Sacar a alguien del panel sin tocar lo que dejó escrito.
 *
 * <p>Hasta ahora, limpiar un usuario de prueba obligaba a entrar a la base a mano — exactamente la
 * operación donde alguien borra la fila equivocada. Lo que se elimina es la <b>inscripción</b>:
 * {@code known_users} es la libreta de quién pasó por cada proyecto, no la identidad de la persona.
 * Sus archivos y su autoría quedan donde están, que es lo que una auditoría necesita.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnownUserDeletionTest {

    private static final Long USUARIO = 47L;
    private static final Long PROYECTO = 3L;

    @Mock private KnownUserRepository knownUserRepository;
    @Mock private FileRepository fileRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private StorageService storageService;
    @Mock private EditorSessionRepository editorSessionRepository;
    @Mock private OnlyOfficeService onlyOfficeService;
    @Mock private ActivityLogRecorder activityLogRecorder;
    @Mock private BackupService backupService;
    @Mock private FileIndexingService fileIndexingService;
    @Mock private FileVersionService fileVersionService;
    @Mock private AdminUserRepository adminUserRepository;
    @Mock private com.officeplatform.repository.PortalUserRepository portalUserRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private KnownUserServiceImpl knownUserService;
    private AdminServiceImpl adminService;
    private KnownUserEntity usuario;

    @BeforeEach
    void setUp() {
        knownUserService = new KnownUserServiceImpl(knownUserRepository);
        adminService = new AdminServiceImpl(fileRepository, apiKeyRepository, activityLogRepository,
                folderRepository, storageService, editorSessionRepository, onlyOfficeService,
                knownUserService, knownUserRepository, activityLogRecorder, backupService,
                fileIndexingService, fileVersionService, adminUserRepository, portalUserRepository, passwordEncoder,
                20, 10_000_000_000L);

        usuario = KnownUserEntity.builder()
                .id(USUARIO).apiKeyId(PROYECTO).userId("1004356866")
                .displayName("Ana Pérez").role("user").build();

        lenient().when(knownUserRepository.findById(USUARIO)).thenReturn(Optional.of(usuario));
        lenient().when(knownUserRepository.existsById(USUARIO)).thenReturn(true);
        lenient().when(apiKeyRepository.findAll()).thenReturn(List.of(new ApiKeyEntity()));
    }

    @Test
    @DisplayName("deleting a user removes the registration, not their files")
    void deletesTheRegistrationOnly() {
        adminService.deleteKnownUser(USUARIO);

        verify(knownUserRepository).deleteById(USUARIO);
        // Lo que la persona creó no se toca: la autoría es justamente lo que una auditoría mira.
        verify(fileRepository, never()).delete(any());
        verify(fileRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("the deletion is recorded before the row disappears")
    void recordsTheDeletionWithTheNameItIsAboutToLose() {
        adminService.deleteKnownUser(USUARIO);

        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(activityLogRecorder).record(eq(PROYECTO), eq("admin"), anyString(),
                eq(ActivityAction.ADMIN_USER_DELETE), any(), any(), detalle.capture(), any());
        // Una bitácora que diga "se eliminó el usuario 47" no le sirve a nadie dentro de seis
        // meses: por eso se registra antes de borrar, cuando el nombre todavía existe.
        assertThat(detalle.getValue()).contains("Ana Pérez").contains("1004356866");
    }

    @Test
    @DisplayName("deleting someone who is not there says so instead of reporting success")
    void failsWhenTheUserDoesNotExist() {
        lenient().when(knownUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deleteKnownUser(99L))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("99");
        verify(knownUserRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("the service itself refuses an id that does not exist")
    void theServiceGuardsItsOwnDoor() {
        lenient().when(knownUserRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> knownUserService.deleteUser(99L))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> knownUserService.deleteUser(null))
                .isInstanceOf(StorageException.class);

        assertThatCode(() -> knownUserService.deleteUser(USUARIO)).doesNotThrowAnyException();
    }
}
