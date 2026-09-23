package com.officeplatform.service.zip;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una entrada dentro de un archivo comprimido, tal como la ve el explorador del gestor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZipEntryInfo {

    /** Nombre visible, sin la ruta: lo que se muestra en la lista. */
    private String name;

    /** Ruta completa dentro del ZIP, saneada. Es la clave para pedir la descarga individual. */
    private String path;

    private boolean directory;

    private long size;

    private long compressedSize;

    private LocalDateTime modifiedAt;

    /** Cuántos niveles de carpeta cuelga: permite dibujar el árbol sin recalcularlo. */
    private int depth;

}
