# Configuración de red y despliegue

Plantilla del archivo `.env` que acompaña a `docker-compose.yml`, con el porqué de cada
valor. Copiala tal cual:

```bash
cp docs/CONFIGURACION-RED-Y-DESPLIEGUE.md /dev/null   # solo para leerla
# y creá el archivo con el bloque de abajo:
nano .env.example        # pegá el bloque y guardá
cp .env.example .env     # ajustá los valores reales
docker compose up -d --build
```

> El `.env` real no se versiona: tiene secretos. El `.env.example` sí, para que quien
> despliegue sepa qué tiene que definir.

## El ajuste que más cuesta descubrir

`ONLYOFFICE_PUBLIC_URL` y `OFFICE_PLATFORM_PUBLIC_URL` tienen que ser alcanzables **desde
la máquina de cada usuario**, no desde el servidor.

OnlyOffice corre en el navegador de quien edita: cuando esa persona abre un documento, su
navegador pide el editor a `ONLYOFFICE_PUBLIC_URL` y el Document Server devuelve el archivo
guardado a `OFFICE_PLATFORM_PUBLIC_URL`. Si esas URLs dicen `localhost`, funcionan
únicamente en el equipo donde está instalada la plataforma. Desde cualquier otra máquina de
la planta el editor abre en blanco **sin mostrar ningún error**, que es el síntoma más caro
de diagnosticar porque parece que el documento está roto.

Para averiguar la IP en el servidor: `ipconfig` en Windows, `ip addr` en Linux.

## Plantilla completa

```bash
# ── RED DE LA EMPRESA ───────────────────────────────────────────────────────
# IP del servidor dentro de la LAN. Ejemplo LAN Plastitec:
SERVER_HOST_IP=192.168.1.50

ONLYOFFICE_PUBLIC_URL=http://${SERVER_HOST_IP}:8081
OFFICE_PLATFORM_PUBLIC_URL=http://${SERVER_HOST_IP}:8080

# Orígenes permitidos para el widget embebido. En producción conviene listar los
# sistemas que lo integran en lugar de dejar el comodín.
# OFFICE_PLATFORM_CORS_ALLOWED_ORIGINS=http://192.168.1.50:3000,http://192.168.1.51
OFFICE_PLATFORM_CORS_ALLOWED_ORIGINS=*

# ── SECRETOS ────────────────────────────────────────────────────────────────
# Cambiar TODOS antes de producción. Los valores por defecto del código están
# para que el proyecto arranque en una máquina de desarrollo, no para sostener
# los documentos de una empresa.
POSTGRES_DB=office_platform
POSTGRES_USER=office_platform
POSTGRES_PASSWORD=cambiar-esta-contrasena

MINIO_ROOT_USER=cambiar-este-usuario
MINIO_ROOT_PASSWORD=cambiar-esta-contrasena-minio

# Mínimo 32 caracteres: firma los tokens de identidad del widget.
WIDGET_TOKEN_SECRET=cambiar-por-un-secreto-de-al-menos-32-caracteres

# Compartido con el Document Server: firma los pedidos del editor en los dos
# sentidos. Si no coincide con el del contenedor de OnlyOffice, el editor abre
# y no guarda.
ONLYOFFICE_JWT_SECRET=cambiar-por-otro-secreto-de-32-caracteres

# ── LÍMITES Y RESPALDO ──────────────────────────────────────────────────────
# Subir este tope exige revisar también el proxy si hay uno adelante: nginx
# corta en 1 MB por defecto y el error aparece en el navegador, no acá.
OFFICE_PLATFORM_MAX_FILE_SIZE=500MB

OFFICE_PLATFORM_BACKUP_AUTO_ENABLED=true
OFFICE_PLATFORM_BACKUP_INTERVAL_HOURS=24
OFFICE_PLATFORM_BACKUP_MAX_RETAINED=10

# Copia del respaldo hacia un destino secundario (NAS, disco externo, recurso de
# red montado en el host). Un respaldo que vive en el mismo disco que la base no
# es un respaldo: el incendio se lleva los dos.
# OFFICE_PLATFORM_BACKUP_REPLICATION_ENABLED=true
# OFFICE_PLATFORM_BACKUP_REPLICATION_DIRECTORY=/respaldos-nas

# Versiones que se conservan por archivo. Cada guardado del editor archiva una
# copia del estado previo, y esas copias no cuentan contra la cuota del proyecto.
OFFICE_PLATFORM_MAX_VERSIONS_PER_FILE=20

# ── MIGRACIÓN DESDE EL DATASERVER ───────────────────────────────────────────
# Carpetas desde las que el panel puede importar en masa. Vacío = sin
# restricción, que sirve el día de la migración pero deja el endpoint leyendo
# cualquier carpeta del contenedor. En operación normal, fijala al punto de
# montaje del recurso de red y nada más.
# OFFICE_PLATFORM_IMPORT_ALLOWED_ROOTS=/import

# Indexa al arrancar lo que quedó sin texto extraído (restauraciones, ingestas).
OFFICE_PLATFORM_INDEX_ON_STARTUP=true
```

## Montar el DataServer para migrarlo

La importación masiva lee del disco del contenedor, no de la red. El recurso se monta en el
host y se expone como volumen:

```yaml
# docker-compose.yml, servicio office-platform-app
volumes:
  - ./backups:/app/backups
  - /mnt/dataserver:/import/dataserver:ro   # <-- agregar
```

En Windows, montar primero el recurso (`net use Z: \\DataServer\Compartido`) y exponer esa
unidad en el volumen. El `:ro` no es decorativo: la migración solo necesita leer, y un
montaje de escritura sobre el DataServer vivo es un riesgo que no compra nada.

Después, desde el panel: **Archivos & Papelera → Migración / Carga Masiva**, ruta
`/import/dataserver`.

## Verificación después de desplegar

| Qué | Cómo |
|---|---|
| La app levantó | `docker compose logs office-platform-app \| grep "Started OfficePlatform"` |
| Las migraciones corrieron | mismo log: `now at version vN` |
| El editor abre desde otra máquina | abrir un `.docx` desde una PC de la planta, no desde el servidor |
| Base y almacenamiento a la par | panel → Dashboard → **Verificar paridad** |
| El respaldo llega al destino secundario | panel → Respaldos → **Probar destino remoto** |
