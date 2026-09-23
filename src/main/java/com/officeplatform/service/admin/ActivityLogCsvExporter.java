package com.officeplatform.service.admin;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.officeplatform.dto.response.ActivityLogResponse;

/**
 * Convierte la trazabilidad en un archivo que una auditoría pueda abrir.
 *
 * <p>Una certificación ISO no revisa la aplicación: pide la evidencia en un archivo y la lee en
 * Excel. Dos decisiones que parecen detalles y deciden si el archivo sirve:
 *
 * <ul>
 *   <li><b>Punto y coma como separador.</b> Excel en configuración regional española interpreta la
 *       coma como separador decimal, no de columnas: un CSV con comas abre con todo apilado en la
 *       primera columna y el auditor concluye que el sistema no exporta bien.
 *   <li><b>BOM al inicio.</b> Sin él, Excel lee el archivo en la codificación del sistema y cada
 *       tilde aparece como un símbolo roto. "Modificación" se lee "ModificaciÃ³n" en todas las
 *       filas de un registro que existe justamente para ser legible.
 * </ul>
 */
@Component
public class ActivityLogCsvExporter {

    /** Marca de orden de bytes de UTF-8: tres bytes que le dicen a Excel cómo leer el archivo. */
    static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    private static final char SEPARADOR = ';';

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] CABECERAS = {
            "Fecha y hora", "Proyecto", "Cédula", "Usuario", "Acción",
            "Archivo", "Id archivo", "Id carpeta", "Detalle", "Dirección IP"
    };

    public byte[] toCsv(List<ActivityLogResponse> registros) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(String.valueOf(SEPARADOR), CABECERAS)).append("\r\n");

        for (ActivityLogResponse r : registros) {
            append(csv, r.getTimestamp() == null ? "" : r.getTimestamp().format(FECHA));
            append(csv, r.getApiKeyName());
            append(csv, r.getUserId());
            append(csv, r.getUserName());
            append(csv, r.getAction() == null ? "" : r.getAction().toString());
            append(csv, r.getFileName());
            append(csv, r.getFileId() == null ? "" : r.getFileId().toString());
            append(csv, r.getFolderId() == null ? "" : r.getFolderId().toString());
            append(csv, r.getDetails());
            appendLast(csv, r.getIpAddress());
        }

        byte[] texto = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] conBom = new byte[UTF8_BOM.length + texto.length];
        System.arraycopy(UTF8_BOM, 0, conBom, 0, UTF8_BOM.length);
        System.arraycopy(texto, 0, conBom, UTF8_BOM.length, texto.length);
        return conBom;
    }

    private void append(StringBuilder csv, String valor) {
        csv.append(escape(valor)).append(SEPARADOR);
    }

    private void appendLast(StringBuilder csv, String valor) {
        csv.append(escape(valor)).append("\r\n");
    }

    /**
     * Encierra el valor entre comillas cuando podría romper la fila.
     *
     * <p>El detalle de un registro es texto libre escrito por el sistema y puede traer punto y
     * coma, comillas o un salto de línea. Sin escapar, una sola de esas filas corre todas las
     * columnas siguientes y el archivo deja de cuadrar justo donde el auditor mira.
     */
    static String escape(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        boolean necesitaComillas = valor.indexOf(SEPARADOR) >= 0
                || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0
                || valor.indexOf('\r') >= 0;
        if (!necesitaComillas) {
            return valor;
        }
        return '"' + valor.replace("\"", "\"\"") + '"';
    }

}
