package com.officeplatform.service.version;

import java.io.InputStream;
import java.util.List;

import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FileVersionEntity;

/**
 * Historial de versiones de un archivo.
 *
 * <p>El contenido vigente sigue viviendo en {@code files.object_name}. Cada versión conserva una
 * copia completa del estado anterior en el almacenamiento, así que la descarga, el editor y el
 * respaldo no cambian en absoluto: el historial es una capa aparte que solo se consulta cuando
 * alguien la pide.
 */
public interface FileVersionService {

    /**
     * Archiva el contenido actual de un archivo antes de que lo sobrescriban.
     *
     * <p>Se invoca desde el guardado del editor, y no puede hacerlo fallar: perder una versión es
     * lamentable, perder el guardado que la persona acaba de hacer es inaceptable. Por eso devuelve
     * null ante cualquier problema en vez de propagarlo.
     *
     * @return la versión archivada, o null si no se pudo archivar
     */
    FileVersionEntity archiveCurrent(FileEntity file, String userId, String userName, String comment);

    /** Historial de un archivo, de la versión más reciente a la más antigua. */
    List<FileVersionEntity> listVersions(Long fileId);

    /**
     * Devuelve el archivo al contenido de una versión anterior.
     *
     * <p>Antes de sobrescribir archiva el estado vigente, así que restaurar nunca destruye: una
     * restauración equivocada se deshace restaurando la versión que la propia restauración creó.
     */
    FileEntity restoreVersion(Long fileId, Integer versionNumber, Long apiKeyId,
                              String userId, String userName);

    /** Contenido de una versión concreta, para descargarla sin alterar el archivo vigente. */
    InputStream downloadVersion(Long fileId, Integer versionNumber);

    /** Borra el historial y sus binarios. Se llama al purgar el archivo definitivamente. */
    void purgeVersions(Long fileId);

}
