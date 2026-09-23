package com.officeplatform.service.zip;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.officeplatform.entity.FolderEntity;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.file.FileService;
import com.officeplatform.service.folder.FolderService;
import com.officeplatform.util.MimeUtils;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Descomprime un archivo directamente dentro del gestor.
 *
 * <p>La alternativa que la gente hace hoy: bajar el .zip, descomprimirlo en el escritorio, y subir
 * cincuenta archivos de a uno reconstruyendo las carpetas a mano. Esto hace lo mismo del lado del
 * servidor y en una sola operación.
 *
 * <p>Cada archivo entra por la misma puerta que una subida normal —misma cuota, mismo registro de
 * actividad, misma indexación— y con la misma validación de tipo: un .zip con un .exe adentro no
 * es una forma de meter un ejecutable al gestor.
 */
@Service
@Slf4j
public class ZipExtractionService {

    private final ZipInspectionService zipInspectionService;
    private final FolderService folderService;
    private final FileService fileService;

    public ZipExtractionService(ZipInspectionService zipInspectionService,
                                FolderService folderService,
                                FileService fileService) {
        this.zipInspectionService = zipInspectionService;
        this.folderService = folderService;
        this.fileService = fileService;
    }

    /** Lo que hizo la extracción, para poder contárselo a quien la pidió. */
    @Data
    @Builder
    public static class ExtractionReport {
        private int foldersCreated;
        private int filesImported;
        private long totalBytesImported;
        private int filesSkipped;
        private java.util.List<String> warnings;
    }

    /**
     * Vuelca el contenido del comprimido bajo la carpeta indicada.
     *
     * @param zipContent    binario del comprimido, ya autorizado
     * @param targetFolderId carpeta destino, o null para la raíz del espacio
     */
    public ExtractionReport extractInto(InputStream zipContent, Long targetFolderId,
                                        ApiKeyPrincipal principal, String userId, String userName,
                                        String scope) {
        Long apiKeyId = principal.getApiKeyId();
        // Cache de carpetas ya creadas en esta extracción: un ZIP con cien archivos en la misma
        // subcarpeta no puede crear cien veces la misma carpeta.
        Map<String, Long> carpetasPorRuta = new HashMap<>();
        java.util.List<String> avisos = new java.util.ArrayList<>();
        int[] contadores = new int[] { 0, 0, 0 }; // carpetas, archivos, omitidos
        long[] bytes = new long[] { 0 };

        zipInspectionService.forEachFile(zipContent, (ruta, contenido) -> {
            String nombre = ZipInspectionService.fileNameOf(ruta);

            // El tipo se valida igual que en una subida: el comprimido no es un pasadizo para
            // meter lo que el gestor rechaza por la puerta del frente.
            if (MimeUtils.isBlockedExecutable(nombre)) {
                contadores[2]++;
                avisos.add("Omitido '" + ruta + "': es un ejecutable y no se permite en el gestor.");
                return;
            }
            String tipo = MimeUtils.contentTypeForFileName(nombre, null);
            // Un tipo genérico significa que la extensión no está en la lista del gestor. Se omite
            // con aviso en vez de entrar como binario indeterminado: lo que no se puede abrir
            // después solo ocupa espacio y ensucia el catálogo.
            if ("application/octet-stream".equals(tipo) || !MimeUtils.matchesExtension(nombre, tipo)) {
                contadores[2]++;
                avisos.add("Omitido '" + ruta + "': tipo de archivo no permitido en el gestor.");
                return;
            }

            try {
                Long carpeta = resolveFolder(parentPathOf(ruta), targetFolderId, carpetasPorRuta,
                        apiKeyId, userId, userName, scope, contadores);
                fileService.createFromBytes(contenido, nombre, apiKeyId, carpeta, userId, userName, scope);
                contadores[1]++;
                bytes[0] += contenido.length;
            } catch (Exception e) {
                // Un archivo que falla no puede abortar los otros cuarenta y nueve: quien extrae un
                // comprimido quiere lo que se pueda recuperar, no un todo o nada.
                contadores[2]++;
                avisos.add("No se pudo importar '" + ruta + "': " + e.getMessage());
                log.warn("No se pudo importar '{}' del comprimido: {}", ruta, e.getMessage());
            }
        });

        return ExtractionReport.builder()
                .foldersCreated(contadores[0])
                .filesImported(contadores[1])
                .totalBytesImported(bytes[0])
                .filesSkipped(contadores[2])
                .warnings(avisos)
                .build();
    }

    /**
     * Devuelve el id de la carpeta que corresponde a una ruta interna, creándola si hace falta.
     *
     * <p>Se crea tramo por tramo para que "Informes/2026/Enero" reproduzca los tres niveles y no
     * una sola carpeta con barras en el nombre.
     */
    private Long resolveFolder(String rutaInterna, Long raiz, Map<String, Long> cache,
                               Long apiKeyId, String userId, String userName, String scope,
                               int[] contadores) {
        if (rutaInterna.isEmpty()) {
            return raiz;
        }
        Long actual = raiz;
        StringBuilder acumulada = new StringBuilder();
        for (String tramo : rutaInterna.split("/")) {
            if (acumulada.length() > 0) {
                acumulada.append('/');
            }
            acumulada.append(tramo);
            String clave = acumulada.toString();
            Long yaCreada = cache.get(clave);
            if (yaCreada != null) {
                actual = yaCreada;
                continue;
            }
            FolderEntity creada = folderService.createFolder(tramo, actual, apiKeyId, userId, userName, scope);
            contadores[0]++;
            cache.put(clave, creada.getId());
            actual = creada.getId();
        }
        return actual;
    }

    /** "Informes/2026/acta.docx" -> "Informes/2026"; "acta.docx" -> "". */
    private static String parentPathOf(String ruta) {
        int barra = ruta.lastIndexOf('/');
        return barra < 0 ? "" : ruta.substring(0, barra);
    }

}
