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
            Map.entry("application/vnd.oasis.opendocument.presentation", Set.of("odp")),

            // ── Multimedia, comprimidos y texto ──────────────────────────────────────────
            // El gestor documental reemplaza a una unidad de red compartida, y en una unidad de
            // red conviven planos, fotos de obra, grabaciones y paquetes comprimidos. Aceptar
            // solo formatos ofimaticos obligaria a la gente a seguir usando G:\ para todo lo
            // demas, que es exactamente lo que este sistema viene a reemplazar.
            Map.entry("image/png", Set.of("png")),
            Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
            Map.entry("image/gif", Set.of("gif")),
            Map.entry("image/webp", Set.of("webp")),
            Map.entry("image/svg+xml", Set.of("svg")),
            Map.entry("image/bmp", Set.of("bmp")),
            Map.entry("image/x-icon", Set.of("ico")),
            Map.entry("image/vnd.microsoft.icon", Set.of("ico")),

            Map.entry("video/mp4", Set.of("mp4")),
            Map.entry("video/webm", Set.of("webm")),
            Map.entry("video/ogg", Set.of("ogv")),
            Map.entry("video/quicktime", Set.of("mov")),
            Map.entry("video/x-msvideo", Set.of("avi")),
            Map.entry("video/x-matroska", Set.of("mkv")),

            Map.entry("audio/mpeg", Set.of("mp3")),
            Map.entry("audio/wav", Set.of("wav")),
            Map.entry("audio/x-wav", Set.of("wav")),
            Map.entry("audio/ogg", Set.of("ogg")),
            Map.entry("audio/aac", Set.of("aac")),
            Map.entry("audio/mp4", Set.of("m4a")),

            Map.entry("application/zip", Set.of("zip")),
            Map.entry("application/x-zip-compressed", Set.of("zip")),
            Map.entry("application/x-rar-compressed", Set.of("rar")),
            Map.entry("application/vnd.rar", Set.of("rar")),
            Map.entry("application/x-7z-compressed", Set.of("7z")),
            Map.entry("application/x-tar", Set.of("tar")),
            Map.entry("application/gzip", Set.of("gz")),

            Map.entry("text/plain", Set.of("txt", "log", "conf", "ini")),
            Map.entry("text/csv", Set.of("csv")),
            Map.entry("text/tab-separated-values", Set.of("tsv")),
            Map.entry("text/markdown", Set.of("md")),
            Map.entry("application/json", Set.of("json")),
            Map.entry("application/xml", Set.of("xml")),
            Map.entry("text/xml", Set.of("xml")),
            Map.entry("text/html", Set.of("html", "htm")),
            Map.entry("application/sql", Set.of("sql")),
            Map.entry("text/yaml", Set.of("yaml", "yml")),
            Map.entry("application/x-yaml", Set.of("yaml", "yml")),
            Map.entry("application/rtf", Set.of("rtf")),
            Map.entry("text/rtf", Set.of("rtf")));

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
            Map.entry("odp", "application/vnd.oasis.opendocument.presentation"),

            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("ico", "image/x-icon"),

            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogv", "video/ogg"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("mkv", "video/x-matroska"),

            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("aac", "audio/aac"),
            Map.entry("m4a", "audio/mp4"),

            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("gz", "application/gzip"),

            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("conf", "text/plain"),
            Map.entry("ini", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("tsv", "text/tab-separated-values"),
            Map.entry("md", "text/markdown"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("sql", "application/sql"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("rtf", "application/rtf"));

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    /**
     * Extensiones que nunca se aceptan, sin importar el tipo que declare el cliente.
     *
     * <p>Esta lista NO reemplaza a la lista blanca de arriba: la refuerza. Un modelo de lista
     * negra a secas seria mas debil, porque deja pasar todo lo que nadie penso en prohibir. Acá
     * la subida ya tiene que estar en la lista blanca Y concordar su extension con el tipo; esta
     * comprobacion extra existe para el caso en que alguien agregue un tipo generico a la
     * configuracion sin advertir que abre la puerta a un ejecutable.
     */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "msp",
            "sh", "bash", "vbs", "vbe", "js", "jse", "wsf", "wsh",
            "ps1", "psm1", "jar", "app", "dmg", "deb", "rpm");

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
    /**
     * Si el nombre corresponde a un ejecutable, cualquiera sea el tipo declarado.
     *
     * <p>Se juzga por la ultima extension a proposito: "informe.pdf.exe" es un ejecutable con un
     * nombre disfrazado, y mirar la primera extension es justamente el descuido que ese nombre
     * busca explotar.
     */
    public static boolean isBlockedExecutable(String fileName) {
        String extension = fileName == null ? null : FileUtils.extractExtension(fileName);
        return extension != null && BLOCKED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    public static boolean matchesExtension(String fileName, String mimeType) {
        if (fileName == null || mimeType == null) {
            return false;
        }
        // Se corta antes de mirar el tipo: ningun MIME legitima un ejecutable.
        if (isBlockedExecutable(fileName)) {
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
