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
 * <p>Los formatos viejos de Office —{@code .doc}, {@code .xls}, {@code .ppt}— no son ZIP ni XML
 * sino contenedores OLE2 binarios, y su especificación es enorme. Leerlos bien exigiría una suite
 * ofimática entera como dependencia; acá se los recorre juntando las cadenas legibles que quedan
 * entre los bytes de formato. No reconstruye el documento y no lo pretende: para buscar alcanza
 * con que los títulos, las celdas y los párrafos aparezcan en el índice.
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

    /**
     * Letras que tiene que tener una cadena para considerarla texto.
     *
     * <p>Se cuentan letras y no caracteres: el encabezado de un contenedor OLE2 son bytes altos
     * que en Latin-1 se leen como "à¡±", tres caracteres imprimibles que no son una palabra. Con
     * el umbral sobre la cantidad de letras, esa clase de ruido —y los "12.5" sueltos de una
     * planilla— se queda afuera sin necesidad de enumerar cada caso.
     */
    private static final int MIN_RUN = 3;

    /**
     * Nombres de la estructura interna de un archivo OLE2, que no son contenido.
     *
     * <p>Son los flujos y los identificadores que todo documento viejo de Office lleva adentro.
     * Sin este filtro, buscar "Workbook" devolvería absolutamente todos los .xls del sistema, que
     * es la forma más rápida de volver inútil una búsqueda.
     */
    private static final Set<String> OLE2_NOISE = Set.of(
            "root entry", "worddocument", "1table", "0table", "data", "objectpool", "compobj",
            "summaryinformation", "documentsummaryinformation", "workbook", "book",
            "powerpoint document", "current user", "pictures", "persistdirectory",
            "microsoft office", "microsoft word", "microsoft excel", "microsoft powerpoint",
            "word.document.8", "excel.sheet.8", "powerpoint.show.8", "msworddoc",
            "arial", "calibri", "times new roman", "normal.dot");

    /** Formatos que ya son texto y se leen directamente. */
    private static final Set<String> PLAIN_TEXT = Set.of(
            "txt", "csv", "tsv", "md", "json", "xml", "log",
            "sql", "yaml", "yml", "ini", "conf", "html", "htm");

    /**
     * Formatos binarios antiguos: OLE2 ({@code .doc}, {@code .xls}, {@code .ppt}) y el BIFF12 de
     * {@code .xlsb}, que es un ZIP con partes binarias en lugar de XML.
     */
    private static final Set<String> LEGACY_BINARY = Set.of("doc", "xls", "ppt", "xlsb");

    /**
     * Grupos RTF que describen el formato y no el texto.
     *
     * <p>La tabla de fuentes y la de colores son listas de nombres y números que, sin este filtro,
     * entrarían al índice como si fueran contenido del documento.
     */
    private static final Set<String> RTF_SKIPPED_GROUPS = Set.of(
            "fonttbl", "colortbl", "stylesheet", "info", "pict", "filetbl", "listtable",
            "listoverridetable", "rsidtbl", "generator", "themedata", "datastore",
            "latentstyles", "xmlnstbl", "shppict", "nonshppict", "object", "fldinst");

    /** Controles RTF que separan bloques de texto. */
    private static final Set<String> RTF_BREAKS = Set.of(
            "par", "pard", "line", "tab", "cell", "row", "sect", "page", "column");

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
            if ("rtf".equals(extension)) {
                return normalize(textOfRtf(content));
            }
            if (LEGACY_BINARY.contains(extension)) {
                return normalize(textOfLegacyBinary(content));
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

    // ── Office binario antiguo (.doc, .xls, .ppt, .xlsb) ────────────────────

    /**
     * Texto legible de un binario de Office, sin interpretar su estructura.
     *
     * <p>Un {@code .doc} es un contenedor OLE2: sectores, tablas de asignación y flujos con el
     * formato entrelazado. Interpretarlo de verdad exige una suite ofimática como dependencia, y
     * este proyecto no la carga por una búsqueda. En su lugar se recorre el archivo juntando las
     * secuencias de caracteres imprimibles, que es donde terminan los títulos, las celdas y los
     * párrafos. El resultado no reconstruye el documento: lo vuelve encontrable.
     *
     * <p>Se recorre dos veces porque conviven dos codificaciones. Word y PowerPoint guardan el
     * texto en UTF-16LE —cada letra seguida de un cero— y Excel usa, según la versión y la cadena,
     * UTF-16LE o un byte por carácter. Las dos pasadas casi no se pisan: una cadena UTF-16 no
     * forma secuencias de un byte por el cero intercalado, y una de un byte no sobrevive al salto
     * de a dos.
     */
    private String textOfLegacyBinary(byte[] content) throws Exception {
        // Un .xlsb es un ZIP con partes binarias, y un archivo renombrado a .doc puede ser un
        // .docx: en los dos casos hay que entrar al contenedor antes de buscar texto suelto.
        byte[] payload = looksLikeZip(content) ? binaryPartsOfZip(content) : content;

        StringBuilder out = new StringBuilder();
        Set<String> seen = new java.util.HashSet<>();
        appendWideRuns(payload, out, seen);
        appendNarrowRuns(payload, out, seen);
        return out.length() == 0 ? null : out.toString();
    }

    private static boolean looksLikeZip(byte[] content) {
        return content.length > 4 && content[0] == 'P' && content[1] == 'K'
                && content[2] == 3 && content[3] == 4;
    }

    /**
     * Junta las partes de un contenedor ZIP para buscar texto en ellas.
     *
     * <p>En un {@code .xlsb} la parte que importa es {@code xl/sharedStrings.bin}, donde viven las
     * cadenas de las celdas igual que en el {@code sharedStrings.xml} de un {@code .xlsx}. Si no
     * está, se recorren las demás: es preferible un índice con algo de ruido a un archivo que no
     * aparece en ninguna búsqueda.
     */
    private byte[] binaryPartsOfZip(byte[] content) throws Exception {
        java.io.ByteArrayOutputStream cadenas = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream resto = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                boolean esTablaDeCadenas = name.endsWith("sharedStrings.bin")
                        || name.endsWith("sharedStrings.xml");
                java.io.ByteArrayOutputStream destino = esTablaDeCadenas ? cadenas : resto;
                // El tope evita que un libro de cien megabytes se copie entero en memoria para
                // quedarse con sus primeras veinte mil letras.
                if (destino.size() > MAX_CHARS * 4) {
                    zip.closeEntry();
                    continue;
                }
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    destino.write(buffer, 0, read);
                }
            }
        }
        return cadenas.size() > 0 ? cadenas.toByteArray() : resto.toByteArray();
    }

    /** Cadenas en UTF-16LE: cada carácter imprimible seguido de un byte cero. */
    private void appendWideRuns(byte[] data, StringBuilder out, Set<String> seen) {
        StringBuilder run = new StringBuilder();
        for (int i = 0; i + 1 < data.length && out.length() < MAX_CHARS; i += 2) {
            char c = (char) ((data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8));
            if (isReadable(c)) {
                run.append(c);
            } else {
                flushRun(run, out, seen);
            }
        }
        flushRun(run, out, seen);
    }

    /** Cadenas de un byte por carácter, como las guarda Excel en muchas de sus celdas. */
    private void appendNarrowRuns(byte[] data, StringBuilder out, Set<String> seen) {
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < data.length && out.length() < MAX_CHARS; i++) {
            char c = (char) (data[i] & 0xFF);
            if (isReadable(c)) {
                run.append(c);
            } else {
                flushRun(run, out, seen);
            }
        }
        flushRun(run, out, seen);
    }

    /**
     * Letras, dígitos, puntuación y acentos; nada de control.
     *
     * <p>El rango 0x80-0x9F queda afuera a propósito: en Latin-1 son caracteres de control, y es
     * justo donde caen los bytes de formato que separan una cadena de la siguiente.
     */
    private static boolean isReadable(char c) {
        if (c >= 0x20 && c <= 0x7E) {
            return true;
        }
        return c >= 0xA0 && c <= 0x24F;
    }

    /** Cierra la cadena en curso y la guarda si vale la pena. */
    private void flushRun(StringBuilder run, StringBuilder out, Set<String> seen) {
        String value = trimToWord(run.toString());
        run.setLength(0);
        if (value.isEmpty() || OLE2_NOISE.contains(value.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (value.chars().filter(Character::isLetter).count() < MIN_RUN) {
            return;
        }
        // Lo repetido se guarda una sola vez: una planilla escribe el mismo encabezado en cada
        // fila, y guardarlo cien veces solo consume el tope de caracteres del documento.
        if (!seen.add(value)) {
            return;
        }
        out.append(value).append(' ');
    }

    /**
     * Recorta los extremos que no son parte de la palabra.
     *
     * <p>Una cadena rescatada de un binario suele arrastrar el byte de formato que la precede o la
     * sigue, y ese byte puede ser imprimible. Sin recortarlo, la misma palabra entra al índice dos
     * veces —con basura y sin ella— y la deduplicación no la reconoce.
     */
    private static String trimToWord(String raw) {
        int start = 0;
        int end = raw.length();
        while (start < end && !Character.isLetterOrDigit(raw.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(raw.charAt(end - 1))) {
            end--;
        }
        return raw.substring(start, end);
    }

    // ── RTF ─────────────────────────────────────────────────────────────────

    /**
     * Texto de un RTF, sin sus códigos de control.
     *
     * <p>Antes se lo trataba como texto plano, así que el índice se llenaba con la tabla de
     * fuentes, la de colores y cada instrucción de formato: buscar "Arial" devolvía todos los RTF
     * del sistema y el texto real quedaba sepultado entre llaves.
     *
     * <p>El recorrido sigue las tres piezas de la gramática: los grupos entre llaves, las palabras
     * de control que empiezan con barra invertida, y el texto que queda entre medio. Los grupos
     * que solo describen el formato se descartan enteros.
     */
    private String textOfRtf(byte[] content) {
        // ISO-8859-1 y no UTF-8: un RTF declara su codificación por página de código y escribe lo
        // que no entra como una secuencia hexadecimal. Leer byte a byte evita que una secuencia
        // inválida corte la lectura del documento entero.
        String rtf = new String(content, StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder();
        int depth = 0;
        // Profundidad del grupo que se está descartando, o -1 si se está copiando texto.
        int skipDepth = -1;
        int i = 0;

        while (i < rtf.length() && out.length() < MAX_CHARS) {
            char c = rtf.charAt(i);

            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                if (skipDepth >= 0 && depth == skipDepth) {
                    skipDepth = -1;
                }
                depth--;
                i++;
                continue;
            }
            if (c != '\\') {
                // Los saltos de línea del archivo son formato, no texto: el RTF marca sus párrafos
                // con una palabra de control, no con un salto.
                if (c != '\r' && c != '\n' && skipDepth < 0) {
                    out.append(c);
                }
                i++;
                continue;
            }

            // A partir de acá, una palabra o un símbolo de control.
            i++;
            if (i >= rtf.length()) {
                break;
            }
            char next = rtf.charAt(i);

            if (next == '\'') {
                // Un byte escrito en hexadecimal, que es como el RTF guarda los acentos.
                if (i + 2 < rtf.length()) {
                    try {
                        int code = Integer.parseInt(rtf.substring(i + 1, i + 3), 16);
                        if (skipDepth < 0) {
                            out.append((char) code);
                        }
                    } catch (NumberFormatException e) {
                        // Secuencia mal formada: se pierde ese carácter y sigue el documento.
                    }
                    i += 3;
                } else {
                    i++;
                }
                continue;
            }

            if (!Character.isLetter(next)) {
                // El asterisco marca un grupo que todo lector que no lo entienda debe descartar
                // entero; el resto de los símbolos son el carácter literal.
                if (next == '*') {
                    skipDepth = depth;
                } else if (skipDepth < 0) {
                    out.append(next);
                }
                i++;
                continue;
            }

            int wordStart = i;
            while (i < rtf.length() && Character.isLetter(rtf.charAt(i))) {
                i++;
            }
            String word = rtf.substring(wordStart, i);

            int paramStart = i;
            if (i < rtf.length() && rtf.charAt(i) == '-') {
                i++;
            }
            while (i < rtf.length() && Character.isDigit(rtf.charAt(i))) {
                i++;
            }
            String param = rtf.substring(paramStart, i);
            // El espacio que sigue a una palabra de control es su delimitador, no texto: copiarlo
            // llenaría el documento de espacios que nadie escribió.
            if (i < rtf.length() && rtf.charAt(i) == ' ') {
                i++;
            }

            if (RTF_SKIPPED_GROUPS.contains(word)) {
                skipDepth = depth;
                continue;
            }
            if (skipDepth >= 0) {
                continue;
            }
            if (RTF_BREAKS.contains(word)) {
                out.append(' ');
            } else if ("u".equals(word) && !param.isEmpty()) {
                i = appendRtfUnicode(rtf, i, param, out);
            }
        }
        return out.toString();
    }

    /**
     * Escribe un carácter Unicode del RTF y saltea su reemplazo.
     *
     * <p>El formato acompaña cada carácter Unicode con un sustituto para lectores viejos. Sin
     * saltearlo, cada acento entra dos veces al índice: una bien y otra como interrogación.
     */
    private int appendRtfUnicode(String rtf, int i, String param, StringBuilder out) {
        try {
            int code = Integer.parseInt(param);
            // El parámetro es un entero con signo: los códigos por encima de 32767 se escriben
            // negativos.
            out.append((char) (code < 0 ? code + 65536 : code));
        } catch (NumberFormatException e) {
            return i;
        }
        if (i < rtf.length()) {
            char sustituto = rtf.charAt(i);
            if (sustituto != '\\' && sustituto != '{' && sustituto != '}') {
                return i + 1;
            }
        }
        return i;
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
