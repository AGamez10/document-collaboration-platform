-- V5: texto extraido de los documentos, para buscar por contenido.
--
-- Columna anulable: los 287 archivos que ya existen no tienen texto extraido, y null es
-- exactamente eso. Se indexan cuando alguien los vuelva a guardar, o con una reindexacion
-- posterior; mientras tanto siguen encontrandose por nombre como siempre.
--
-- TEXT y no varchar: un documento largo no entra en 255 caracteres y truncar en la base seria
-- perder texto en silencio. El limite real lo pone el extractor, en 20.000 caracteres, que es
-- una decision de memoria y no de esquema.

ALTER TABLE files ADD COLUMN IF NOT EXISTS search_content text;

-- Indice trigram para que el LIKE '%palabra%' sobre texto largo no degrade a escaneo completo.
-- Se intenta la extension y, si no esta disponible en esta instalacion de PostgreSQL, la
-- migracion sigue: la busqueda funciona igual, solo que sin ese acelerador.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    CREATE INDEX IF NOT EXISTS idx_file_search_content
        ON files USING gin (search_content gin_trgm_ops);
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm no disponible: la busqueda por contenido funciona sin indice acelerador.';
END $$;
