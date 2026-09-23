package com.officeplatform.entity;

public enum ActivityAction {
    UPLOAD,
    DOWNLOAD,
    DELETE,
    RESTORE,
    PURGE,
    RENAME,
    EDITOR_OPEN,
    EDITOR_SAVE,
    CREATE_BLANK,
    CREATE_FOLDER,
    RENAME_FOLDER,
    DELETE_FOLDER,
    MOVE_FILE,
    MOVE_FOLDER,
    DOWNLOAD_FOLDER,
    SHARE,
    UNSHARE,
    REVOKE_SHARE,

    /** Session closed by the reaper because the browser stopped reporting activity. */
    SESSION_TIMEOUT,

    /**
     * El administrador cambio su propia contrasena.
     *
     * <p>Toda accion nueva de este enum necesita una migracion que actualice el CHECK de
     * activity_log: Hibernate lo creo con la lista de valores de ese momento y no lo altera nunca.
     * Esta y la siguiente entraron con la V10.
     */
    ADMIN_PASSWORD_CHANGE,

    /** Importacion masiva desde una carpeta del servidor. */
    BULK_IMPORT
}
