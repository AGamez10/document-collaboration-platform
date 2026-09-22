-- V8: los indices de busqueda, sobre la expresion que la consulta usa de verdad.
--
-- La V5 creo un indice trigram sobre search_content crudo, pero la busqueda compara
-- lower(search_content) contra lower(patron) para ser insensible a mayusculas. PostgreSQL no
-- puede usar un indice sobre una columna cuando la consulta la envuelve en una funcion: son
-- expresiones distintas. Verificado con EXPLAIN contra la base real, el plan era
-- "Seq Scan on files" — el indice existia, ocupaba disco, se mantenia en cada escritura y no
-- aceleraba una sola busqueda.
--
-- No se notaba porque el catalogo era chico. A escala de DataServer, con decenas de miles de
-- archivos y hasta 20.000 caracteres de texto extraido en cada uno, cada busqueda leeria cientos
-- de megabytes.
--
-- Se indexan las dos columnas que la consulta toca. Indexar solo el contenido no alcanza: el
-- filtro es un OR entre nombre y contenido, y con una sola rama indexada PostgreSQL tiene que
-- recorrer la tabla entera igual para resolver la otra.
--
-- Nota sobre trigramas: un patron de menos de tres caracteres no forma un trigrama completo y
-- cae en escaneo secuencial por diseño. Es correcto — buscar "ab" no es una busqueda, es un
-- filtro, y sobre pocos resultados el escaneo es mas barato que el indice.
--
-- CREATE INDEX sin CONCURRENTLY porque Flyway ejecuta cada migracion en una transaccion y
-- CONCURRENTLY no puede correr dentro de una. Bloquea escrituras mientras construye: sobre el
-- catalogo actual es instantaneo, y si algun dia hay que recrearlo sobre millones de filas,
-- conviene hacerlo a mano y fuera de Flyway.

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;

    CREATE INDEX IF NOT EXISTS idx_files_search_content_lower
        ON files USING gin (lower(search_content) gin_trgm_ops);

    CREATE INDEX IF NOT EXISTS idx_files_original_name_lower
        ON files USING gin (lower(original_file_name) gin_trgm_ops);

    -- El de la V5 queda sin uso posible: ninguna consulta compara la columna cruda. Mantenerlo
    -- costaria escritura y disco a cambio de nada.
    DROP INDEX IF EXISTS idx_file_search_content;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm no disponible: la busqueda funciona sin indice acelerador.';
END $$;
