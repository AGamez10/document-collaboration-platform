-- V6: rellena la extension de los archivos que la tengan vacia.
--
-- La extension gobierna con que motor abre OnlyOffice un documento: sin ella, resolveDocumentType
-- devuelve "word" y un libro de Excel se le entrega al conversor equivocado.
--
-- Sobre los datos actuales esta migracion afecta CERO filas: el unico archivo sin extension se
-- llama "Actividad1" y no tiene punto, asi que la condicion lo excluye. Se aplica igual porque es
-- barata y porque el dia que alguien inserte una fila sin extension --una restauracion desde un
-- respaldo viejo, una carga directa-- el arreglo ya esta puesto en lugar de descubrirse cuando un
-- documento no abra.

UPDATE files
SET extension = LOWER(SUBSTRING(original_file_name FROM '\.([^\.]+)$'))
WHERE (extension IS NULL OR extension = '')
  AND original_file_name LIKE '%.%';
