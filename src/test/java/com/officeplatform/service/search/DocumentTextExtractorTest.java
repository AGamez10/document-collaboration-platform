package com.officeplatform.service.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pulling searchable text out of a document.
 *
 * <p>Done without an office library on purpose: a {@code .docx} is a ZIP holding XML, so reading
 * {@code word/document.xml} and keeping what sits between the {@code <w:t>} tags is enough — the
 * same approach this project already uses to <b>write</b> documents. Adding Apache POI for this
 * would cost tens of megabytes of dependencies and minutes of build time to solve thirty lines.
 *
 * <p>The cases that matter here are the ones where a naive reading goes wrong: markup leaking into
 * the text, words from separate paragraphs fusing into one nobody wrote, and a corrupt file taking
 * down the upload that carried it.
 */
class DocumentTextExtractorTest {

    private DocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DocumentTextExtractor();
    }

    /** Arma un .docx mínimo con el XML de Word que se le pase. */
    private byte[] docx(String documentXml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private String parrafo(String texto) {
        return "<w:p><w:r><w:t>" + texto + "</w:t></w:r></w:p>";
    }

    /** ZIP con una sola parte, que es la forma de todo OOXML y OpenDocument. */
    private byte[] zipWith(String partName, String xml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(partName));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    /** PDF real, generado con la misma libreria que despues lo lee. */
    private byte[] pdf(String texto) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(texto);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── .docx ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the words of a document come out without any of its markup")
    void extractsTheWordsOfADocx() throws Exception {
        byte[] file = docx("<w:document><w:body>"
                + parrafo("Procedimiento de calidad ISO 9001")
                + "</w:body></w:document>");

        String text = extractor.extract(file, "procedimiento.docx");

        assertThat(text).isEqualTo("Procedimiento de calidad ISO 9001");
        // Si el marcado se filtra, buscar "w" o "document" devolveria todos los archivos.
        assertThat(text).doesNotContain("<").doesNotContain("w:t");
    }

    @Test
    @DisplayName("words from separate paragraphs do not fuse into one nobody wrote")
    void keepsParagraphsApart() throws Exception {
        byte[] file = docx("<w:document><w:body>"
                + parrafo("Primera") + parrafo("Segunda")
                + "</w:body></w:document>");

        String text = extractor.extract(file, "doc.docx");

        // Sin separador quedaria "PrimeraSegunda", que no coincide con ninguna de las dos.
        assertThat(text).isEqualTo("Primera Segunda");
    }

    @Test
    @DisplayName("XML entities come back as the characters the author actually typed")
    void unescapesEntities() throws Exception {
        byte[] file = docx("<w:document><w:body>"
                + parrafo("Ventas &amp; Marketing &lt;2026&gt;")
                + "</w:body></w:document>");

        assertThat(extractor.extract(file, "doc.docx")).isEqualTo("Ventas & Marketing <2026>");
    }

    @Test
    @DisplayName("a very long document is cut at the cap instead of filling the database")
    void truncatesALongDocument() throws Exception {
        String largo = "palabra ".repeat(6000);
        byte[] file = docx("<w:document><w:body>" + parrafo(largo) + "</w:body></w:document>");

        String text = extractor.extract(file, "manual.docx");

        // Un manual de trescientas paginas no aporta mas a una busqueda que sus primeras veinte
        // mil letras, y guardarlo entero multiplica base y respaldos.
        assertThat(text).hasSizeLessThanOrEqualTo(DocumentTextExtractor.MAX_CHARS);
    }

    @Test
    @DisplayName("a macro-enabled document is read like any other Word file")
    void readsDocm() throws Exception {
        byte[] file = docx("<w:document><w:body>" + parrafo("Con macros") + "</w:body></w:document>");

        assertThat(extractor.extract(file, "planilla.docm")).isEqualTo("Con macros");
    }

    // ── texto plano ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("plain formats are read directly")
    void readsPlainText() {
        byte[] txt = "Acta de reunion del 12 de marzo".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(txt, "acta.txt")).isEqualTo("Acta de reunion del 12 de marzo");
        assertThat(extractor.extract("a,b,c".getBytes(StandardCharsets.UTF_8), "datos.csv")).isEqualTo("a,b,c");
        assertThat(extractor.extract("# Titulo".getBytes(StandardCharsets.UTF_8), "notas.md")).isEqualTo("# Titulo");
    }

    @Test
    @DisplayName("accents survive the extraction")
    void keepsAccents() {
        byte[] txt = "Presupuesto de diseño y producción".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(txt, "presupuesto.txt")).isEqualTo("Presupuesto de diseño y producción");
    }

    // ── lo que no da texto ───────────────────────────────────────────────────

    // ── el resto de los formatos ofimaticos ─────────────────────────────────

    @Test
    @DisplayName("a spreadsheet gives up the text of its cells")
    void extractsTheTextOfASpreadsheet() throws Exception {
        // Excel guarda una sola vez cada cadena en sharedStrings y las celdas la referencian:
        // es la parte que concentra el texto del libro.
        byte[] file = zipWith("xl/sharedStrings.xml",
                "<sst><si><t>Presupuesto anual</t></si><si><t>Soldadura</t></si></sst>");

        String text = extractor.extract(file, "planilla.xlsx");

        assertThat(text).contains("Presupuesto anual").contains("Soldadura");
        assertThat(text).doesNotContain("<").doesNotContain("sst");
    }

    @Test
    @DisplayName("a macro-enabled workbook is read like any other spreadsheet")
    void readsXlsm() throws Exception {
        byte[] file = zipWith("xl/sharedStrings.xml", "<sst><si><t>Con macros</t></si></sst>");

        assertThat(extractor.extract(file, "planilla.xlsm")).isEqualTo("Con macros");
    }

    @Test
    @DisplayName("a presentation gives up the text of its slides")
    void extractsTheTextOfAPresentation() throws Exception {
        byte[] file = zipWith("ppt/slides/slide1.xml",
                "<p:sld><p:cSld><a:p><a:r><a:t>Plan de calidad</a:t></a:r></a:p></p:cSld></p:sld>");

        assertThat(extractor.extract(file, "charla.pptx")).contains("Plan de calidad");
    }

    @Test
    @DisplayName("an OpenDocument file gives up the text of its content part")
    void extractsTheTextOfAnOpenDocument() throws Exception {
        byte[] file = zipWith("content.xml",
                "<office:document-content><text:p>Acta de reunion</text:p>"
                        + "<text:p><text:span>Segundo parrafo</text:span></text:p></office:document-content>");

        String text = extractor.extract(file, "acta.odt");

        assertThat(text).contains("Acta de reunion").contains("Segundo parrafo");
    }

    @Test
    @DisplayName("a PDF gives up its text instead of staying unsearchable")
    void extractsTheTextOfAPdf() throws Exception {
        // Era el formato mas numeroso entre los que quedaban sin indexar.
        byte[] file = pdf("Procedimiento de soldadura ISO 9001");

        assertThat(extractor.extract(file, "procedimiento.pdf"))
                .contains("Procedimiento de soldadura ISO 9001");
    }

    @Test
    @DisplayName("a corrupt PDF is treated as having no text, not as a failure")
    void survivesACorruptPdf() throws Exception {
        assertThat(extractor.extract("esto no es un pdf".getBytes(StandardCharsets.UTF_8), "roto.pdf"))
                .isNull();
    }

    @Test
    @DisplayName("the additional text and configuration formats are read directly")
    void readsTheAdditionalTextFormats() {
        for (String name : new String[] { "consulta.sql", "config.yaml", "config.yml",
                                          "ajustes.ini", "servidor.conf", "datos.tsv", "pagina.html" }) {
            assertThat(extractor.extract("contenido buscable".getBytes(StandardCharsets.UTF_8), name))
                    .as("formato %s", name)
                    .isEqualTo("contenido buscable");
        }
    }

    @Test
    @DisplayName("binary and multimedia formats are left unindexed instead of failing")
    void leavesBinaryFormatsAlone() {
        // Imagen, audio, video y comprimidos se buscan por nombre y metadatos: pretender leerlos
        // solo gastaria tiempo. El PDF salio de esta lista porque ahora si se extrae.
        assertThat(extractor.extract(new byte[] { 1, 2, 3 }, "foto.jpg")).isNull();
        assertThat(extractor.extract(new byte[] { 1, 2, 3 }, "video.mp4")).isNull();
        assertThat(extractor.extract(new byte[] { 1, 2, 3 }, "audio.mp3")).isNull();
        assertThat(extractor.extract(new byte[] { 1, 2, 3 }, "paquete.zip")).isNull();
        assertThat(extractor.extract(new byte[] { 1, 2, 3 }, "sin-extension")).isNull();
    }

    @Test
    @DisplayName("a corrupt document never takes down the upload that carried it")
    void survivesACorruptDocument() {
        // Perder la busqueda por contenido de un archivo es un inconveniente; romper su subida
        // por eso seria absurdo.
        assertThat(extractor.extract("esto no es un zip".getBytes(StandardCharsets.UTF_8), "roto.docx"))
                .isNull();
    }

    @Test
    @DisplayName("empty input is handled instead of throwing")
    void handlesEmptyInput() {
        assertThat(extractor.extract(null, "a.txt")).isNull();
        assertThat(extractor.extract(new byte[0], "a.txt")).isNull();
        assertThat(extractor.extract("x".getBytes(StandardCharsets.UTF_8), null)).isNull();
    }

    @Test
    @DisplayName("a document with no text at all is stored as null, not as an empty string")
    void storesNullForAnEmptyDocument() throws Exception {
        byte[] file = docx("<w:document><w:body><w:p/></w:body></w:document>");

        // Un string vacio en la columna haria que un LIKE '%%' lo devolviera en toda busqueda.
        assertThat(extractor.extract(file, "vacio.docx")).isNull();
    }

}
