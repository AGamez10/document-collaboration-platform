-- V2: papelera de carpetas y permisos de re-compartir.
--
-- Las cuatro columnas son ANULABLES a proposito. Se agregan sobre tablas que ya tienen filas en
-- produccion, y una columna NOT NULL sin valor por defecto falla en seco sobre datos existentes.
-- Ademas, null es exactamente el significado correcto en los cuatro casos: una carpeta sin
-- deleted_at no esta eliminada, y una concesion sin can_share no autoriza a re-compartir, que es
-- la lectura segura para un permiso.
--
-- IF NOT EXISTS en todas: una base donde ddl-auto=update ya haya agregado estas columnas antes de
-- adoptar Flyway debe poder aplicar esta migracion sin fallar.

-- ── Papelera de carpetas ─────────────────────────────────────────────────────
-- Antes las carpetas se borraban fisicamente y sus archivos perdian el folder_id para no quedar
-- apuntando a una fila inexistente. El costo era que restaurar un archivo lo dejaba en la raiz:
-- su ubicacion original ya no existia en ninguna parte.

ALTER TABLE folders ADD COLUMN IF NOT EXISTS deleted_at timestamp(6) without time zone;
ALTER TABLE folders ADD COLUMN IF NOT EXISTS deleted_by_user_id character varying(255);

CREATE INDEX IF NOT EXISTS idx_folder_deleted_at ON folders (deleted_at);

-- ── Re-compartir y papelera por persona ──────────────────────────────────────
-- can_share es un flag y no un cuarto valor de permission_level: Hibernate emite un CHECK con los
-- valores del enum al crear la tabla y ddl-auto=update nunca lo altera, asi que un valor nuevo
-- compilaria, pasaria los tests contra H2 --que construye el esquema desde cero-- y fallaria en
-- produccion contra share_permissions_permission_level_check.
--
-- deleted_at marca que el destinatario saco el recurso de su vista. Vive en el vinculo y no en el
-- archivo porque files.deleted_at es una unica marca global: un destinatario que eliminaba algo
-- compartido lo hacia desaparecer para todos, su autor incluido.

ALTER TABLE share_permissions ADD COLUMN IF NOT EXISTS can_share boolean;
ALTER TABLE share_permissions ADD COLUMN IF NOT EXISTS deleted_at timestamp(6) without time zone;

CREATE INDEX IF NOT EXISTS idx_share_deleted_at ON share_permissions (deleted_at);
