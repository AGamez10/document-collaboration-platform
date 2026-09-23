package com.officeplatform.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.officeplatform.dto.response.ActivityLogResponse;

/**
 * La trazabilidad exportada para una auditoría.
 *
 * <p>Una certificación no revisa la aplicación: pide la evidencia en un archivo y la abre en Excel.
 * Un CSV correcto en teoría que se abre mal en la máquina del auditor no sirve de nada, y los dos
 * motivos por los que se abre mal son invisibles en el código: la coma como separador, que Excel en
 * español lee como decimal, y la falta de BOM, que le rompe cada tilde.
 */
class ActivityLogCsvExporterTest {

    private final ActivityLogCsvExporter exporter = new ActivityLogCsvExporter();

    private ActivityLogResponse registro(String usuario, String detalle) {
        return ActivityLogResponse.builder()
                .id(1L)
                .apiKeyId(3L)
                .apiKeyName("Proyecto X")
                .userId("1004356866")
                .userName(usuario)
                .action("UPLOAD")
                .fileId(10L)
                .fileName("informe.docx")
                .folderId(2L)
                .details(detalle)
                .ipAddress("192.168.1.50")
                .timestamp(LocalDateTime.of(2026, 9, 23, 10, 30, 0))
                .build();
    }

    private String textoDe(byte[] csv) {
        return new String(csv, ActivityLogCsvExporter.UTF8_BOM.length,
                csv.length - ActivityLogCsvExporter.UTF8_BOM.length, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the file starts with the BOM Excel needs to show accents")
    void startsWithTheUtf8Bom() {
        byte[] csv = exporter.toCsv(List.of(registro("Ana Pérez", "Subió el informe")));

        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);
        // Sin BOM, Excel muestra "Ana PÃ©rez" en todas las filas de un registro que existe para
        // ser leído.
        assertThat(textoDe(csv)).contains("Ana Pérez");
    }

    @Test
    @DisplayName("columns are separated by semicolons, which is what Excel in Spanish expects")
    void separatesWithSemicolons() {
        String texto = textoDe(exporter.toCsv(List.of(registro("Ana", "Subió el informe"))));

        String[] lineas = texto.split("\r\n");
        assertThat(lineas[0]).startsWith("Fecha y hora;Proyecto;Cédula");
        assertThat(lineas[1]).contains("2026-09-23 10:30:00;Proyecto X;1004356866;Ana;UPLOAD");
    }

    @Test
    @DisplayName("a detail with a semicolon does not shift every column after it")
    void quotesValuesThatWouldBreakTheRow() {
        // El detalle es texto libre que escribe el sistema. Una sola fila sin escapar corre las
        // columnas siguientes y el archivo deja de cuadrar justo donde el auditor mira.
        String texto = textoDe(exporter.toCsv(List.of(
                registro("Ana", "Movido de carpeta 3; ahora en 7"))));

        assertThat(texto).contains("\"Movido de carpeta 3; ahora en 7\"");
        assertThat(texto.split("\r\n")[1].split(";")).hasSizeGreaterThan(5);
    }

    @Test
    @DisplayName("a quote inside a value is doubled, as the format demands")
    void escapesEmbeddedQuotes() {
        String texto = textoDe(exporter.toCsv(List.of(registro("Ana", "Renombrado a \"final\""))));

        assertThat(texto).contains("\"Renombrado a \"\"final\"\"\"");
    }

    @Test
    @DisplayName("a line break inside a detail stays inside its cell")
    void keepsLineBreaksInsideTheCell() {
        String texto = textoDe(exporter.toCsv(List.of(registro("Ana", "Primera línea\nSegunda"))));

        assertThat(texto).contains("\"Primera línea\nSegunda\"");
    }

    @Test
    @DisplayName("an empty period exports the header instead of an empty file")
    void exportsTheHeaderForAnEmptyPeriod() {
        String texto = textoDe(exporter.toCsv(List.of()));

        // Un archivo vacío se confunde con un fallo de la exportación; con el encabezado, el
        // auditor ve que la consulta corrió y que ese período no tuvo actividad.
        assertThat(texto.trim()).startsWith("Fecha y hora;");
    }

    @Test
    @DisplayName("null fields become empty cells instead of the word null")
    void writesEmptyCellsForMissingData() {
        ActivityLogResponse sinDatos = ActivityLogResponse.builder()
                .action("DELETE")
                .timestamp(LocalDateTime.of(2026, 9, 23, 8, 0, 0))
                .build();

        String texto = textoDe(exporter.toCsv(List.of(sinDatos)));

        assertThat(texto).doesNotContain("null");
    }
}
