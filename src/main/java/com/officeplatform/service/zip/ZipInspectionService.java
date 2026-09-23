package com.officeplatform.service.zip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.officeplatform.exception.InvalidOperationException;
import com.officeplatform.exception.StorageException;

import lombok.extern.slf4j.Slf4j;

/**
 * Mira dentro de un archivo comprimido sin descomprimirlo en disco.
 *
 * <p>En el DataServer de Windows, entrar a un .zip es hacer doble clic. Acá el archivo vive en el
 * almacenamiento de objetos, así que sin esto la única forma de ver qué hay adentro es bajarse los
 * doscientos megabytes enteros para abrir uno solo. El recorrido se hace en streaming sobre el
 * objeto: se lee de corrido, se anota qué entradas tiene, y nada toca el disco del servidor.
 *
 * <p>Dos defensas que no son opcionales:
 *
 * <ul>
 *   <li><b>Zip Slip.</b> La ruta de cada entrada la eligió quien armó el ZIP, no el servidor. Una
 *       entrada llamada {@code ../../etc/passwd} escribe fuera del destino si alguien la usa tal
 *       cual para armar una ruta. Acá se sanea siempre y la entrada peligrosa se descarta.
 *   <li><b>Zip bomb.</b> Un archivo de pocos kilobytes puede declarar millones de entradas o
 *       descomprimirse en gigabytes. Los dos topes existen para que inspeccionar un archivo ajeno
 *       no pueda tumbar el servidor.
 * </ul>
 */
@Service
@Slf4j
public class ZipInspectionService {

    /** Entradas que se listan como máximo. Más que esto no es un documento, es un ataque. */
    private static final int MAX_ENTRIES = 5_000;

    /** Tope de lo que se deja extraer de una sola entrada hacia memoria. */
    private final long maxEntryBytes;

    public ZipInspectionService(
            @Value("${office-platform.zip.max-entry-bytes:524288000}") long maxEntryBytes) {
        this.maxEntryBytes = maxEntryBytes;
    }

