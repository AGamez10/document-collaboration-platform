# Document Collaboration Platform

Backend SaaS que permite a cualquier aplicación web incorporar un gestor de archivos y un editor de documentos colaborativo (Word, Excel, PowerPoint) integrando un widget JavaScript, sin conocer ni operar la infraestructura de almacenamiento ni el motor de edición.

---

## El problema que resuelve

Toda empresa que digitaliza procesos termina necesitando lo mismo: que sus usuarios suban, organicen, compartan y **editen** documentos ofimáticos desde el navegador. Construir eso desde cero implica resolver cuatro problemas costosos y ajenos al negocio:

1. Almacenamiento de binarios (no en base de datos, con cuotas y control de tipos MIME).
2. Un motor de edición colaborativa en línea, que es una pieza pesada y con licenciamiento propio.
3. Un modelo de permisos que distinga espacios privados de espacios compartidos.
4. Trazabilidad y respaldo de la información.

Este proyecto **encapsula esos cuatro problemas detrás de una única API REST**. La aplicación consumidora integra un `<script>` y obtiene un gestor documental completo. Nunca sabe que detrás hay MinIO ni OnlyOffice: no los configura, no los expone y no depende de sus URLs.

**Para quién:** equipos de producto que necesitan gestión documental dentro de una aplicación existente (intranets, ERPs, sistemas de gestión) y no quieren construir ni mantener esa infraestructura. Cada aplicación consumidora es un *tenant* aislado, identificado por su propia API Key.

---

## Stack técnico

| Capa | Tecnología | Versión | Rol |
|---|---|---|---|
| Lenguaje | Java | 21 (LTS) | Base del proyecto |
| Framework | Spring Boot | 3.5.16 | Web MVC, Data JPA, Security, Validation |
| Base de datos | PostgreSQL | 16 | Metadatos, permisos, auditoría |
| Almacenamiento | MinIO (SDK `io.minio`) | 9.0.3 | Object storage S3-compatible para los binarios |
| Editor | OnlyOffice Document Server | 8.2 | Edición colaborativa, JWT obligatorio |
| Autenticación | `jjwt` + Spring Security | 0.13.0 | API Key, Widget Token (JWT), HTTP Basic para admin |
| Documentación API | springdoc-openapi | 2.8.17 | Swagger UI / OpenAPI 3 |
| Build | Maven Wrapper | — | `./mvnw`, sin Maven global |
| Utilidades | Lombok | — | Reducción de boilerplate |
| Infraestructura | Docker + Docker Compose | — | 4 servicios en red interna |
| Frontend embebible | JavaScript vanilla | — | Widget y panel administrativo, sin framework |

Sin dependencias fuera de esta lista: el `pom.xml` fija todas las versiones de forma explícita.

---

## Arquitectura

### Vista de infraestructura

```
   Aplicación consumidora (Laravel / React / Django / .NET ...)
                     │
                     │  <script src=".../office-platform-widget.js">
                     ▼
        ┌───────────────────────────────┐
        │   Document Collaboration      │   API REST stateless
        │   Platform  (Spring Boot)     │   :8080
        └───────────────────────────────┘
             │            │            │
             ▼            ▼            ▼
      ┌───────────┐ ┌───────────┐ ┌──────────────────┐
      │PostgreSQL │ │   MinIO   │ │    OnlyOffice    │
      │ metadatos │ │ binarios  │ │ Document Server  │
      └───────────┘ └───────────┘ └──────────────────┘
                          red interna office-platform-net
                    (OnlyOffice NO publica puerto al host)
```

### Vista de capas

Arquitectura por capas estricta, con el dominio aislado del transporte y de la infraestructura:

```
controller/   →   service/ (interfaz + Impl)   →   repository/
     │                      │                            │
   DTOs               reglas de negocio            Spring Data JPA
                            │
                            ▼
              storage/provider/ · minio/ · onlyoffice/
                   (infraestructura abstraída)
```

**Reglas que el código respeta sin excepción:**

