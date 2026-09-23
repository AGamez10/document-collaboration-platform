package com.officeplatform.service.admin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.officeplatform.dto.request.BulkImportRequest;
import com.officeplatform.dto.response.BulkImportResponse;
import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.exception.InvalidOperationException;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.file.FileService;
import com.officeplatform.service.folder.FolderService;
import com.officeplatform.util.MimeUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Trae una carpeta del servidor entera al gestor, con su jerarquía.
 *
 * <p>Es el puente para apagar el DataServer: terabytes repartidos en carpetas de red que nadie va
 * a subir a mano. El árbol se recorre con {@code walkFileTree} y cada archivo se lee y se sube de
 * a uno, así el consumo de memoria no depende del tamaño de lo que se está migrando.
 *
 * <p>Lo que no entra, entra por algo:
 *
 * <ul>
 *   <li><b>Basura de Windows.</b> {@code thumbs.db}, {@code desktop.ini} y los temporales de Office
 *       ({@code ~$informe.docx}) están en toda carpeta compartida con más de un año. Migrarlos
 *       ensucia el catálogo desde el primer día.
 *   <li><b>Lo que el gestor no acepta.</b> Ejecutables y extensiones fuera de la lista se omiten
 *       con aviso: la migración no puede ser el atajo que evita las reglas de la subida.
 * </ul>
 *
 * <p>La ruta de origen llega en la petición, así que el servicio solo lee dentro de las raíces
 * permitidas por configuración. Sin ese cerco, un endpoint de administración se convierte en un
 * lector del disco entero del servidor.
 */
@Service
@Slf4j
public class BulkImportService {

    /** Nombres que toda carpeta compartida de Windows acumula y nadie quiere migrar. */
    private static final List<String> BASURA_WINDOWS = List.of(
            "thumbs.db", "desktop.ini", ".ds_store", "ehthumbs.db", "folder.jpg", "album artwork");

    private final FolderService folderService;
    private final FileService fileService;
    private final ActivityLogRecorder activityLogRecorder;

    /**
     * Raíces desde las que se permite importar.
     *
     * <p>Vacío significa sin restricción, que es lo que hace falta el día de la migración cuando
     * todavía no se sabe qué volumen se va a montar. En operación normal conviene fijarla al punto
     * de montaje del recurso de red y nada más.
     */
    private final List<String> allowedRoots;

    public BulkImportService(FolderService folderService,
                             FileService fileService,
                             ActivityLogRecorder activityLogRecorder,
                             @Value("${office-platform.import.allowed-roots:}") String allowedRoots) {
        this.folderService = folderService;
        this.fileService = fileService;
        this.activityLogRecorder = activityLogRecorder;
        this.allowedRoots = (allowedRoots == null || allowedRoots.isBlank())
                ? List.of()
                : java.util.Arrays.stream(allowedRoots.split(","))
                        .map(String::trim).filter(r -> !r.isEmpty()).toList();
    }

