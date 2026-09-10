package com.officeplatform.service.search;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import com.officeplatform.util.FileUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Extrae el texto de un documento para poder buscarlo por su contenido.
 *
 * <p>Sin librerías de ofimática. Un {@code .docx} es un ZIP con XML adentro, así que alcanza con
 * abrir {@code word/document.xml} y quedarse con lo que hay entre las etiquetas {@code <w:t>},
 * que es exactamente el enfoque que este proyecto ya usa para <b>generar</b> documentos en
 * {@code DocxUtils}. Sumar Apache POI por esto costaría decenas de megabytes de dependencias y
 * varios minutos de build para resolver un problema que son treinta líneas.
 *
 * <p>Nunca propaga errores. Un documento corrupto, cifrado o de un formato inesperado deja el
 * texto en null y el archivo se sigue encontrando por su nombre: perder la búsqueda por contenido
 * de un archivo es un inconveniente, romper su subida por eso sería absurdo.
 */
@Component
@Slf4j
public class DocumentTextExtractor {

    /**
     * Tope de caracteres que se guardan por documento.
     *
     * <p>Un manual de trescientas páginas no aporta más a una búsqueda que sus primeras veinte mil
     * letras, y guardarlo entero multiplica el tamaño de la base y el de cada respaldo. El corte
     * es por memoria y por base de datos, no por capacidad del extractor.
     */
    static final int MAX_CHARS = 20_000;

    /** Lo que se lee del ZIP de un OOXML: el resto son estilos, relaciones y metadatos. */
    private static final String DOCX_MAIN_PART = "word/document.xml";

    /** Formatos que ya son texto y se leen directamente. */
    private static final java.util.Set<String> PLAIN_TEXT =
            java.util.Set.of("txt", "csv", "md", "json", "xml", "log");

    /**
     * Texto buscable de un archivo, o null si su formato no da texto.
     *
     * @param content   contenido binario del archivo
     * @param fileName  nombre original, del que se toma la extensión
     */
    public String extract(byte[] content, String fileName) {
        if (content == null || content.length == 0 || fileName == null) {
            return null;
        }
        String extension = FileUtils.extractExtension(fileName);
        if (extension == null || extension.isBlank()) {
            return null;
        }
        extension = extension.toLowerCase(Locale.ROOT);

        try {
            if ("docx".equals(extension) || "docm".equals(extension)) {
                return normalize(extractFromDocx(content));
            }
            if (PLAIN_TEXT.contains(extension)) {
                return normalize(new String(content, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            // Un formato roto no puede impedir que el archivo exista. Se pierde la busqueda por
            // contenido de ese documento y nada mas.
            log.debug("No se pudo extraer texto de '{}': {}", fileName, e.getMessage());
        }
        return null;
    }

    /**
     * Texto de un {@code .docx}, leyendo su parte principal en streaming.
     *
     * <p>Se recorre el ZIP sin materializarlo entero y se corta apenas se alcanza el tope: un
     * documento de cien megabytes no debe ocupar cien megabytes de memoria para aportar veinte mil
     * caracteres.
     */
    private String extractFromDocx(byte[] content) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!DOCX_MAIN_PART.equals(entry.getName())) {
                    zip.closeEntry();
                    continue;
                }
                return textOfWordXml(zip);
            }
        }
        return null;
    }

    /**
     * Junta el contenido de las etiquetas {@code <w:t>} del XML de Word.
     *
     * <p>Se hace con un recorrido de caracteres y no con un parser XML porque el documento puede
     * pesar cientos de megabytes: un DOM completo en memoria para quedarse con las primeras veinte
     * mil letras sería desproporcionado. Los saltos de párrafo se conservan como espacios para que
     * dos palabras de párrafos distintos no queden pegadas y formen una que nadie escribió.
     */
    private String textOfWordXml(InputStream xml) throws Exception {
        StringBuilder out = new StringBuilder(Math.min(MAX_CHARS, 8192));
        StringBuilder tag = new StringBuilder(32);
        boolean insideTag = false;
        boolean insideText = false;

        byte[] buffer = new byte[8192];
        int read;
        while ((read = xml.read(buffer)) != -1 && out.length() < MAX_CHARS) {
            String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
            for (int i = 0; i < chunk.length() && out.length() < MAX_CHARS; i++) {
                char c = chunk.charAt(i);
                if (c == '<') {
                    insideTag = true;
                    tag.setLength(0);
                } else if (c == '>') {
                    insideTag = false;
                    String name = tag.toString();
                    if (name.startsWith("w:t") && !name.startsWith("w:tab")) {
                        insideText = !name.startsWith("/") && !name.endsWith("/");
                    } else if (name.startsWith("/w:t")) {
                        insideText = false;
                    } else if (name.startsWith("w:p ") || name.equals("w:p") || name.startsWith("w:br")) {
                        // Sin esto, el final de un parrafo y el inicio del siguiente se pegan.
                        out.append(' ');
                    }
                } else if (insideTag) {
                    tag.append(c);
                } else if (insideText) {
                    out.append(c);
                }
            }
        }
        return unescapeXml(out.toString());
    }

    /** Las cinco entidades que Word escribe; el resto del XML no llega hasta acá. */
    private static String unescapeXml(String raw) {
        return raw.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    /** Colapsa espacios y recorta al tope, para no guardar ruido de formato. */
    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > MAX_CHARS ? cleaned.substring(0, MAX_CHARS) : cleaned;
    }

}