- Las entidades JPA **nunca** salen del backend. Todo endpoint devuelve un DTO de `dto/response/`.
- Toda respuesta viaja en un envoltorio uniforme `ApiResponse<T>` (`success`, `timestamp`, `message`, `data`, `metadata`).
- La lógica de negocio vive en `service/`, jamás en controllers ni en entidades.
- Cada dominio expone una interfaz `XService` y su implementación `XServiceImpl`, para poder sustituir la implementación sin tocar al consumidor.
- Los errores se modelan como excepciones de dominio y `GlobalExceptionHandler` las traduce a códigos HTTP.

### Estructura de paquetes (`com.officeplatform`)

```
src/main/java/com/officeplatform/
├── config/            Beans de configuración: Security, CORS, MinIO, OpenAPI,
│                      Jackson, RestTemplate, seeder de administrador
├── controller/        9 controllers REST (File, Folder, Editor, Share, Admin,
│                      Project, User, WidgetAuth, Health)
├── dto/
│   ├── request/       Entradas validadas con Bean Validation
│   └── response/      Salidas — nunca entidades
├── entity/            9 entidades JPA (File, Folder, ApiKey, AdminUser,
│                      ActivityLog, EditorSession, KnownUser, SharePermission)
├── repository/        Repositorios Spring Data JPA
├── service/           Lógica de negocio por dominio: file, folder, editor,
│                      share, project, admin, backup, storage, activity, user
├── security/
│   ├── api/           ApiKeyFilter — aislamiento por tenant
│   ├── widget/        WidgetTokenFilter + WidgetTokenService — identidad de usuario
│   ├── jwt/           JwtService — firma de tokens
│   ├── admin/         AdminUserDetailsService
│   └── model/         ApiKeyPrincipal
├── onlyoffice/        DTOs y servicio de integración con Document Server
├── minio/service/     Cliente MinIO
├── storage/provider/  Abstracción de proveedor de almacenamiento
├── exception/         Excepciones de dominio + GlobalExceptionHandler
└── util/              Helpers: File, Mime, Url, Jwt, Docx, Xlsx, Pptx, Date

src/main/resources/
├── application.yml                    Configuración (todo externalizable por env)
└── static/
    ├── office-platform-widget.js      Widget embebible (gestor completo)
    └── admin/                         Panel administrativo (HTML/CSS/JS vanilla)
```

Aproximadamente **125 clases Java / 8.300 líneas** en `src/main`.

---

## Funcionalidades principales

### Gestión documental
- Subida de archivos con validación de tipo MIME (PDF, Word, Excel, PowerPoint) y límite de tamaño configurable.
- Carpetas anidadas con breadcrumbs, renombrado y movimiento entre espacios.
- **Dos espacios separados:** *Mis archivos* (privado, filtrado por identidad de usuario) y *Compartidos* (visible a todo el proyecto).
- Papelera con *soft delete*: eliminar, restaurar y purgar definitivamente.
- Búsqueda global por nombre en todo el espacio, no solo en la carpeta actual, respetando el aislamiento.
- Descarga individual de archivos y descarga de carpetas completas.

### Edición colaborativa
- Creación de documentos en blanco (Word, Excel, PowerPoint) generados desde cero por la propia plataforma.
- Apertura en OnlyOffice con configuración firmada y guardado automático vía callback.
- Sesiones de edición registradas, consultables y **cerrables en caliente** desde el panel admin (comando `drop` real al Document Server).

### Seguridad y multi-tenancy
- Aislamiento por **API Key** (`X-Api-Key`): cada aplicación consumidora solo ve sus propios recursos.
- **Widget Token**: JWT de corta vida que transporta la identidad del usuario final. La aplicación consumidora lo resuelve en su backend, de modo que la API Key nunca llega al navegador.
- Permisos granulares de compartición: por usuario o por proyecto, con niveles `VIEW` / `DOWNLOAD` / `EDIT`, revocables.
- Panel administrativo protegido con HTTP Basic y contraseña con hash BCrypt.

### Operación y auditoría
- Registro de actividad con resolución de nombres reales de usuario y proyecto.
- Directorio automático de usuarios conocidos por proyecto, con roles asignables.
- **Sistema de copias de seguridad físicas**: paquetes ZIP con `manifest.json` y streaming de binarios desde MinIO. Manual o programado (`@Scheduled`), con política de retención y métricas de espacio en disco.
- Dashboard con métricas de uso, actividad diaria y consumo por API Key.

