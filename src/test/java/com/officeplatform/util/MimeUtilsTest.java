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

    private static final List<String> ALLOWED = List.of(PDF, XLSX);

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
    @DisplayName("null arguments are rejected instead of throwing")
    void handlesNulls() {
        assertThat(MimeUtils.matchesExtension(null, PDF)).isFalse();
        assertThat(MimeUtils.matchesExtension("informe.pdf", null)).isFalse();
    }

}
