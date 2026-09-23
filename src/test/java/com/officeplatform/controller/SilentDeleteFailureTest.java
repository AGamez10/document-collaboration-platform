package com.officeplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.exception.ShareAccessDeniedException;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.file.FileService;
import com.officeplatform.service.folder.FolderService;
import com.officeplatform.service.notification.NotificationService;
import com.officeplatform.service.share.ShareService;
import com.officeplatform.service.version.FileVersionService;

/**
 * El engaño del aviso verde.
 *
 * <p>Al eliminar un recurso del espacio compartido que no había creado, el usuario no es dueño ni
 * administrador, así que el controlador caía en descartar la concesión individual. Un recurso
 * compartido a nivel de proyecto no tiene concesión individual para nadie: se modificaban cero
 * filas, pero la respuesta salía igual con {@code success: true} y el mensaje "movido a tu
 * papelera". El widget pintaba el aviso verde, recargaba la lista, y el archivo seguía ahí.
 *
 * <p>Un fallo silencioso es peor que un error: el error se reporta, el silencio se repite hasta
 * que alguien deja de confiar en el sistema. Estos casos exigen que el resultado de la operación
 * sea el que decide la respuesta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SilentDeleteFailureTest {

    private static final Long PROJECT = 3L;
    private static final Long RESOURCE = 10L;

    @Mock private FileService fileService;
    @Mock private FolderService folderService;
    @Mock private ShareService shareService;
    @Mock private FileVersionService fileVersionService;
    @Mock private NotificationService notificationService;
    @Mock private com.officeplatform.service.zip.ZipInspectionService zipInspectionService;
    @Mock private com.officeplatform.service.zip.ZipExtractionService zipExtractionService;
    @Mock private ApiKeyPrincipal principal;

    private FileController fileController;
    private FolderController folderController;

    @BeforeEach
    void setUp() {
        fileController = new FileController(fileService, folderService, shareService,
                fileVersionService, notificationService, zipInspectionService, zipExtractionService);
        folderController = new FolderController(folderService, shareService, notificationService);

        lenient().when(principal.getApiKeyId()).thenReturn(PROJECT);
        lenient().when(principal.resolveUserId(anyString())).thenReturn("111");
        lenient().when(principal.resolveUserName(anyString())).thenReturn("Ana");
        lenient().when(principal.resolveUserId(null)).thenReturn("111");
        lenient().when(principal.resolveUserName(null)).thenReturn("Ana");

        // No es dueño ni administrador: el caso exacto del recurso corporativo ajeno.
        lenient().when(shareService.isOwnerOrAdmin(any(), anyLong(), any(), any(), any()))
                .thenReturn(false);
    }

    @Test
    @DisplayName("deleting a project resource you cannot touch fails loudly instead of lying")
    void aFileThatCannotBeDiscardedAnswersWithAnError() {
        // Cero concesiones que descartar: el recurso es del proyecto, no algo compartido con esta
        // persona en particular.
        lenient().when(shareService.discardForUser(eq(ResourceType.FILE), eq(RESOURCE), anyString()))
                .thenReturn(0);

        assertThatThrownBy(() -> fileController.delete(RESOURCE, null, null, principal))
                .isInstanceOf(ShareAccessDeniedException.class)
                .hasMessageContaining("No podés eliminar este archivo");

        // Y sobre todo: no se tocó nada. El archivo sigue vivo para todo el proyecto.
        verify(fileService, never()).softDeleteFile(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("the same lie was told about folders")
    void aFolderThatCannotBeDiscardedAnswersWithAnError() {
        lenient().when(shareService.discardForUser(eq(ResourceType.FOLDER), eq(RESOURCE), anyString()))
                .thenReturn(0);

        assertThatThrownBy(() -> folderController.delete(RESOURCE, null, null, principal))
                .isInstanceOf(ShareAccessDeniedException.class)
                .hasMessageContaining("No podés eliminar esta carpeta");

        verify(folderService, never()).deleteFolder(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("a recipient discarding something actually shared with them still succeeds")
    void discardingARealGrantKeepsWorking() {
        // La corrección no puede romper el caso legítimo: a quien sí le compartieron el recurso,
        // sacarlo de su vista le sigue funcionando y el original queda intacto.
        lenient().when(shareService.discardForUser(eq(ResourceType.FILE), eq(RESOURCE), anyString()))
                .thenReturn(1);

        var respuesta = fileController.delete(RESOURCE, null, null, principal);

        assertThat(respuesta.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getSuccess()).isTrue();
        verify(fileService, never()).softDeleteFile(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("the author still sends their own file to the trash")
    void theAuthorStillDeletesTheirOwnFile() {
        lenient().when(shareService.isOwnerOrAdmin(any(), anyLong(), any(), any(), any()))
                .thenReturn(true);

        var respuesta = fileController.delete(RESOURCE, null, null, principal);

        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getSuccess()).isTrue();
        verify(fileService).softDeleteFile(eq(RESOURCE), eq(PROJECT), any(), any());
    }
}