---

## Instalación y ejecución local

### Requisitos

- **Java 21** (JDK)
- **Docker** y **Docker Compose**
- Maven **no** es necesario: el proyecto incluye el wrapper (`./mvnw`)

### 1. Clonar y configurar variables de entorno

```bash
git clone https://github.com/AGamez10/document-collaboration-platform.git
cd document-collaboration-platform
cp .env.example .env
```

Editá `.env` con tus valores. El archivo `.env` está en `.gitignore` y **nunca debe versionarse**.

Variables relevantes (todas documentadas en `.env.example`, sin valores reales):

| Variable | Para qué sirve |
|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Credenciales de la base de datos |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | Credenciales del object storage |
| `ONLYOFFICE_JWT_SECRET` | Secreto compartido con el Document Server. Mínimo 32 caracteres |
| `WIDGET_TOKEN_SECRET` | Firma de los tokens de identidad del widget. Mínimo 32 caracteres |
| `OFFICE_PLATFORM_PUBLIC_URL` | URL pública del servicio, usada para construir el callback de OnlyOffice |
| `OFFICE_PLATFORM_CORS_ALLOWED_ORIGINS` | Orígenes permitidos. En producción, lista explícita — nunca `*` |
| `OFFICE_PLATFORM_MAX_TOTAL_SIZE` | Cuota total de almacenamiento en bytes |
| `OFFICE_PLATFORM_BACKUP_*` | Ruta, periodicidad y retención de las copias de seguridad |

> `ONLYOFFICE_JWT_SECRET` y `WIDGET_TOKEN_SECRET` deben ser **distintos entre sí** y tener al menos 32 caracteres (requisito de HS256).

### 2. Levantar la infraestructura

```bash
docker compose up -d postgres minio onlyoffice
```

Esperá a que los *healthchecks* de `postgres` y `minio` pasen a `healthy`:

```bash
docker compose ps
```

### 3. Ejecutar la aplicación en modo desarrollo

```bash
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run`

### 4. Verificar

| Recurso | URL |
|---|---|
| Health check | http://localhost:8080/api/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |
| Panel administrativo | http://localhost:8080/admin/ |
| Página de prueba del widget | http://localhost:8080/test.html |

### Alternativa: todo con Docker

```bash
docker compose up --build
```

### Otros comandos

```bash
./mvnw test           # Ejecutar la suite de tests
./mvnw clean package  # Construir el JAR ejecutable
```

### Primer arranque

En el primer arranque, `DefaultAdminSeeder` crea un usuario administrador por defecto (`admin`) con una contraseña de desarrollo y lo advierte en el log.

> **Cambiá esa contraseña antes de exponer el servicio.** Es un valor de conveniencia para desarrollo local, no una credencial de producción.

Desde el panel admin generás la primera **API Key**, que es lo que habilita a una aplicación consumidora a usar la plataforma.

---

## Documentación de la API

La documentación interactiva completa está en **Swagger UI**: `http://localhost:8080/swagger-ui.html`

Todas las respuestas comparten el mismo envoltorio:

```json
{
  "success": true,
  "timestamp": "2026-01-15T10:30:00-05:00",
  "message": "Operación realizada correctamente",
  "data": { },
  "metadata": { }
}
```

### Autenticación

| Mecanismo | Header | Uso |
|---|---|---|
| API Key | `X-Api-Key: <key>` | Llamadas servidor a servidor. Identifica al tenant |
| Widget Token | `Authorization: Bearer <jwt>` | Llamadas desde el navegador. Identifica al usuario final |
| HTTP Basic | `Authorization: Basic <...>` | Exclusivo de `/api/admin/**` |

### Endpoints principales

