package com.officeplatform.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MimeUtils {

    /**
     * Extensions accepted for each allowed MIME type.
     *
     * <p>The declared content type of an upload is chosen by the client and cannot be trusted on
     * its own: sending an executable while declaring {@code application/pdf} passed validation.
     * Requiring the file name extension to agree with the declared type closes that bypass without
     * reading file contents.
     */
    private static final Map<String, Set<String>> EXTENSIONS_BY_MIME_TYPE = Map.ofEntries(
            Map.entry("application/pdf", Set.of("pdf")),
            Map.entry("application/msword", Set.of("doc")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
            // Algunos clientes declaran el tipo genérico de Excel para un .xlsm en vez del
            // macroEnabled: se tolera esa extensión acá para no rechazar cargas legítimas.
            Map.entry("application/vnd.ms-excel", Set.of("xls", "xlsm")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")),
            Map.entry("application/vnd.ms-powerpoint", Set.of("ppt")),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx")),

            // Formatos con macros. Cada tipo sigue exigiendo su propia extensión, así que
            // habilitarlos no relaja la verificación: solo agrega pares válidos.
            Map.entry("application/vnd.ms-excel.sheet.macroEnabled.12", Set.of("xlsm")),
            Map.entry("application/vnd.ms-excel.template.macroEnabled.12", Set.of("xltm")),
            Map.entry("application/vnd.ms-excel.sheet.binary.macroEnabled.12", Set.of("xlsb")),
            Map.entry("application/vnd.ms-word.document.macroEnabled.12", Set.of("docm")),
            Map.entry("application/vnd.ms-word.template.macroEnabled.12", Set.of("dotm")),
            Map.entry("application/vnd.ms-powerpoint.presentation.macroEnabled.12", Set.of("pptm")),
            Map.entry("application/vnd.ms-powerpoint.template.macroEnabled.12", Set.of("potm")),

            // OpenDocument. El widget y docker-compose ya los aceptaban, pero sin entrada acá
            // matchesExtension no encontraba el tipo y devolvía false: la subida pasaba el primer
            // control y moría en el segundo con "la extensión no corresponde al tipo declarado".
            Map.entry("application/vnd.oasis.opendocument.text", Set.of("odt")),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", Set.of("ods")),
            Map.entry("application/vnd.oasis.opendocument.presentation", Set.of("odp")));

    /**
     * Tipo de contenido canónico de cada extensión conocida, para servir descargas.
     *
     * <p>El MIME guardado en la base es el que declaró el navegador al subir, y no siempre es el
     * correcto: un {@code .xlsm} suele llegar como {@code application/vnd.ms-excel} genérico. Si esa
     * descarga se sirve tal cual, Excel de escritorio abre el archivo como un {@code .xls} antiguo y
     * avisa que el formato no coincide con la extensión, justo el momento en que el usuario necesita
     * que las macros funcionen. La extensión del nombre es el dato fiable acá.
     */
    private static final Map<String, String> MIME_TYPE_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("docm", "application/vnd.ms-word.document.macroEnabled.12"),
            Map.entry("dotm", "application/vnd.ms-word.template.macroEnabled.12"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12"),
            Map.entry("xltm", "application/vnd.ms-excel.template.macroEnabled.12"),
            Map.entry("xlsb", "application/vnd.ms-excel.sheet.binary.macroEnabled.12"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12"),
            Map.entry("potm", "application/vnd.ms-powerpoint.template.macroEnabled.12"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation"));

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private MimeUtils() {
    }

    /**
     * Tipo de contenido con el que servir una descarga.
     *
     * <p>Prioriza la extensión del nombre original; si no se reconoce, respeta el MIME almacenado, y
     * solo cae en {@code application/octet-stream} cuando no hay ninguno utilizable. Ese último caso
     * importa: {@code MediaType.parseMediaType} lanza excepción con un valor nulo o mal formado, así
     * que un registro viejo o restaurado sin MIME rompería la descarga con un 500 en vez de bajar.
     *
     * @param fileName nombre original del archivo, del que se toma la extensión
     * @param storedMimeType MIME guardado al subir; se usa como respaldo
     */
    public static String contentTypeForFileName(String fileName, String storedMimeType) {
        String extension = fileName == null ? null : FileUtils.extractExtension(fileName);
        if (extension != null && !extension.isBlank()) {
            String canonical = MIME_TYPE_BY_EXTENSION.get(extension.toLowerCase(Locale.ROOT));
            if (canonical != null) {
                return canonical;
            }
        }
        if (storedMimeType != null && !storedMimeType.isBlank() && storedMimeType.contains("/")) {
            return storedMimeType.trim();
        }
        return FALLBACK_CONTENT_TYPE;
    }

    public static boolean isAllowed(String mimeType, Collection<String> allowedMimeTypes) {
        if (mimeType == null || allowedMimeTypes == null) {
            return false;
        }
        return allowedMimeTypes.contains(mimeType);
    }

    /**
     * Whether the file name extension is one of those expected for the declared MIME type.
     *
     * <p>An unknown MIME type is not accepted here; callers check {@link #isAllowed} first, so a
     * type reaching this method without a mapping means the two lists drifted apart and the upload
     * is rejected rather than let through unchecked.
     *
     * @param fileName original file name supplied with the upload
     * @param mimeType content type declared by the client
     */
    public static boolean matchesExtension(String fileName, String mimeType) {
        if (fileName == null || mimeType == null) {
            return false;
        }
        Set<String> expected = EXTENSIONS_BY_MIME_TYPE.get(mimeType);
        if (expected == null) {
            return false;
        }
        String extension = FileUtils.extractExtension(fileName);
        if (extension == null || extension.isBlank()) {
            return false;
        }
        return expected.contains(extension.toLowerCase(Locale.ROOT));
    }

}
