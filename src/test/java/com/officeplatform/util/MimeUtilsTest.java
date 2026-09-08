package com.officeplatform.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Upload type validation.
 *
 * <p>The declared content type is chosen by the client, so checking it alone let an executable
 * through as long as it claimed to be a PDF. These cases pin the extension agreement that closes
 * that bypass, and the legitimate uploads that must keep working.
 */
class MimeUtilsTest {

    private static final String PDF = "application/pdf";
    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String XLSM = "application/vnd.ms-excel.sheet.macroEnabled.12";
    private static final String DOCM = "application/vnd.ms-word.document.macroEnabled.12";
    private static final String PPTM = "application/vnd.ms-powerpoint.presentation.macroEnabled.12";
    private static final String XLS = "application/vnd.ms-excel";
    private static final String ODT = "application/vnd.oasis.opendocument.text";
    private static final String ODS = "application/vnd.oasis.opendocument.spreadsheet";
    private static final String ODP = "application/vnd.oasis.opendocument.presentation";

    private static final List<String> ALLOWED = List.of(PDF, XLSX, XLSM, DOCM, PPTM, XLS);

    @Test
    @DisplayName("an allowed type is accepted and an unlisted one is not")
    void allowsOnlyListedTypes() {
        assertThat(MimeUtils.isAllowed(PDF, ALLOWED)).isTrue();
        assertThat(MimeUtils.isAllowed("application/x-msdownload", ALLOWED)).isFalse();
        assertThat(MimeUtils.isAllowed(null, ALLOWED)).isFalse();
    }

    @Test
    @DisplayName("the extension must agree with the declared type")
    void extensionMustAgreeWithDeclaredType() {
        assertThat(MimeUtils.matchesExtension("informe.pdf", PDF)).isTrue();
        assertThat(MimeUtils.matchesExtension("hoja.xlsx", XLSX)).isTrue();
    }

    @Test
    @DisplayName("an executable claiming to be a PDF is rejected")
    void rejectsExecutableDeclaringPdf() {
        assertThat(MimeUtils.matchesExtension("virus.exe", PDF)).isFalse();
    }

    @Test
    @DisplayName("a mismatch between two allowed types is still rejected")
    void rejectsMismatchBetweenAllowedTypes() {
        assertThat(MimeUtils.matchesExtension("documento.docx", PDF)).isFalse();
    }

    @Test
    @DisplayName("the comparison ignores the case of the extension")
    void ignoresExtensionCase() {
        assertThat(MimeUtils.matchesExtension("INFORME.PDF", PDF)).isTrue();
    }

    @Test
    @DisplayName("a name with no extension is rejected")
    void rejectsNameWithoutExtension() {
        assertThat(MimeUtils.matchesExtension("informe", PDF)).isFalse();
        assertThat(MimeUtils.matchesExtension("informe.", PDF)).isFalse();
    }

    @Test
    @DisplayName("a double extension is judged by the last one")
    void judgesByTheLastExtension() {
        assertThat(MimeUtils.matchesExtension("informe.pdf.exe", PDF)).isFalse();
        assertThat(MimeUtils.matchesExtension("informe.exe.pdf", PDF)).isTrue();
    }

    @Test
    @DisplayName("a macro-enabled spreadsheet is allowed and its extension agrees")
    void allowsMacroEnabledSpreadsheet() {
        assertThat(MimeUtils.isAllowed(XLSM, ALLOWED)).isTrue();
        assertThat(MimeUtils.matchesExtension("planilla.xlsm", XLSM)).isTrue();
    }

    @Test
    @DisplayName("the macro types of Word and PowerPoint keep their own extension")
    void allowsMacroEnabledWordAndPresentation() {
        assertThat(MimeUtils.matchesExtension("carta.docm", DOCM)).isTrue();
        assertThat(MimeUtils.matchesExtension("charla.pptm", PPTM)).isTrue();
        // Cada tipo sigue atado a su extensión: habilitar macros no vuelve intercambiables
        // los formatos entre sí.
        assertThat(MimeUtils.matchesExtension("planilla.xlsm", DOCM)).isFalse();
        assertThat(MimeUtils.matchesExtension("carta.docm", XLSM)).isFalse();
    }

