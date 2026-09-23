-- V10: dos acciones nuevas en la bitacora, con su restriccion actualizada.
--
-- Hibernate creo activity_log con un CHECK que enumera los valores del enum que existian ese dia,
-- y ddl-auto nunca lo altera despues. Agregar un valor al enum de Java compila, pasa los tests
-- contra H2 —que no tiene esa restriccion— y revienta en produccion la primera vez que alguien
-- ejecuta la accion nueva. Este proyecto ya perdio dos veces por eso; la migracion es la mitad que
-- faltaba.
--
-- ADMIN_PASSWORD_CHANGE: el cambio de contrasena del administrador tiene que quedar en la
-- trazabilidad. Una auditoria ISO pregunta quien cambio las credenciales y cuando, y "no se
-- registra" no es una respuesta aceptable.
--
-- BULK_IMPORT: la migracion masiva desde una carpeta de red entra miles de archivos de una vez.
-- Sin una fila que resuma la operacion, en la bitacora aparecen mil altas sueltas sin nada que
-- explique de donde salieron.

ALTER TABLE activity_log DROP CONSTRAINT IF EXISTS activity_log_action_check;

ALTER TABLE activity_log ADD CONSTRAINT activity_log_action_check
    CHECK (action IN (
        'UPLOAD', 'DOWNLOAD', 'DELETE', 'RESTORE', 'PURGE', 'RENAME',
        'EDITOR_OPEN', 'EDITOR_SAVE', 'CREATE_BLANK',
        'CREATE_FOLDER', 'RENAME_FOLDER', 'DELETE_FOLDER',
        'MOVE_FILE', 'MOVE_FOLDER', 'DOWNLOAD_FOLDER',
        'SHARE', 'UNSHARE', 'REVOKE_SHARE', 'SESSION_TIMEOUT',
        'ADMIN_PASSWORD_CHANGE', 'BULK_IMPORT'));
