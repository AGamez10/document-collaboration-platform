package com.officeplatform.service.editor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.officeplatform.entity.FileEntity;

/**
 * Which engine OnlyOffice is told to open a document with.
 *
 * <p>The extension decides that, and a row without one made {@code resolveDocumentType} fall into
 * its default branch and hand a spreadsheet to the Word converter. What the user saw was a screen
 * frozen on "Cargando editor…"; nothing named the real cause.
 *
 * <p>The original file name always carries the extension, so it is used as the fallback instead of
 * trusting the column. These cases pin both halves: the fallback itself, and that every spreadsheet
 * format reaches {@code cell} — which is the mapping that actually keeps a workbook from being
 * handed to the wrong engine.
 */
class EditorExtensionResolutionTest {

    /** Ambas son funciones puras y de paquete: se las llama directo, sin reflexión ni mocks. */
    private String extensionOf(String column, String fileName) {
        FileEntity file = new FileEntity();
        file.setExtension(column);
        file.setOriginalFileName(fileName);
        return EditorServiceImpl.resolveExtension(file);
    }

    private String documentTypeOf(String extension) {
        return EditorServiceImpl.resolveDocumentType(extension);
    }

    // ── resolución de la extensión ───────────────────────────────────────────

    @Test
    @DisplayName("the stored extension is used when it is there")
    void usesTheStoredExtension() {
        assertThat(extensionOf("xlsm", "planilla.xlsm")).isEqualTo("xlsm");
    }

    @Test
    @DisplayName("an empty column falls back to the name instead of leaving it undecided")
    void fallsBackToTheFileName() {
        // Este es el caso que dejaba la pantalla congelada: sin extension, un libro de Excel se
        // le entregaba al conversor de Word.
        assertThat(extensionOf(null, "planilla.xlsm")).isEqualTo("xlsm");
        assertThat(extensionOf("", "planilla.xlsx")).isEqualTo("xlsx");
        assertThat(extensionOf("   ", "presentacion.pptx")).isEqualTo("pptx");
    }

    @Test
    @DisplayName("the extension is normalised, so an uppercase name resolves like any other")
    void normalisesTheExtension() {
        assertThat(extensionOf("XLSM", "PLANILLA.XLSM")).isEqualTo("xlsm");
        assertThat(extensionOf(null, "PLANILLA.XLSM")).isEqualTo("xlsm");
    }

    @Test
    @DisplayName("a name with no extension resolves to nothing rather than to a wrong guess")
    void returnsNullWhenThereIsNothingToResolve() {
        // Es el caso real de "Actividad1": sin punto no hay nada que deducir, y inventar una
        // extension seria peor que admitir que no se sabe.
        assertThat(extensionOf(null, "Actividad1")).isNull();
        assertThat(extensionOf(null, null)).isNull();
    }

    // ── tipo de documento ────────────────────────────────────────────────────

    @Test
    @DisplayName("every spreadsheet format reaches the spreadsheet engine")
    void mapsSpreadsheetsToCell() {
        for (String ext : new String[] { "xls", "xlsx", "xlsm", "xltm", "xlsb", "ods", "csv" }) {
            assertThat(documentTypeOf(ext)).as("extensión %s", ext).isEqualTo("cell");
        }
    }

    @Test
    @DisplayName("presentations and text documents reach their own engines")
    void mapsTheOtherFamilies() {
        for (String ext : new String[] { "ppt", "pptx", "pptm", "potm", "odp" }) {
            assertThat(documentTypeOf(ext)).as("extensión %s", ext).isEqualTo("slide");
        }
        for (String ext : new String[] { "doc", "docx", "docm", "dotm", "odt" }) {
            assertThat(documentTypeOf(ext)).as("extensión %s", ext).isEqualTo("word");
        }
    }

    @Test
    @DisplayName("an unknown extension still gets a usable default")
    void keepsADefaultForTheUnknown() {
        assertThat(documentTypeOf(null)).isEqualTo("word");
        assertThat(documentTypeOf("raro")).isEqualTo("word");
    }

}
