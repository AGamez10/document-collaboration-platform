package com.officeplatform.service.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

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
import com.officeplatform.exception.InvalidOperationException;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;

/**
 * El espacio compartido no se privatiza moviendo.
 *
 * <p>"Compartidos" se define por {@code user_id IS NULL}. Mover un documento de ahí a "Mis
 * archivos" le estampaba la cédula de quien lo movía, y en ese instante el archivo desaparecía
 * para todo el resto del equipo: no quedaba borrado ni en la papelera, dejaba de existir para los
 * demás. Con una carpeta el daño se multiplica por todo su contenido. Y como un recurso compartido
 * resuelve EDIT para cualquier integrante del proyecto, cualquiera podía hacerlo sin querer.
 *
 * <p>Estos casos fijan la regla —mover cambia de lugar, no de dueño— y las tres puertas por donde
 * se colaba: el scope explícito, la carpeta destino privada, y el arrastre de una carpeta entera.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeIntegrityTest {

    private static final Long PROJECT = 3L;
    private static final String ME = "111";

    @Mock private FolderRepository folderRepository;
    @Mock private FileRepository fileRepository;
    @Mock private ActivityLogRecorder activityLogRecorder;
    @Mock private StorageService storageService;

    private FolderServiceImpl service;
    private FileEntity compartido;
    private FolderEntity carpetaCompartida;
    private FolderEntity carpetaPrivada;

    @BeforeEach
    void setUp() {
        service = new FolderServiceImpl(folderRepository, fileRepository, activityLogRecorder, storageService);

        compartido = FileEntity.builder()
                .id(10L).uuid("uuid-10").fileName("procedimiento.docx")
                .originalFileName("procedimiento.docx").bucket("office-platform")
                .objectName("obj-10").mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .size(1024L).apiKeyId(PROJECT).userId(null)   // null = del proyecto, no de una persona
                .build();

        carpetaCompartida = FolderEntity.builder()
                .id(1L).name("Procedimientos").apiKeyId(PROJECT).userId(null).build();
        carpetaPrivada = FolderEntity.builder()
                .id(2L).name("Mis cosas").apiKeyId(PROJECT).userId(ME).build();

        lenient().when(fileRepository.findByIdAndApiKeyIdAndDeletedAtIsNull(10L, PROJECT))
                .thenReturn(Optional.of(compartido));
        lenient().when(fileRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(compartido));
        lenient().when(folderRepository.findById(1L)).thenReturn(Optional.of(carpetaCompartida));
        lenient().when(folderRepository.findById(2L)).thenReturn(Optional.of(carpetaPrivada));
        lenient().when(folderRepository.findByIdAndApiKeyId(1L, PROJECT)).thenReturn(Optional.of(carpetaCompartida));
        lenient().when(folderRepository.findByIdAndApiKeyId(2L, PROJECT)).thenReturn(Optional.of(carpetaPrivada));
        lenient().when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(fileRepository.findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(List.of());
        lenient().when(folderRepository.findAllByApiKeyIdAndParentIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("moving a shared file to My Files is refused instead of hiding it from the team")
    void refusesToPrivatizeASharedFileByScope() {
        assertThatThrownBy(() -> service.moveFile(10L, null, PROJECT, ME, "Ana", "private"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Hacer una copia");

        // Lo que importa no es la excepción sino que el archivo siga siendo del equipo.
        assertThat(compartido.getUserId()).isNull();
    }

    @Test
    @DisplayName("dropping a shared file into a private folder privatizes it just the same")
    void refusesToPrivatizeThroughAPrivateDestinationFolder() {
        // La puerta que nadie miraba: sin scope explícito, el archivo hereda el dueño de la
        // carpeta destino. Arrastrarlo a una carpeta personal lo sacaba del equipo igual.
        assertThatThrownBy(() -> service.moveFile(10L, 2L, PROJECT, ME, "Ana", null))
                .isInstanceOf(InvalidOperationException.class);

        assertThat(compartido.getUserId()).isNull();
    }

    @Test
    @DisplayName("moving a shared folder to My Files is refused, content and all")
    void refusesToPrivatizeASharedFolder() {
        assertThatThrownBy(() -> service.moveFolder(1L, null, PROJECT, ME, "Ana", "private"))
                .isInstanceOf(InvalidOperationException.class);

        assertThat(carpetaCompartida.getUserId()).isNull();
    }

    @Test
    @DisplayName("moving inside the shared space keeps working")
    void allowsMovingWithinTheSharedSpace() {
        // La regla no puede volverse un candado: mover de una carpeta compartida a otra, o a la
        // raíz compartida, es exactamente lo que la gente hace todos los días.
        assertThatCode(() -> service.moveFile(10L, 1L, PROJECT, ME, "Ana", null)).doesNotThrowAnyException();
        assertThat(compartido.getUserId()).isNull();

        assertThatCode(() -> service.moveFile(10L, null, PROJECT, ME, "Ana", "shared")).doesNotThrowAnyException();
        assertThat(compartido.getUserId()).isNull();
    }

    @Test
    @DisplayName("a private file of its owner still moves freely within My Files")
    void allowsMovingAPrivateFileOfItsOwner() {
        FileEntity propio = FileEntity.builder()
                .id(11L).uuid("uuid-11").fileName("notas.docx").originalFileName("notas.docx")
                .bucket("office-platform").objectName("obj-11").mimeType("text/plain")
                .size(10L).apiKeyId(PROJECT).userId(ME).build();
        lenient().when(fileRepository.findByIdAndApiKeyIdAndDeletedAtIsNull(11L, PROJECT))
                .thenReturn(Optional.of(propio));

        assertThatCode(() -> service.moveFile(11L, null, PROJECT, ME, "Ana", "private"))
                .doesNotThrowAnyException();
        assertThat(propio.getUserId()).isEqualTo(ME);
    }

    @Test
    @DisplayName("moving a folder into itself is a 409, not a server error")
    void movingAFolderIntoItselfIsAnInvalidOperation() {
        // Era StorageException, que el manejador traduce a 500: el usuario veía "error interno"
        // frente a algo que él mismo podía corregir, y el monitoreo lo contaba como caída.
        assertThatThrownBy(() -> service.moveFolder(1L, 1L, PROJECT, ME, "Ana", null))
                .isInstanceOf(InvalidOperationException.class);
    }
}
