package com.officeplatform.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.entity.FileEntity;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.SharePermissionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.search.FileIndexingService;
import com.officeplatform.service.storage.StorageService;

/**
 * Buscar sin tener que recordar dónde se guardó.
 *
 * <p>La búsqueda quedaba encerrada en la pestaña abierta: desde "Mis Archivos" solo miraba lo
 * privado de quien buscaba, y un procedimiento del área no aparecía hasta cambiar de espacio. Para
 * encontrarlo había que saber de antemano dónde estaba, que es exactamente lo que uno no sabe
 * cuando lo está buscando.
 *
 * <p>Ampliar el alcance no puede ampliar lo visible: estos casos fijan las dos mitades —se busca
 * en todo lo accesible, y lo privado de un tercero sigue afuera— y que pedir un espacio concreto
 * siga acotando, porque el widget lo usa para listar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalSearchTest {

    private static final Long PROJECT = 3L;
    private static final String ME = "111";
    private static final String TERM = "procedimiento";

    @Mock private FileRepository fileRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private SharePermissionRepository sharePermissionRepository;
    @Mock private StorageService storageService;
    @Mock private FileIndexingService fileIndexingService;
    @Mock private ActivityLogRecorder activityLogRecorder;

    private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileServiceImpl(fileRepository, apiKeyRepository, folderRepository,
                sharePermissionRepository, storageService, fileIndexingService, activityLogRecorder,
                "office-platform", "application/pdf");

        lenient().when(fileRepository.searchAccessibleByNameOrContent(anyLong(), anyString(), anyString()))
                .thenReturn(List.of(new FileEntity()));
        lenient().when(fileRepository.searchSharedByNameOrContent(anyLong(), anyString()))
                .thenReturn(List.of(new FileEntity()));
        lenient().when(fileRepository.searchByNameOrContentForUser(anyLong(), anyString(), anyString()))
                .thenReturn(List.of(new FileEntity()));
    }

    @Test
    @DisplayName("a search with no scope spans everything the person can reach")
    void searchesEverythingAccessibleByDefault() {
        service.searchFiles(PROJECT, TERM, ME, null);

        verify(fileRepository).searchAccessibleByNameOrContent(PROJECT, ME, TERM);
        // El comportamiento anterior: encerrarse en lo privado de quien busca.
        verify(fileRepository, never()).searchByNameOrContentForUser(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("an explicit 'all' does the same as no scope at all")
    void treatsAllTheSameAsNoScope() {
        service.searchFiles(PROJECT, TERM, ME, "all");

        verify(fileRepository).searchAccessibleByNameOrContent(PROJECT, ME, TERM);
    }

    @Test
    @DisplayName("asking for one space still narrows to that space")
    void anExplicitScopeStillNarrows() {
        // El widget lo usa para listar: si "shared" dejara de acotar, la pestaña del área mostraría
        // también lo privado de quien mira.
        service.searchFiles(PROJECT, TERM, ME, "shared");
        verify(fileRepository).searchSharedByNameOrContent(PROJECT, TERM);

        service.searchFiles(PROJECT, TERM, ME, "private");
        verify(fileRepository).searchByNameOrContentForUser(PROJECT, ME, TERM);
    }

    @Test
    @DisplayName("a caller with no identity searches the shared space, not nothing")
    void aCallerWithoutIdentityStillFindsTheSharedSpace() {
        // Antes devolvía una lista vacía: una integración por API key sin cédula no encontraba
        // nada, ni siquiera lo que es del proyecto entero y no de nadie en particular.
        service.searchFiles(PROJECT, TERM, null, null);

        verify(fileRepository).searchSharedByNameOrContent(PROJECT, TERM);
        verify(fileRepository, never()).searchAccessibleByNameOrContent(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("the term is trimmed and an empty search asks the database nothing")
    void anEmptySearchNeverReachesTheDatabase() {
        assertThat(service.searchFiles(PROJECT, "   ", ME, null)).isEmpty();
        assertThat(service.searchFiles(PROJECT, null, ME, null)).isEmpty();

        service.searchFiles(PROJECT, "  " + TERM + "  ", ME, null);
        verify(fileRepository).searchAccessibleByNameOrContent(eq(PROJECT), eq(ME), eq(TERM));
    }
}