    @Test
    @DisplayName("a .xlsm declared with the generic Excel type is tolerated")
    void toleratesXlsmDeclaredAsGenericExcel() {
        assertThat(MimeUtils.matchesExtension("planilla.xlsm", XLS)).isTrue();
        assertThat(MimeUtils.matchesExtension("planilla.xls", XLS)).isTrue();
    }

    @Test
    @DisplayName("an executable renamed with a macro content type is rejected")
    void rejectsExecutableDeclaringMacroType() {
        assertThat(MimeUtils.matchesExtension("virus.exe", XLSM)).isFalse();
        assertThat(MimeUtils.matchesExtension("planilla.xlsm.exe", XLSM)).isFalse();
    }

    @Test
    @DisplayName("a download is served with the type its extension deserves")
    void servesTheCanonicalTypeOfTheExtension() {
        // El caso que rompía las macros: subido con el MIME genérico, descargado como .xls antiguo.
        assertThat(MimeUtils.contentTypeForFileName("planilla.xlsm", XLS)).isEqualTo(XLSM);
        assertThat(MimeUtils.contentTypeForFileName("planilla.xlsm", null)).isEqualTo(XLSM);
        assertThat(MimeUtils.contentTypeForFileName("PLANILLA.XLSM", null)).isEqualTo(XLSM);
        assertThat(MimeUtils.contentTypeForFileName("carta.docm", null)).isEqualTo(DOCM);
        assertThat(MimeUtils.contentTypeForFileName("hoja.xlsx", null)).isEqualTo(XLSX);
    }

    @Test
    @DisplayName("an unknown extension keeps the stored type instead of losing it")
    void keepsTheStoredTypeForUnknownExtensions() {
        assertThat(MimeUtils.contentTypeForFileName("plano.dwg", "image/vnd.dwg")).isEqualTo("image/vnd.dwg");
        assertThat(MimeUtils.contentTypeForFileName("sin-extension", PDF)).isEqualTo(PDF);
    }

    @Test
    @DisplayName("a record with no usable type falls back instead of breaking the download")
    void fallsBackWhenNoTypeIsUsable() {
        // parseMediaType lanza excepción con un valor nulo o sin barra: sin este respaldo, un
        // registro viejo o restaurado sin MIME devolvería 500 en vez de descargar.
        assertThat(MimeUtils.contentTypeForFileName("plano.dwg", null)).isEqualTo("application/octet-stream");
        assertThat(MimeUtils.contentTypeForFileName("plano.dwg", "  ")).isEqualTo("application/octet-stream");
        assertThat(MimeUtils.contentTypeForFileName("plano.dwg", "basura")).isEqualTo("application/octet-stream");
        assertThat(MimeUtils.contentTypeForFileName(null, null)).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("the OpenDocument formats the widget offers are actually accepted")
    void acceptsOpenDocumentFormats() {
        // El widget y docker-compose ya los ofrecían, pero sin entrada en el mapa la subida
        // pasaba isAllowed y moría en matchesExtension: un rechazo que nadie podía explicar.
        assertThat(MimeUtils.matchesExtension("texto.odt", ODT)).isTrue();
        assertThat(MimeUtils.matchesExtension("hoja.ods", ODS)).isTrue();
        assertThat(MimeUtils.matchesExtension("charla.odp", ODP)).isTrue();
        // Siguen atados a su propia extensión.
        assertThat(MimeUtils.matchesExtension("hoja.ods", ODT)).isFalse();
        assertThat(MimeUtils.matchesExtension("virus.exe", ODT)).isFalse();
    }

    @Test
    @DisplayName("null arguments are rejected instead of throwing")
    void handlesNulls() {
        assertThat(MimeUtils.matchesExtension(null, PDF)).isFalse();
        assertThat(MimeUtils.matchesExtension("informe.pdf", null)).isFalse();
    }

}
