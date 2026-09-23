package com.officeplatform.service.zip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.officeplatform.exception.InvalidOperationException;

/**
 * Mirar dentro de un comprimido sin bajárselo entero.
 *
 * <p>Lo que se fija acá no es el listado —eso lo hace la biblioteca estándar— sino las dos cosas
 * que convierten a un ZIP ajeno en un arma: una entrada llamada {@code ../../etc/passwd}, que
 * escribe fuera del destino si alguien usa su nombre tal cual, y un archivo de pocos kilobytes que
 * al descomprimirse ocupa gigabytes. Las dos llegan en archivos que un usuario sube sin pensar.
 */
class ZipInspectionServiceTest {

    private ZipInspectionService service;

    @BeforeEach
    void setUp() {
        service = new ZipInspectionService(1024 * 1024);
    }

    /** Arma un ZIP con las entradas dadas, respetando el nombre exacto de cada una. */
    private byte[] zip(Map<String, String> entradas) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entradas.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                if (e.getValue() != null) {
                    zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("the listing shows what is inside, with its folders and sizes")
    void listsWhatIsInside() throws Exception {
        byte[] paquete = zip(new java.util.LinkedHashMap<>(Map.of(
                "informe.docx", "contenido del informe",
                "Informes/2026/acta.docx", "contenido del acta")));

        List<ZipEntryInfo> entradas = service.listEntries(new ByteArrayInputStream(paquete));

        assertThat(entradas).extracting(ZipEntryInfo::getPath)
                .containsExactlyInAnyOrder("informe.docx", "Informes/2026/acta.docx");
        ZipEntryInfo acta = entradas.stream()
                .filter(e -> e.getPath().endsWith("acta.docx")).findFirst().orElseThrow();
        assertThat(acta.getName()).isEqualTo("acta.docx");
        assertThat(acta.getDepth()).isEqualTo(2);
        assertThat(acta.getSize()).isEqualTo("contenido del acta".length());
    }

    @Test
    @DisplayName("an entry that escapes the destination is dropped, not sanitised into something else")
    void dropsEntriesThatEscapeTheDestination() throws Exception {
        // Zip Slip: el nombre lo eligio quien armo el paquete. Usarlo tal cual para escribir en
        // disco es como se sobreescribe un archivo del sistema desde una subida inocente.
        byte[] paquete = zip(new java.util.LinkedHashMap<>(Map.of(
                "../../etc/passwd", "root:x:0:0",
                "..\\\\..\\\\windows\\\\system32\\\\config", "nope",
                "/absoluto/archivo.txt", "tampoco",
                "legitimo.txt", "este si")));

        List<ZipEntryInfo> entradas = service.listEntries(new ByteArrayInputStream(paquete));

        assertThat(entradas).extracting(ZipEntryInfo::getPath).containsExactly("legitimo.txt");
    }

    @Test
    @DisplayName("windows separators become normal folders instead of one long name")
    void normalisesWindowsSeparators() {
        assertThat(ZipInspectionService.sanitize("Informes\\2026\\acta.docx"))
                .isEqualTo("Informes/2026/acta.docx");
        // Tramos vacios y "." no aportan nivel y no pueden convertirse en carpetas sin nombre.
        assertThat(ZipInspectionService.sanitize("Informes//./2026/acta.docx"))
                .isEqualTo("Informes/2026/acta.docx");
        assertThat(ZipInspectionService.sanitize("C:/Windows/system.ini")).isNull();
        assertThat(ZipInspectionService.sanitize("   ")).isNull();
    }

    @Test
    @DisplayName("one entry can be pulled out without touching the rest")
    void extractsASingleEntry() throws Exception {
        byte[] paquete = zip(new java.util.LinkedHashMap<>(Map.of(
                "uno.txt", "contenido uno",
                "Informes/dos.txt", "contenido dos")));

        byte[] contenido = service.extractEntry(new ByteArrayInputStream(paquete), "Informes/dos.txt");

        assertThat(new String(contenido, StandardCharsets.UTF_8)).isEqualTo("contenido dos");
    }

    @Test
    @DisplayName("asking for an entry that is not there says so instead of returning nothing")
    void failsClearlyWhenTheEntryIsMissing() throws Exception {
        byte[] paquete = zip(Map.of("uno.txt", "contenido"));

        assertThatThrownBy(() -> service.extractEntry(new ByteArrayInputStream(paquete), "no-existe.txt"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("no-existe.txt");
    }

    @Test
    @DisplayName("an entry bigger than the ceiling is refused instead of filling the heap")
    void refusesAnEntryOverTheCeiling() throws Exception {
        // Una bomba zip declara poco y expande mucho: el tope se controla sobre lo que sale, no
        // sobre lo que la cabecera promete.
        ZipInspectionService conTopeChico = new ZipInspectionService(64);
        byte[] paquete = zip(Map.of("grande.txt", "x".repeat(5000)));

        assertThatThrownBy(() -> conTopeChico.extractEntry(new ByteArrayInputStream(paquete), "grande.txt"))
                .isInstanceOf(com.officeplatform.exception.StorageException.class)
                .hasMessageContaining("tamaño máximo");
    }

    @Test
    @DisplayName("something that is not a zip is reported as such")
    void reportsAFileThatIsNotAZip() {
        byte[] basura = "esto no es un zip".getBytes(StandardCharsets.UTF_8);

        // La biblioteca no falla al abrirlo: simplemente no encuentra entradas. Devolver una lista
        // vacia es la respuesta honesta, y el controlador ya rechaza lo que no termina en .zip.
        assertThat(service.listEntries(new ByteArrayInputStream(basura))).isEmpty();
    }

    @Test
    @DisplayName("walking the package hands over every file with a safe path")
    void walksEveryFileWithASafePath() throws Exception {
        byte[] paquete = zip(new java.util.LinkedHashMap<>(Map.of(
                "Informes/acta.docx", "acta",
                "../fuera.txt", "peligroso",
                "raiz.txt", "raiz")));

        java.util.List<String> vistos = new java.util.ArrayList<>();
        service.forEachFile(new ByteArrayInputStream(paquete), (ruta, contenido) -> vistos.add(ruta));

        assertThat(vistos).containsExactlyInAnyOrder("Informes/acta.docx", "raiz.txt");
    }
}
