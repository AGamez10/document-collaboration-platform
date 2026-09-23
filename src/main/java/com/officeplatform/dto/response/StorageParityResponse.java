package com.officeplatform.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de comparar la base de datos contra el almacenamiento de objetos.
 *
 * <p>Las dos mitades del sistema pueden desincronizarse sin que nada falle a la vista: una fila
 * cuyo binario ya no está se lista normalmente en el gestor y recién falla cuando alguien intenta
 * descargarla, que suele ser el peor momento. Un objeto sin fila es lo contrario — ocupa disco y
 * nadie lo va a borrar nunca, porque nadie sabe que existe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageParityResponse {

    /** Filas vivas en {@code files}. */
    private long databaseFiles;

    /** Objetos en el bucket, sin contar las versiones históricas. */
    private long minioObjects;

    /** Filas cuyo binario no existe: se ven en el gestor y fallan al descargar. */
    private long brokenLinks;

    /** Objetos sin fila que los referencie: espacio muerto. */
    private long orphanedObjects;

    private boolean healthy;

    /** Nombres de los primeros archivos rotos, para poder ir a buscarlos. */
    private List<String> brokenFileNames;

    /** Claves de los primeros objetos huérfanos. */
    private List<String> orphanedObjectNames;

    private String message;

}