    public BulkImportResponse importFromFilesystem(BulkImportRequest request) {
        long inicio = System.currentTimeMillis();
        Path origen = resolveSource(request.getSourceDirectoryPath());

        Map<Path, Long> carpetasPorRuta = new HashMap<>();
        List<String> avisos = new ArrayList<>();
        int[] contadores = new int[] { 0, 0, 0 }; // carpetas, archivos, omitidos
        long[] bytes = new long[] { 0 };

        try {
            Files.walkFileTree(origen, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(origen)) {
                        carpetasPorRuta.put(dir, request.getTargetFolderId());
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        Long padre = carpetasPorRuta.get(dir.getParent());
                        FolderEntity creada = folderService.createFolder(
                                dir.getFileName().toString(), padre, request.getTargetApiKeyId(),
                                request.getDefaultUserId(), "Importación", request.getScope());
                        carpetasPorRuta.put(dir, creada.getId());
                        contadores[0]++;
                    } catch (Exception e) {
                        // Si la carpeta no se pudo crear, su contenido no tiene dónde ir: se salta
                        // la rama entera en vez de desparramar sus archivos en la raíz.
                        avisos.add("No se pudo crear la carpeta '" + dir.getFileName() + "': " + e.getMessage());
                        log.warn("No se pudo crear la carpeta '{}': {}", dir, e.getMessage());
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String nombre = file.getFileName().toString();
                    if (esBasura(nombre)) {
                        contadores[2]++;
                        return FileVisitResult.CONTINUE;
                    }
                    if (MimeUtils.isBlockedExecutable(nombre)) {
                        contadores[2]++;
                        avisos.add("Omitido '" + nombre + "': es un ejecutable.");
                        return FileVisitResult.CONTINUE;
                    }
                    String tipo = MimeUtils.contentTypeForFileName(nombre, null);
                    if ("application/octet-stream".equals(tipo)) {
                        contadores[2]++;
                        avisos.add("Omitido '" + nombre + "': tipo de archivo no permitido en el gestor.");
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        byte[] contenido = Files.readAllBytes(file);
                        Long carpeta = carpetasPorRuta.get(file.getParent());
                        fileService.createFromBytes(contenido, nombre, request.getTargetApiKeyId(),
                                carpeta, request.getDefaultUserId(), "Importación", request.getScope());
                        contadores[1]++;
                        bytes[0] += contenido.length;
                    } catch (Exception e) {
                        // Un archivo que falla no puede abortar una migración de horas.
                        contadores[2]++;
                        avisos.add("No se pudo importar '" + nombre + "': " + e.getMessage());
                        log.warn("No se pudo importar '{}': {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Un archivo sin permisos de lectura o un enlace roto: se anota y se sigue.
                    avisos.add("No se pudo leer '" + file.getFileName() + "': " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new InvalidOperationException("No se pudo recorrer '" + origen + "': " + e.getMessage());
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Importación desde '{}': {} carpetas, {} archivos, {} omitidos, {} ms",
                origen, contadores[0], contadores[1], contadores[2], duracion);
        activityLogRecorder.record(request.getTargetApiKeyId(), request.getDefaultUserId(),
                "Importación", ActivityAction.BULK_IMPORT, null, origen.toString(),
                contadores[1] + " archivo(s) y " + contadores[0] + " carpeta(s) importados desde "
                        + origen, request.getTargetFolderId());

        return BulkImportResponse.builder()
                .sourceDirectoryPath(origen.toString())
                .foldersCreated(contadores[0])
                .filesImported(contadores[1])
                .filesSkipped(contadores[2])
                .totalBytesImported(bytes[0])
                .durationMs(duracion)
                .warnings(avisos.size() > 200 ? avisos.subList(0, 200) : avisos)
                .build();
    }

    /**
     * Valida la ruta de origen antes de tocar el disco.
     *
     * <p>La ruta llega en la petición: sin este control, el endpoint lee cualquier carpeta del
     * servidor. Se normaliza primero —{@code ..} incluido— y después se compara contra las raíces
     * permitidas, porque comparar antes de normalizar es justamente como se esquiva un control así.
     */
    private Path resolveSource(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new InvalidOperationException("Indicá la carpeta del servidor que querés importar.");
        }
        Path origen;
        try {
            origen = Paths.get(rawPath.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new InvalidOperationException("La ruta '" + rawPath + "' no tiene un formato válido.");
        }
        if (!Files.exists(origen)) {
            throw new InvalidOperationException("La ruta '" + origen + "' no existe en el servidor. "
                    + "Si es un recurso de red, montalo en el host y exponelo como volumen del contenedor.");
        }
        if (!Files.isDirectory(origen)) {
            throw new InvalidOperationException("La ruta '" + origen + "' es un archivo, no una carpeta.");
        }
        if (!allowedRoots.isEmpty()) {
            boolean permitida = allowedRoots.stream()
                    .map(raiz -> Paths.get(raiz).toAbsolutePath().normalize())
                    .anyMatch(origen::startsWith);
            if (!permitida) {
                throw new InvalidOperationException("La ruta '" + origen + "' está fuera de las carpetas "
                        + "habilitadas para importar.");
            }
        }
        return origen;
    }

    /** Lo que Windows y Office dejan por todas partes y nadie quiere ver en el gestor. */
    static boolean esBasura(String nombre) {
        String limpio = nombre.trim().toLowerCase(Locale.ROOT);
        if (BASURA_WINDOWS.contains(limpio)) {
            return true;
        }
        // Temporales de Office: aparecen mientras un documento está abierto y quedan si algo falla.
        return limpio.startsWith("~$") || limpio.startsWith(".~lock.") || limpio.endsWith(".tmp");
    }

}
