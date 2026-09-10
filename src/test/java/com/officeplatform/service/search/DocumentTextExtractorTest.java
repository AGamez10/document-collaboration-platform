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

    @Test
    @DisplayName("a format that yields no text is left unindexed instead of failing")
    void leavesUnsupportedFormatsAlone() {
        assertThat(extractor.extract(new byte[] { 1, 2, 3 }, "plano.pdf")).isNull();
        assertThat(extractor.extract(new byte[] { 1, 2, 3 }, "foto.jpg")).isNull();
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
