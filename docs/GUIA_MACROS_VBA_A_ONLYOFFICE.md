# De Excel VBA a Office Platform

**Guía de conversión de macros para analistas de negocio**

Si automatizás hojas de cálculo con VBA, esta guía te lleva de lo que ya sabés a lo que vas a usar. No necesitás aprender a programar desde cero: las ideas son las mismas, cambia la forma de escribirlas.

Y no tenés que traducir a mano. En la sección 4 hay un prompt que hace la conversión por vos.

---

## 1. Por qué el cambio te conviene

No es un capricho técnico. Estas son las cuatro diferencias que vas a notar en el día a día.

### Funciona en cualquier lado

Tu macro corre en el navegador. Windows, Mac, Linux, una tablet, la computadora de un cliente. No hay que instalar Office ni copiar el archivo a una máquina "que sí lo tiene".

### Se acabaron las alertas rojas

Ese cartel de *"Las macros se han deshabilitado"*, el bloqueo del antivirus, el archivo que llega por correo y Windows marca como peligroso: nada de eso pasa acá. El código corre en un entorno aislado del sistema operativo. **No hay `.xlsm` que nadie quiera abrir.**

### Se conecta con los sistemas de la empresa

Una macro puede consultar el ERP, un servicio web o una base de datos y traer los datos a la hoja:

```javascript
// Traer el estado de una orden desde el ERP
let respuesta = await fetch("https://erp.empresa.com/api/ordenes/12345");
let datos = await respuesta.json();
sheet.GetRange("B2").SetValue(datos.estado);
```

En VBA esto significaba librerías, referencias y permisos. Acá es una línea.

### La inteligencia artificial lo entiende

JavaScript es el lenguaje más usado del mundo, así que ChatGPT y Claude lo escriben mucho mejor que VBA. Le pedís lo que necesitás en castellano y te devuelve código que funciona. **Esa es la ventaja que más vas a usar.**

---

## 2. Diccionario de equivalencias

Lo mismo que hacías, escrito distinto. Cada línea de JavaScript termina en punto y coma (`;`).

### Empezar a trabajar

| Excel VBA | OnlyOffice JavaScript |
|---|---|
| `Set ws = ActiveSheet` | `let sheet = Api.GetActiveSheet();` |
| `Set wb = ActiveWorkbook` | `let libro = Api.GetActiveWorkbook();` |
| `Sheets("Datos").Activate` | `Api.GetSheet("Datos").SetActive();` |

### Leer y escribir celdas

| Excel VBA | OnlyOffice JavaScript |
|---|---|
| `Range("A1").Value = "Total"` | `sheet.GetRange("A1").SetValue("Total");` |
| `x = Range("A1").Value` | `let x = sheet.GetRange("A1").GetValue();` |
| `Cells(fila, col).Value = 100` | `sheet.GetRangeByNumber(fila - 1, col - 1).SetValue(100);` |
| `Range("A1:C10").ClearContents` | `sheet.GetRange("A1:C10").Clear();` |
| `Range("D2").Formula = "=B2*C2"` | `sheet.GetRange("D2").SetValue("=B2*C2");` |

> **Ojo con esto.** `GetRangeByNumber` cuenta desde **cero**, no desde uno. La celda A1 es `GetRangeByNumber(0, 0)`. Es la causa número uno de macros convertidas que apuntan una fila más abajo de lo esperado.

### Dar formato

| Excel VBA | OnlyOffice JavaScript |
|---|---|
| `.Font.Bold = True` | `.SetBold(true);` |
| `.Font.Italic = True` | `.SetItalic(true);` |
| `.Font.Size = 12` | `.SetFontSize(12);` |
| `.Font.Name = "Calibri"` | `.SetFontName("Calibri");` |
| `.Font.Color = RGB(255,255,255)` | `.SetFontColor(Api.CreateColorFromRGB(255, 255, 255));` |
| `.Interior.Color = RGB(31,78,121)` | `.SetFillColor(Api.CreateColorFromRGB(31, 78, 121));` |
| `.HorizontalAlignment = xlCenter` | `.SetAlignHorizontal("center");` |
| `.NumberFormat = "#,##0.00"` | `.SetNumberFormat("#,##0.00");` |
| `.Columns.AutoFit` | `.SetColumnWidth(18);` |

### Bordes

VBA usa constantes; acá se nombran con texto:

```javascript
// Borde inferior fino y gris
sheet.GetRange("A1:F1").SetBorders(
    "Bottom",                              // Top, Bottom, Left, Right
    "Thin",                                // Thin, Medium, Thick, Double
    Api.CreateColorFromRGB(180, 180, 180)
);
```

### Repetir y decidir

| Excel VBA | OnlyOffice JavaScript |
|---|---|
| `For i = 2 To 100` … `Next i` | `for (let i = 2; i <= 100; i++) { … }` |
| `If x > 10 Then` … `End If` | `if (x > 10) { … }` |
| `If … Else … End If` | `if (…) { … } else { … }` |

Ejemplo completo, recorriendo filas:

```javascript
let sheet = Api.GetActiveSheet();

for (let fila = 2; fila <= 100; fila++) {
    let importe = sheet.GetRange("E" + fila).GetValue();
    if (importe > 1000000) {
        sheet.GetRange("E" + fila).SetFillColor(Api.CreateColorFromRGB(255, 235, 156));
    }
}
```

### Avisarle algo al usuario

| Excel VBA | OnlyOffice JavaScript |
|---|---|
| `MsgBox "Listo"` | `Api.ShowAlert("Listo");` |
| `MsgBox "Van " & n & " filas"` | `Api.ShowAlert("Van " + n + " filas");` |

### Lo que cambia de fondo

