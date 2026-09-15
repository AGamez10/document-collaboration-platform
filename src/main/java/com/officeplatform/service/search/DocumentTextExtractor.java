package com.officeplatform.service.search;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import com.officeplatform.util.FileUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Extrae el texto de un documento para poder buscarlo por su contenido.
 *
 * <p>Casi todo se resuelve sin librerías. Los formatos ofimáticos modernos —OOXML y OpenDocument—
 * son ZIP con XML adentro, así que alcanza con abrir la parte que lleva el texto y quedarse con lo
 * que hay entre sus etiquetas. Es el mismo enfoque que este proyecto ya usa para <b>generar</b>
 * documentos en {@code DocxUtils}. La única excepción es el PDF, cuyo texto vive en flujos
 * comprimidos con su propia gramática: ahí sí hace falta PDFBox.
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

    /**
     * Páginas de PDF que se recorren como máximo.
     *
     * <p>Un PDF escaneado de quinientas páginas tarda minutos en procesarse entero para aportar,
     * casi siempre, nada: el texto que identifica un documento está al principio.
     */
    private static final int MAX_PDF_PAGES = 15;

    /** Formatos que ya son texto y se leen directamente. */
    private static final Set<String> PLAIN_TEXT = Set.of(
            "txt", "csv", "tsv", "md", "json", "xml", "log",
            "sql", "yaml", "yml", "ini", "conf", "rtf", "html", "htm");

    /** Word: el texto vive entre etiquetas {@code <w:t>} de la parte principal. */
    private static final Set<String> WORD = Set.of("docx", "docm", "dotm");

    /** Excel: el grueso del texto está en la tabla de cadenas compartidas. */
    private static final Set<String> EXCEL = Set.of("xlsx", "xlsm", "xltm");

    /** PowerPoint: cada diapositiva es su propia parte XML. */
    private static final Set<String> POWERPOINT = Set.of("pptx", "pptm", "potm");

    /** OpenDocument: texto, hoja y presentación comparten un mismo {@code content.xml}. */
    private static final Set<String> OPEN_DOCUMENT = Set.of("odt", "ods", "odp");

    /**
     * Texto buscable de un archivo, o null si su formato no da texto.
     *
     * <p>Las imágenes, el audio, el video y los comprimidos caen acá deliberadamente: se buscan
     * por su nombre y sus metadatos, y pretender leerlos solo gastaría tiempo.
     *
     * @param content  contenido binario del archivo
     * @param fileName nombre original, del que se toma la extensión
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
            if (WORD.contains(extension)) {
                return normalize(textOfZipPart(content, "word/document.xml", "w:t"));
            }
            if (EXCEL.contains(extension)) {
                return normalize(textOfSpreadsheet(content));
            }
            if (POWERPOINT.contains(extension)) {
                return normalize(textOfPresentation(content));
            }
            if (OPEN_DOCUMENT.contains(extension)) {
                // OpenDocument marca el texto con varias etiquetas distintas; el recorrido las
                // trata por prefijo comun en lugar de enumerarlas una por una.
                return normalize(textOfZipPart(content, "content.xml", "text:"));
            }
            if ("pdf".equals(extension)) {
                return normalize(textOfPdf(content));
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

    // ── OOXML y OpenDocument ────────────────────────────────────────────────

    /**
     * Texto de una parte concreta dentro del ZIP de un documento.
     *
     * @param partName nombre exacto de la entrada a leer
     * @param textTag  prefijo de las etiquetas que envuelven el texto
     */
    private String textOfZipPart(byte[] content, String partName, String textTag) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (partName.equals(entry.getName())) {
                    return textOfXml(zip, textTag, new StringBuilder());
                }
                zip.closeEntry();
            }
        }
        return null;
    }

    /**
     * Texto de un libro de Excel.
     *
     * <p>Se lee {@code xl/sharedStrings.xml}, donde Excel guarda una sola vez cada cadena que
     * aparece en las celdas: es la parte que concentra el texto del libro. Las hojas en sí
     * contienen sobre todo referencias numéricas a esa tabla, fórmulas y estilos, así que
     * recorrerlas aportaría ruido y muy pocas palabras nuevas.
     */
    private String textOfSpreadsheet(byte[] content) throws Exception {
        StringBuilder out = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && out.length() < MAX_CHARS) {
                if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    textOfXml(zip, "t", out);
                    break;
                }
                zip.closeEntry();
            }
        }
        return out.length() == 0 ? null : out.toString();
    }

    /**
     * Texto de una presentación, recorriendo sus diapositivas.
     *
     * <p>Las entradas del ZIP no vienen ordenadas por número de diapositiva, así que el texto sale
     * en el orden en que estén guardadas. Para buscar da igual: lo que importa es que la palabra
     * esté, no en qué lámina aparece.
     */
    private String textOfPresentation(byte[] content) throws Exception {
        StringBuilder out = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && out.length() < MAX_CHARS) {
                String name = entry.getName();
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    textOfXml(zip, "a:t", out);
                    // No se cierra la entrada acá: textOfXml ya la consumió y el propio
                    // getNextEntry avanza a la siguiente.
                } else {
                    zip.closeEntry();
                }
            }
        }
        return out.length() == 0 ? null : out.toString();
    }

    /**
     * Junta el contenido de las etiquetas de texto de un XML.
     *
     * <p>Se hace con un recorrido de caracteres y no con un parser XML porque el documento puede
     * pesar cientos de megabytes: un DOM completo en memoria para quedarse con las primeras veinte
     * mil letras sería desproporcionado. Los saltos de párrafo y de celda se conservan como
     * espacios para que dos palabras contiguas no queden pegadas formando una que nadie escribió.
     *
     * @param textTag prefijo de la etiqueta que envuelve el texto ({@code w:t}, {@code a:t},
     *                {@code t} o {@code text:})
     */
    private String textOfXml(InputStream xml, String textTag, StringBuilder out) throws Exception {
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
                    if (name.startsWith("/")) {
                        if (name.substring(1).startsWith(textTag)) {
                            insideText = false;
                            // Separador al cerrar: sin esto, celdas o parrafos contiguos se pegan.
                            out.append(' ');
                        }
                    } else if (isTextTag(name, textTag)) {
                        insideText = !name.endsWith("/");
                    } else if (isBreak(name)) {
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

    /**
     * Si la etiqueta abre texto.
     *
     * <p>Se compara contra el nombre exacto o contra el nombre seguido de un espacio, porque una
     * etiqueta puede traer atributos. Sin esa distinción, buscar el prefijo {@code t} dentro de un
     * XML de Excel tomaría también {@code <tableParts>} y metería basura en el índice. Para
     * OpenDocument el prefijo {@code text:} sí abarca varias etiquetas a propósito.
     */
    private static boolean isTextTag(String name, String textTag) {
        if (textTag.endsWith(":")) {
            return name.startsWith(textTag);
        }
        return name.equals(textTag) || name.startsWith(textTag + " ") || name.equals(textTag + "/");
    }

    /** Etiquetas que separan bloques de texto en cualquiera de los formatos. */
    private static boolean isBreak(String name) {
        return name.startsWith("w:p ") || name.equals("w:p") || name.startsWith("w:br")
                || name.startsWith("a:p ") || name.equals("a:p")
                || name.startsWith("si ") || name.equals("si");
    }

    // ── PDF ─────────────────────────────────────────────────────────────────

    /**
     * Texto de un PDF, limitado a sus primeras páginas.
     *
     * <p>Es el único formato que necesita una librería: su texto vive en flujos comprimidos con
     * una gramática propia, no en XML que se pueda recorrer. Un PDF protegido por contraseña o
     * escaneado como imagen no da texto, y eso se trata como ausencia y no como error.
     */
    private String textOfPdf(byte[] content) {
        try (org.apache.pdfbox.pdmodel.PDDocument document =
                     org.apache.pdfbox.pdmodel.PDDocument.load(new ByteArrayInputStream(content))) {
            if (document.isEncrypted()) {
                // Se puede abrir pero no leer: devolver ausencia es mas honesto que devolver
                // el texto parcial que a veces deja un documento cifrado.
                return null;
            }
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PDF_PAGES));
            return stripper.getText(document);
        } catch (Exception | Error e) {
            // Error incluido a proposito: un PDF malformado puede hacer que PDFBox lance
            // OutOfMemoryError o StackOverflowError al recorrer su estructura, y eso no puede
            // tumbar la indexacion de todo el catalogo.
            log.debug("No se pudo leer el PDF: {}", e.getMessage());
            return null;
        }
    }

    // ── comunes ─────────────────────────────────────────────────────────────

    /** Las cinco entidades que los formatos ofimáticos escriben. */
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
