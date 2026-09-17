-- V7: papelera de dos niveles.
--
-- Hasta ahora "Eliminar permanentemente" desde el gestor ejecutaba un borrado fisico: se iba la
-- fila de PostgreSQL y el binario de MinIO en el mismo acto. El archivo desaparecia tambien de la
-- papelera del panel de administracion, asi que un vaciado apurado dejaba al Super Admin sin nada
-- que auditar ni que recuperar: el error mas caro del sistema era el mas facil de cometer.
--
-- Con esta columna el vaciado del usuario pasa a ser una segunda ocultacion. El archivo sale de su
-- papelera, pero la fila y el binario siguen existiendo y solo el panel de administracion los ve.
-- El borrado fisico queda como potestad exclusiva del administrador.
--
-- Columna ANULABLE y sin default: null significa "el usuario no lo vacio", que es el estado de
-- todas las filas existentes. Un default habria marcado como vaciado a todo el historico.

ALTER TABLE files ADD COLUMN IF NOT EXISTS user_purged_at timestamp;

-- La papelera del usuario filtra por esta columna en cada consulta, y la del administrador la lee
-- para distinguir un archivo vaciado de uno simplemente borrado.
CREATE INDEX IF NOT EXISTS idx_files_user_purged_at ON files (user_purged_at);
