# DataServer `G:\` y Office Platform

**Política de coexistencia — sin migración masiva**

---

## La decisión de fondo

**No vamos a copiar el DataServer a MinIO.** Son terabytes de historia y moverlos completos sería caro, lento y riesgoso, sin ningún beneficio real: nadie abre en el navegador un expediente de 2019.

La regla es simple:

| Contenido | Dónde vive | Cómo se accede |
|---|---|---|
| Histórico **2018–2024** | `G:\` únicamente | Explorador de Windows, como siempre |
| Proyectos **activos 2026** | `G:\` **y** MinIO | Explorador **y** Office Platform |

Un archivo activo existe en los dos lados. El de `G:\` sigue siendo el original; el de MinIO es la copia que permite editarlo en el navegador y compartirlo con permisos.

### Por qué así

**Migrar todo sería tirar plata.** El costo de almacenamiento se duplicaría para archivos que nadie consulta.

**El riesgo se concentra donde importa.** Sincronizar solo lo activo significa que un error afecta a una carpeta del año en curso, no a seis años de historia.

**Vuelta atrás inmediata.** Si Office Platform tuviera que apagarse mañana, `G:\` sigue intacto y completo. Nada quedó atrapado adentro.

---

## Qué se sincroniza

Una carpeta entra a MinIO cuando cumple **las tres** condiciones:

1. Es de un proyecto **2026 en curso**.
2. Alguien necesita **editarla en el navegador** o **compartirla** con permisos.
3. Pesa menos de ~50 GB.

Si no cumple las tres, se queda en `G:\`.

---

## Cómo sincronizar

El script es `scripts/sync-dataserver-to-minio.ps1`. Se ejecuta desde PowerShell, en la máquina que tiene acceso a `G:\`.

### Siempre, siempre: primero en seco

```powershell
.\scripts\sync-dataserver-to-minio.ps1 -SourcePath "G:\Proyectos_2026" -WhatIf
```

`-WhatIf` muestra exactamente qué se transferiría **sin escribir nada**. Leé esa salida antes de seguir. Si la lista tiene algo que no esperabas, ahí te enteraste, no después.

### Cuando la salida en seco es la correcta

```powershell
.\scripts\sync-dataserver-to-minio.ps1 -SourcePath "G:\Proyectos_2026"
```

Copia y actualiza. **No borra nada** de MinIO.

### Una carpeta puntual con nombre propio en el bucket

```powershell
.\scripts\sync-dataserver-to-minio.ps1 `
    -SourcePath "G:\Proyectos_2026\Planta_Norte" `
    -Prefix "planta-norte-2026" `
    -WhatIf
```

### Desde otra máquina de la red

```powershell
.\scripts\sync-dataserver-to-minio.ps1 `
    -SourcePath "\\dataserver\Proyectos_2026" `
    -Endpoint "http://192.168.1.50:9000" `
    -WhatIf
```

---

## El modo espejo: cuidado

El parámetro `-Mirror` usa `rclone sync` en vez de `rclone copy`. La diferencia no es menor:

| Modo | Qué hace | Riesgo |
|---|---|---|
| normal (por defecto) | agrega y actualiza | ninguno, nunca borra |
| `-Mirror` | además **borra** de MinIO lo que ya no está en el origen | **destructivo** |

`-Mirror` borra archivos que solo existen en MinIO — por ejemplo, un documento que alguien creó desde el navegador y nunca estuvo en `G:\`. **Ese trabajo se pierde.**

Por eso el script pide que escribas `MIRROR` a mano para confirmar. Usalo solo si estás seguro de que MinIO no tiene nada propio, y **siempre con `-WhatIf` antes**.

---

## Frecuencia sugerida

| Cuándo | Qué |
|---|---|
| Al abrir un proyecto nuevo | sincronizar su carpeta una vez |
| Semanal | volver a sincronizar los proyectos activos |
| Al cerrar un proyecto | dejar de sincronizar; queda frío en `G:\` |

No hace falta automatizarlo al principio. Una corrida semanal manual, con `-WhatIf` primero, es más segura que una tarea programada que nadie mira.

---

## Ver el bucket como una unidad de disco

Para consultar lo que está en MinIO sin abrir el navegador:

```
scripts\mount-dataserver-minio.bat P
```

Monta el bucket como unidad `P:\`. Requiere [Rclone](https://rclone.org/downloads/) y [WinFsp](https://winfsp.dev/).

> **Es solo lectura conveniente, no un lugar donde trabajar.** Un archivo copiado a `P:\` a mano llega al bucket pero **no queda registrado en la base de datos** de Office Platform: no aparece en el gestor, no tiene permisos ni historial. Para que exista de verdad en la plataforma, se sube desde el widget o por la API.

---

## Cuando algo no funciona

| Síntoma | Causa habitual |
|---|---|
| "Cannot reach minio:office-platform" | MinIO apagado, o el endpoint apunta a `localhost` desde otra máquina |
| "rclone is not on PATH" | falta instalar Rclone, o el terminal se abrió antes de instalarlo |
| El drive `P:` no aparece | falta WinFsp |
| Copió menos archivos de los esperados | el script omite lo que ya está actualizado; es correcto |
| Subí un archivo a `P:\` y no lo veo en el gestor | esperado: hay que subirlo por el widget para que se registre |

---

## Resumen para tener a mano

```
Histórico  →  se queda en G:\, no se toca
Activo     →  se sincroniza a MinIO, semanalmente
Siempre    →  -WhatIf antes de escribir
Nunca      →  -Mirror sin haber corrido -WhatIf antes
```

---

*Office Platform — Documentación operativa*