**Identidad**

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/auth/resolve` | Intercambia API Key + identidad de usuario por un Widget Token |

**Archivos** — `/api/files`

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/files?scope={private\|shared}` | Listar archivos de una carpeta |
| `GET` | `/api/files/search?q={term}&scope={...}` | Búsqueda global por nombre |
| `GET` | `/api/files/trash` | Contenido de la papelera |
| `POST` | `/api/files/upload` | Subir un archivo |
| `POST` | `/api/files/new`, `/new/spreadsheet`, `/new/presentation` | Crear documento en blanco |
| `GET` | `/api/files/{id}/download` | Descargar |
| `PATCH` | `/api/files/{id}/rename`, `/api/files/{id}/move` | Renombrar / mover |
| `DELETE` | `/api/files/{id}` | Enviar a papelera (*soft delete*) |
| `POST` | `/api/files/{id}/restore` | Restaurar desde papelera |
| `DELETE` | `/api/files/{id}/purge` | Eliminar definitivamente |

**Carpetas** — `/api/folders`: `POST /`, `GET /`, `GET /search`, `PATCH /{id}/rename`, `PATCH /{id}/move`, `GET /{id}/download`, `DELETE /{id}`

**Editor**

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/editor/{id}` | Configuración firmada para montar el editor |
| `POST` | `/api/editor/{id}/close` | Cerrar sesión de edición |
| `POST` | `/api/onlyoffice/callback` | Callback de guardado (valida firma JWT) |
| `GET` | `/api/editor/proxy/**` | Proxy inverso hacia los assets del Document Server |

**Compartición** — `POST /api/share`, `GET /api/share/{resourceType}/{resourceId}`, `DELETE /api/share/{permissionId}`, `GET /api/shared-with-me`, `GET /api/shared-with-project`

**Administración** — `/api/admin`: dashboard, CRUD de API Keys, log de actividad, gestión de archivos, sesiones de editor activas, directorio de usuarios y roles, y gestión completa de copias de seguridad (`/backups`, `/backups/create`, `/backups/{filename}/download`, `/backups/config`).

---

## Decisiones técnicas y retos resueltos

### 1. Ocultar el Document Server detrás de un proxy inverso propio

**Problema:** OnlyOffice necesita ser alcanzable desde el navegador del usuario para cargar sus assets. Publicarlo directamente expone la infraestructura, obliga a gestionar su CORS y su TLS por separado, y ata al consumidor a una URL que no controlamos.

**Solución:** `GET /api/editor/proxy/**` reenvía las peticiones al Document Server desde dentro de la red interna de Docker. En `docker-compose.yml` el servicio `onlyoffice` **no publica puerto al host** de forma deliberada. El navegador solo habla con nuestro dominio; OnlyOffice es un detalle de implementación invisible desde afuera.

### 2. Dos mecanismos de autenticación coexistiendo en la misma cadena de filtros

**Problema:** hay dos tipos de llamador con necesidades opuestas. El backend del consumidor se autentica como *aplicación* (API Key). El navegador del usuario final necesita identificarse como *persona*, y no puede recibir la API Key: sería robable desde el HTML.

**Solución:** dos filtros encadenados. `WidgetTokenFilter` procesa `Authorization: Bearer` y, si no hay token, deja pasar la petición intacta a `ApiKeyFilter`, que procesa `X-Api-Key`. Ambos producen el mismo `ApiKeyPrincipal`, de modo que los servicios aguas abajo no distinguen el origen. El consumidor obtiene el Widget Token en su backend vía `POST /api/auth/resolve`, y la API Key nunca llega al cliente.

### 3. Cadenas de seguridad separadas para admin y API pública

El panel administrativo y la API pública tienen modelos de autenticación incompatibles. Se resolvieron con dos `SecurityFilterChain` ordenadas: `@Order(1)` con `securityMatcher("/api/admin/**")` y HTTP Basic, `@Order(2)` para el resto.

Un detalle no evidente: en la cadena de admin se **deshabilitó la autenticación anónima**. Con `anonymous` activo (el default de Spring Security), una petición sin credenciales se considera "autenticada como anónimo" a la que simplemente le falta `ROLE_ADMIN`, y el `ExceptionTranslationFilter` devuelve **403** en lugar de **401**. Al desactivarla, la petición es genuinamente no autenticada y llega al *entry point* de Basic, devolviendo `401` con `WWW-Authenticate`, que es la semántica correcta.

### 4. Fuga de aislamiento entre espacio privado y compartido

**Problema detectado en producción:** el listado resolvía primero el *fallback* de "usuario sin identidad" y después el ámbito. Un visor sin cédula que entraba a *Compartidos* recibía **todos** los archivos, incluidos los privados de otros usuarios.

**Solución:** `scope=shared` pasó a ser **autoritativo** y se evalúa **antes** que cualquier *fallback*. *Compartidos* filtra siempre por `user_id IS NULL` y es estructuralmente incapaz de devolver un archivo privado. Se ajustó también `moveFile` para reasignar la propiedad al cruzar de espacio. Un recordatorio de que en control de acceso **el orden de las guardas es la guarda**.

### 5. Permisos como capa aditiva, sin romper lo existente

Introducir permisos granulares sobre un sistema en uso normalmente rompe la visibilidad de todo lo ya creado. Aquí los permisos se diseñaron como **capa aditiva**: un recurso sin filas de permiso sigue siendo visible para todos —comportamiento previo intacto—, y solo al aparecer la primera fila se restringe a creador, administradores del proyecto y destinatarios explícitos.

El filtrado se aplica **después** del listado existente, sin tocar las *queries* originales. Además, la bandera `restricted` que consume el widget se calcula reutilizando las consultas que el filtro ya ejecuta, sin coste adicional.

### 6. Identidad del creador robusta ante datos heredados

Los recursos antiguos se identificaban solo por `created_by_name`, un campo frágil. Se añadió `created_by_user_id` como identificador fuerte, manteniendo `created_by_name` como *fallback* para las filas anteriores a la migración. La autorización intenta primero la coincidencia fuerte y degrada a la débil solo cuando el campo nuevo es nulo: se endurece el modelo sin invalidar el histórico.

### 7. Generación de documentos ofimáticos sin librerías pesadas

Crear un `.docx`, `.xlsx` o `.pptx` en blanco no requiere Apache POI. `DocxUtils`, `XlsxUtils` y `PptxUtils` ensamblan el paquete OOXML mínimo válido (ZIP + XML de las partes obligatorias). Se evita una dependencia grande a cambio de código explícito y controlado.

### 8. Copias de seguridad con *streaming*, no en memoria

Un respaldo debe poder incluir gigabytes de binarios. `BackupServiceImpl` escribe el ZIP a disco haciendo *streaming* de cada objeto desde MinIO, sin materializar el paquete en memoria, y acompaña los binarios con un `manifest.json` que preserva la estructura de carpetas y los metadatos necesarios para restaurar.

### 9. Cerrar una sesión de edición de verdad

Marcar `closedAt` en la base de datos no desconecta a nadie: el usuario sigue editando. `forceCloseSession` envía el comando `drop` al *Command Service* de OnlyOffice —firmado con el JWT correspondiente— **antes** de marcar el cierre. Si el `drop` falla, se registra el error y el cierre en base de datos se ejecuta igual, para no dejar sesiones fantasma.

---

## Deuda técnica conocida

Documentarla es parte del trabajo:

- **Sin migraciones versionadas.** El esquema se gestiona con `spring.jpa.hibernate.ddl-auto=update`. Adecuado para iterar rápido, insuficiente para producción a largo plazo: el siguiente paso natural es Flyway o Liquibase.
- **Cobertura de tests parcial.** La suite corre en verde y sin infraestructura: usa H2 en memoria (`scope` de test, no viaja en el JAR) y sustituye el cliente de MinIO por un *mock*. Cubre el arranque del contexto y la resolución de autoría para operaciones destructivas. Falta cubrir el aislamiento por *scope* y el filtrado de permisos compartidos.
- **Editor y compartición entre proyectos.** Abrir en el editor un recurso compartido desde otro proyecto puede fallar: el endpoint del editor está limitado al proyecto del llamador y todavía no consulta la tabla de permisos.
- **`FolderEntity` sin *soft delete*.** Los archivos tienen `deletedAt`; las carpetas aún no.

---

## Licencia

Código propietario. Este repositorio se publica con fines de **portafolio y demostración técnica**. No se concede licencia de uso, copia, modificación o distribución. Todos los derechos reservados.

Los datos, credenciales y contenidos de ejemplo han sido reemplazados por valores ficticios.