    /**
     * Lista lo que hay adentro del comprimido.
     *
     * @param content el binario del ZIP, ya resuelto por quien tiene permiso de leerlo
     */
    public List<ZipEntryInfo> listEntries(InputStream content) {
        List<ZipEntryInfo> entradas = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.BufferedInputStream(content, 64 * 1024))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null && entradas.size() < MAX_ENTRIES) {
                String ruta = sanitize(entry.getName());
                if (ruta == null) {
                    log.warn("Entrada descartada por ruta peligrosa dentro del ZIP: '{}'", entry.getName());
                    zis.closeEntry();
                    continue;
                }
                entradas.add(toInfo(entry, ruta, sizeOf(entry, zis)));
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new InvalidOperationException(
                    "El archivo no es un ZIP válido o está dañado: " + e.getMessage());
        }
        return entradas;
    }

    /**
     * Saca una sola entrada del comprimido, sin tocar el resto.
     *
     * <p>Es la diferencia entre bajar un archivo y bajar el paquete entero para abrir uno.
     *
     * @return el contenido de esa entrada
     */
    public byte[] extractEntry(InputStream content, String entryPath) {
        String buscada = sanitize(entryPath);
        if (buscada == null || buscada.isBlank()) {
            throw new InvalidOperationException("La ruta pedida dentro del comprimido no es válida.");
        }
        try (ZipInputStream zis = new ZipInputStream(new java.io.BufferedInputStream(content, 64 * 1024))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String ruta = sanitize(entry.getName());
                if (ruta != null && ruta.equals(buscada) && !entry.isDirectory()) {
                    return readLimited(zis, entry.getName());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new InvalidOperationException(
                    "No se pudo leer el comprimido: " + e.getMessage());
        }
        throw new InvalidOperationException(
                "El comprimido no contiene '" + entryPath + "'.");
    }

    /**
     * Recorre el comprimido entregando cada archivo con su ruta saneada.
     *
     * <p>Existe para la extracción masiva: recibir cada entrada de a una permite crear la carpeta y
     * subir el binario sin que el ZIP entero pase por memoria.
     */
    public void forEachFile(InputStream content, EntryConsumer consumer) {
        try (ZipInputStream zis = new ZipInputStream(new java.io.BufferedInputStream(content, 64 * 1024))) {
            ZipEntry entry;
            int vistas = 0;
            while ((entry = zis.getNextEntry()) != null && vistas < MAX_ENTRIES) {
                vistas++;
                String ruta = sanitize(entry.getName());
                if (ruta == null) {
                    log.warn("Entrada descartada por ruta peligrosa al extraer: '{}'", entry.getName());
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                consumer.accept(ruta, readLimited(zis, entry.getName()));
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new InvalidOperationException("No se pudo recorrer el comprimido: " + e.getMessage());
        }
    }

    /** Recibe cada archivo del comprimido con su ruta ya segura. */
    @FunctionalInterface
    public interface EntryConsumer {
        void accept(String path, byte[] content);
    }

    /**
     * Tamano real de una entrada, aunque la cabecera no lo diga.
     *
     * <p>Un ZIP escrito en streaming —el que genera cualquier programa que no puede volver atras a
     * corregir la cabecera— deja el tamano en -1 y lo anota recien despues de los datos. Mostrar
     * "0 bytes" en el explorador seria mentir sobre todos esos archivos, asi que cuando falta se
     * mide descomprimiendo la entrada sin guardarla.
     */
    private long sizeOf(ZipEntry entry, ZipInputStream zis) throws IOException {
        if (entry.getSize() >= 0) {
            return entry.getSize();
        }
        if (entry.isDirectory()) {
            return 0;
        }
        byte[] bloque = new byte[64 * 1024];
        long total = 0;
        int leidos;
        while ((leidos = zis.read(bloque)) != -1) {
            total += leidos;
            if (total > maxEntryBytes) {
                // No se sigue contando: listar no puede volverse el vector de la bomba que el
                // tope de extraccion ya bloquea.
                return total;
            }
        }
        return total;
    }

    private ZipEntryInfo toInfo(ZipEntry entry, String ruta, long size) {
        String nombre = ruta;
        int barra = ruta.lastIndexOf('/');
        if (barra >= 0) {
            nombre = ruta.substring(barra + 1);
        }
        LocalDateTime modificado = null;
        if (entry.getLastModifiedTime() != null) {
            modificado = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(entry.getLastModifiedTime().toMillis()), ZoneId.systemDefault());
        }
        return ZipEntryInfo.builder()
                .name(nombre)
                .path(ruta)
                .directory(entry.isDirectory())
                .size(Math.max(size, 0))
                .compressedSize(Math.max(entry.getCompressedSize(), 0))
                .modifiedAt(modificado)
                .depth((int) ruta.chars().filter(c -> c == '/').count() - (entry.isDirectory() ? 1 : 0))
                .build();
    }

    /**
     * Devuelve una ruta segura, o null si la entrada intenta salirse.
     *
     * <p>Se normalizan las barras invertidas de Windows, se descartan los tramos vacíos y los
     * {@code .}, y cualquier {@code ..} o ruta absoluta invalida la entrada completa: no hay forma
     * legítima de que un archivo dentro de un comprimido necesite apuntar fuera de él.
     */
    static String sanitize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String normalizada = rawName.replace('\\', '/').trim();
        // Ruta absoluta o unidad de Windows: fuera del destino por definición.
        if (normalizada.startsWith("/") || normalizada.matches("^[A-Za-z]:/.*")) {
            return null;
        }
        boolean carpeta = normalizada.endsWith("/");
        List<String> tramos = new ArrayList<>();
        for (String tramo : normalizada.split("/")) {
            String limpio = tramo.trim();
            if (limpio.isEmpty() || ".".equals(limpio)) {
                continue;
            }
            if ("..".equals(limpio)) {
                return null;
            }
            tramos.add(limpio);
        }
        if (tramos.isEmpty()) {
            return null;
        }
        String resultado = String.join("/", tramos);
        return carpeta ? resultado + "/" : resultado;
    }

    /** Nombre de archivo apto para el gestor, tomado del último tramo de la ruta. */
    public static String fileNameOf(String path) {
        String limpio = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int barra = limpio.lastIndexOf('/');
        return barra >= 0 ? limpio.substring(barra + 1) : limpio;
    }

    /** Extensión en minúsculas, o cadena vacía. */
    static String extensionOf(String name) {
        int punto = name.lastIndexOf('.');
        return (punto > 0 && punto < name.length() - 1)
                ? name.substring(punto + 1).toLowerCase(Locale.ROOT) : "";
    }

    private byte[] readLimited(ZipInputStream zis, String nombreOriginal) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bloque = new byte[64 * 1024];
        long total = 0;
        int leidos;
        while ((leidos = zis.read(bloque)) != -1) {
            total += leidos;
            if (total > maxEntryBytes) {
                // El tamaño declarado en la cabecera puede mentir; el que cuenta es el que sale.
                throw new StorageException("La entrada '" + nombreOriginal
                        + "' supera el tamaño máximo permitido para extraer.");
            }
            out.write(bloque, 0, leidos);
        }
        return out.toByteArray();
    }

}
