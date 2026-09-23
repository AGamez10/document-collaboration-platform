package com.officeplatform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.officeplatform.dto.request.BulkImportRequest;
import com.officeplatform.dto.response.BulkImportResponse;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.exception.InvalidOperationException;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.file.FileService;
import com.officeplatform.service.folder.FolderService;

/**
 * La migración del DataServer de Windows.
 *
 * <p>Son terabytes repartidos en carpetas de red que nadie va a subir a mano, y vienen con una
 * década de sedimento: {@code thumbs.db} en cada carpeta con fotos, {@code desktop.ini} en cada
 * carpeta personalizada, y los {@code ~$} que Office deja cuando alguien cierra mal un documento.
 * Migrar eso tal cual ensucia el catálogo desde el primer día y nadie lo limpia después.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkImportServiceTest {

    private static final Long PROYECTO = 3L;

    @Mock private FolderService folderService;
    @Mock private FileService fileService;
    @Mock private ActivityLogRecorder activityLogRecorder;

    private BulkImportService service;

    @BeforeEach
    void setUp() {
        service = new BulkImportService(folderService, fileService, activityLogRecorder, "");

        AtomicLong secuencia = new AtomicLong(100);
        lenient().when(folderService.createFolder(anyString(), any(), anyLong(), any(), any(), any()))
                .thenAnswer(i -> {
                    FolderEntity f = new FolderEntity();
                    f.setId(secuencia.incrementAndGet());
                    f.setName(i.getArgument(0));
                    return f;
                });
    }

    private BulkImportRequest peticion(Path origen) {
        BulkImportRequest r = new BulkImportRequest();
        r.setSourceDirectoryPath(origen.toString());
        r.setTargetApiKeyId(PROYECTO);
        r.setTargetFolderId(null);
        r.setDefaultUserId("1004356866");
        r.setScope("shared");
        return r;
    }

    private void archivo(Path dir, String nombre, String contenido) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(nombre), contenido, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the folder tree is rebuilt inside the manager, level by level")
    void rebuildsTheFolderTree(@TempDir Path origen) throws Exception {
        archivo(origen, "raiz.docx", "contenido");
        archivo(origen.resolve("Informes"), "acta.docx", "contenido");
        archivo(origen.resolve("Informes").resolve("2026"), "cierre.xlsx", "contenido");

        BulkImportResponse reporte = service.importFromFilesystem(peticion(origen));

        // Dos carpetas: "Informes" y "2026". La raíz es el destino, no se crea.
        assertThat(reporte.getFoldersCreated()).isEqualTo(2);
        assertThat(reporte.getFilesImported()).isEqualTo(3);
        assertThat(reporte.getTotalBytesImported()).isEqualTo(3L * "contenido".length());
        verify(folderService).createFolder(eqNombre("Informes"), any(), anyLong(), any(), any(), any());
        verify(folderService).createFolder(eqNombre("2026"), any(), anyLong(), any(), any(), any());
    }

    private String eqNombre(String nombre) {
        return org.mockito.ArgumentMatchers.eq(nombre);
    }

    @Test
    @DisplayName("the sediment of a Windows share is left behind")
    void skipsWindowsClutter(@TempDir Path origen) throws Exception {
        archivo(origen, "informe.docx", "contenido");
        archivo(origen, "Thumbs.db", "basura");
        archivo(origen, "desktop.ini", "basura");
        archivo(origen, "~$informe.docx", "temporal de Word");
        archivo(origen, "borrador.tmp", "temporal");

        BulkImportResponse reporte = service.importFromFilesystem(peticion(origen));

        assertThat(reporte.getFilesImported()).isEqualTo(1);
        assertThat(reporte.getFilesSkipped()).isEqualTo(4);
    }

    @Test
    @DisplayName("an executable does not sneak in through the migration")
    void refusesExecutables(@TempDir Path origen) throws Exception {
        // La migración no puede ser el atajo que evita las reglas de la subida.
        archivo(origen, "instalador.exe", "MZ");
        archivo(origen, "macro.bat", "echo");

        BulkImportResponse reporte = service.importFromFilesystem(peticion(origen));

        assertThat(reporte.getFilesImported()).isZero();
        assertThat(reporte.getFilesSkipped()).isEqualTo(2);
        assertThat(reporte.getWarnings()).anyMatch(w -> w.contains("instalador.exe"));
        verify(fileService, never()).createFromBytes(any(), anyString(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unknown extension is reported instead of entering as a blob")
    void skipsUnknownExtensions(@TempDir Path origen) throws Exception {
        archivo(origen, "plano.dwg", "contenido");

        BulkImportResponse reporte = service.importFromFilesystem(peticion(origen));

        assertThat(reporte.getFilesImported()).isZero();
        assertThat(reporte.getWarnings()).anyMatch(w -> w.contains("plano.dwg"));
    }

    @Test
    @DisplayName("one bad file does not abort a migration of hours")
    void oneFailureDoesNotStopTheRest(@TempDir Path origen) throws Exception {
        archivo(origen, "uno.docx", "contenido");
        archivo(origen, "dos.docx", "contenido");
        archivo(origen, "tres.docx", "contenido");
        lenient().when(fileService.createFromBytes(any(), org.mockito.ArgumentMatchers.eq("dos.docx"),
                        anyLong(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("cuota excedida"));

        BulkImportResponse reporte = service.importFromFilesystem(peticion(origen));

        assertThat(reporte.getFilesImported()).isEqualTo(2);
        assertThat(reporte.getFilesSkipped()).isEqualTo(1);
        assertThat(reporte.getWarnings()).anyMatch(w -> w.contains("dos.docx"));
    }

    @Test
    @DisplayName("a path that does not exist says so instead of importing nothing in silence")
    void failsClearlyOnAMissingPath() {
        BulkImportRequest r = new BulkImportRequest();
        r.setSourceDirectoryPath("/no/existe/esta/carpeta");
        r.setTargetApiKeyId(PROYECTO);

        assertThatThrownBy(() -> service.importFromFilesystem(r))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("a path outside the allowed roots is refused")
    void refusesAPathOutsideTheAllowedRoots(@TempDir Path permitida, @TempDir Path otra) throws Exception {
        // La ruta llega en la petición: sin este cerco, el endpoint lee cualquier carpeta del
        // servidor, incluido lo que no tiene nada que ver con documentos.
        BulkImportService acotado = new BulkImportService(folderService, fileService,
                activityLogRecorder, permitida.toString());
        archivo(otra, "ajeno.docx", "contenido");

        assertThatThrownBy(() -> acotado.importFromFilesystem(peticion(otra)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("fuera de las carpetas");
    }

    @Test
    @DisplayName("the traversal sequence is normalised before the root is checked")
    void normalisesBeforeCheckingTheRoot(@TempDir Path permitida) {
        // Comparar antes de normalizar es exactamente como se esquiva un control de este tipo:
        // "/permitida/../etc" empieza con "/permitida" y no está adentro.
        BulkImportService acotado = new BulkImportService(folderService, fileService,
                activityLogRecorder, permitida.resolve("documentos").toString());
        BulkImportRequest r = peticion(permitida.resolve("documentos").resolve("..").resolve("otra"));

        assertThatThrownBy(() -> acotado.importFromFilesystem(r))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("the clutter rule covers the names a Windows share really accumulates")
    void recognisesTheUsualClutter() {
        assertThat(BulkImportService.esBasura("Thumbs.db")).isTrue();
        assertThat(BulkImportService.esBasura("desktop.ini")).isTrue();
        assertThat(BulkImportService.esBasura("~$Presupuesto.xlsx")).isTrue();
        assertThat(BulkImportService.esBasura(".~lock.informe.odt#")).isTrue();
        assertThat(BulkImportService.esBasura("copia.tmp")).isTrue();
        // Y no se lleva puesto nada legítimo.
        assertThat(BulkImportService.esBasura("Informe anual.docx")).isFalse();
        assertThat(BulkImportService.esBasura("thumbnails.pptx")).isFalse();
    }
}
