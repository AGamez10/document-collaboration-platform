-- V11: las operaciones del panel de administracion entran a la bitacora.
--
-- Una auditoria ISO pregunta quien creo una API key, quien cambio una cuota y quien borro un
-- respaldo. Hasta ahora esas operaciones no dejaban rastro: el panel las ejecutaba y el registro
-- de actividad solo veia lo que hacian los usuarios.
--
-- Como en la V10: agregar valores al enum de Java sin tocar el CHECK compila, pasa los tests
-- contra H2 y revienta en produccion la primera vez que alguien ejecuta la accion nueva.

ALTER TABLE activity_log DROP CONSTRAINT IF EXISTS activity_log_action_check;

ALTER TABLE activity_log ADD CONSTRAINT activity_log_action_check
    CHECK (action IN (
        'UPLOAD', 'DOWNLOAD', 'DELETE', 'RESTORE', 'PURGE', 'RENAME',
        'EDITOR_OPEN', 'EDITOR_SAVE', 'CREATE_BLANK',
        'CREATE_FOLDER', 'RENAME_FOLDER', 'DELETE_FOLDER',
        'MOVE_FILE', 'MOVE_FOLDER', 'DOWNLOAD_FOLDER',
        'SHARE', 'UNSHARE', 'REVOKE_SHARE', 'SESSION_TIMEOUT',
        'ADMIN_PASSWORD_CHANGE', 'BULK_IMPORT',
        'ADMIN_USER_DELETE', 'ADMIN_USER_ROLE', 'ADMIN_API_KEY',
        'ADMIN_QUOTA', 'ADMIN_BACKUP'));
