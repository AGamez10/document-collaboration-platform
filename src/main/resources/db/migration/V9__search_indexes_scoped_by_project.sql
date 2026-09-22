-- V9: los indices de busqueda, esta vez con el proyecto adentro.
--
-- La V8 movio los indices trigram a lower(), que era necesario pero no suficiente. Medido con
-- EXPLAIN ANALYZE sobre 20.000 archivos sinteticos:
--
--   * Solo el contenido, sin mas filtros -> Bitmap Index Scan, 2,3 ms. El indice funciona.
--   * La consulta real de la aplicacion  -> el planificador descarta el trigram, entra por el
--     indice de api_key_id y aplica el LIKE como filtro fila por fila: 19.994 filas descartadas
--     a mano, 954 bloques leidos, 414 ms.
--
-- El motivo es que las tres consultas de busqueda filtran por proyecto, y PostgreSQL no puede
-- combinar en un solo recorrido un indice GIN de trigramas con un indice btree de igualdad: elige
-- uno y filtra el resto. Con todos los archivos de un proyecto grande, ese "resto" es el proyecto
-- entero, y cada busqueda vuelve a leer megabytes de texto extraido.
--
-- La solucion es meter api_key_id dentro del mismo indice GIN, que es lo que habilita btree_gin.
-- Con eso el plan pasa a ser un BitmapOr de dos Bitmap Index Scan, uno por nombre y otro por
-- contenido, ya restringidos al proyecto: 20 bloques y 2,0 ms sobre el mismo catalogo. Doscientas
-- veces mas rapido, y la diferencia crece con el tamano del proyecto.
--
-- Los indices de una sola expresion que creo la V8 quedan sin uso: ninguna consulta busca sin
-- decir en que proyecto. Se los deja caer para no pagar su escritura en cada subida y cada
-- guardado del editor.
--
-- Si btree_gin no estuviera disponible, los de la V8 se conservan: acelerar a medias es mejor que
-- no acelerar, y la migracion no puede frenar el arranque por un acelerador.

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    CREATE EXTENSION IF NOT EXISTS btree_gin;

    CREATE INDEX IF NOT EXISTS idx_files_proj_content_trgm
        ON files USING gin (api_key_id, lower(search_content) gin_trgm_ops);

    CREATE INDEX IF NOT EXISTS idx_files_proj_name_trgm
        ON files USING gin (api_key_id, lower(original_file_name) gin_trgm_ops);

    DROP INDEX IF EXISTS idx_files_search_content_lower;
    DROP INDEX IF EXISTS idx_files_original_name_lower;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'btree_gin no disponible: se conservan los indices de la V8, que aceleran la busqueda sin el filtro de proyecto.';
END $$;
