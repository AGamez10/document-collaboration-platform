-- V4: cuota de almacenamiento por proyecto.
--
-- Columna ANULABLE y sin valor por defecto a proposito: null significa "sin limite", que es
-- exactamente el comportamiento que tienen hoy los proyectos existentes. Una cuota con default
-- numerico habria impuesto un tope retroactivo a proyectos que hoy trabajan sin el, y el primer
-- sintoma habria sido gente sin poder subir un archivo sin entender por que.
--
-- Se guarda en bytes y no en gigabytes: el endpoint de administracion recibe GB por comodidad,
-- pero almacenar la unidad derivada obligaria a redondear en cada comparacion.

ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS storage_quota_bytes bigint;