| Concepto | VBA | OnlyOffice |
|---|---|---|
| Declarar variable | `Dim x As Integer` | `let x = 0;` (sin tipo) |
| Unir texto | `"a" & "b"` | `"a" + "b"` |
| Comentario | `' esto es un comentario` | `// esto es un comentario` |
| Fin de instrucción | salto de línea | punto y coma `;` |
| Bloques | `End If`, `Next` | llaves `{ }` |
| Índice de celda | desde 1 | **desde 0** |

---

## 3. Tu primera macro, paso a paso

1. Abrí la hoja de cálculo en Office Platform.
2. Andá a la pestaña **Vista** → botón **Macros**.
3. Pegá este código:

```javascript
(function () {
    let sheet = Api.GetActiveSheet();
    sheet.GetRange("A1").SetValue("¡Funciona!");
    Api.ShowAlert("Mi primera macro corrió bien.");
})();
```

4. Poné **Ejecutar**.

Ese `(function () { ... })()` que envuelve todo es el equivalente a `Sub` … `End Sub`. Escribilo siempre igual; es una fórmula fija.

---

## 4. Prompt maestro: que la IA convierta por vos

**No traduzcas a mano.** Copiá el texto de abajo, pegalo en ChatGPT o Claude, y debajo pegá tu macro de VBA.

> Sos experto en la API de macros de OnlyOffice Document Server (Api de hojas de cálculo, la misma que usa Office Platform).
>
> Convertí la macro de Excel VBA que te paso al final a JavaScript de OnlyOffice, siguiendo estas reglas sin excepción:
>
> 1. Envolvé todo el código en `(function () { ... })();`
> 2. Usá `Api.GetActiveSheet()` para obtener la hoja activa.
> 3. Para celdas por letra usá `sheet.GetRange("A1")`; por número usá `sheet.GetRangeByNumber(fila, columna)` recordando que **empiezan en 0**, no en 1.
> 4. Leer es `.GetValue()`, escribir es `.SetValue(valor)`.
> 5. Colores: `Api.CreateColorFromRGB(r, g, b)`. Relleno con `.SetFillColor(...)`, letra con `.SetFontColor(...)`.
> 6. Formato: `.SetBold(true)`, `.SetFontSize(n)`, `.SetFontName("...")`, `.SetAlignHorizontal("center")`, `.SetNumberFormat("#,##0.00")`.
> 7. Bordes: `.SetBorders("Bottom", "Thin", Api.CreateColorFromRGB(r, g, b))`.
> 8. `MsgBox` se convierte en `Api.ShowAlert(...)`.
> 9. Ancho de columna con `.SetColumnWidth(n)`; no existe AutoFit.
> 10. Comentá cada bloque en castellano, explicando qué hace en términos de negocio, no de programación.
> 11. No inventes funciones que no existan en la API de OnlyOffice. Si algo de VBA no tiene equivalente, decilo claramente en un comentario y proponé la alternativa más cercana.
>
> Devolveme únicamente el código final, listo para pegar en el editor de macros, y debajo una lista breve de las diferencias de comportamiento que debería revisar.
>
> Esta es mi macro de VBA:
>
> ```vba
> [PEGÁ ACÁ TU MACRO]
> ```

**Revisá siempre el resultado sobre una copia del archivo antes de usarlo en producción.** La IA acierta casi siempre, pero el que responde por el número final sos vos.

---

## 5. Botones en la hoja: que nadie vea código

El usuario final no debería abrir el editor de macros nunca. Se le pone un botón y listo.

**Paso 1 — Dibujar la forma.**
Pestaña **Insertar** → **Formas** → elegí un rectángulo redondeado. Dibujalo sobre la hoja, en un lugar visible.

**Paso 2 — Ponerle texto.**
Doble clic sobre la forma y escribí la acción, no el mecanismo. Un emoji ayuda a encontrarla rápido:

- ✅ `🔄 Estandarizar datos`
- ✅ `🎨 Aplicar formato`
- ❌ `Ejecutar macro 1`

**Paso 3 — Darle color.**
Clic derecho → **Relleno**. Un color sobrio y el texto en blanco. Que se vea como un botón, no como un adorno.

**Paso 4 — Asignar la macro.**
Clic derecho sobre la forma → **Asignar macro** → elegí la macro de la lista → **Aceptar**.

Listo. A partir de ahí el usuario hace clic y la macro corre. Nunca ve una línea de código.

> **Consejo de diseño:** agrupá los botones arriba a la derecha, alineados. Una hoja con botones desparramados se siente improvisada; tres botones alineados se sienten un sistema.

---

## 6. Cuando algo no funciona

| Síntoma | Causa habitual |
|---|---|
| No pasa nada al ejecutar | Falta el `(function () { ... })();` alrededor |
| Escribe una fila más abajo | `GetRangeByNumber` empieza en 0, no en 1 |
| "Api is not defined" | La macro se ejecutó fuera del editor de macros |
| El color no se aplica | Falta `Api.CreateColorFromRGB(...)`; no acepta `RGB(...)` |
| El botón no hace nada | La forma no tiene macro asignada (clic derecho → Asignar macro) |
| No veo el botón Macros | El archivo está en modo lectura; abrilo en modo edición |

---

## 7. Material de apoyo

- **`samples/Plantilla_Maestra_Macros.xlsx`** — hoja de ejemplo con tres botones ya funcionando.
- **`samples/macros_plantilla.js`** — el código de esas tres macros, comentado línea por línea.
- **Botón «Ayuda Macros»** dentro del gestor de archivos — tenés este prompt y la tabla de equivalencias a un clic, con botones para copiar.

---

*Office Platform — Documentación interna*
