/* =====================================================================
 * Plantilla_Maestra_Macros.xlsx — código de las tres macros de ejemplo
 * =====================================================================
 *
 * CÓMO CARGARLAS (una sola vez por archivo):
 *
 *   1. Abrí Plantilla_Maestra_Macros.xlsx en Office Platform, en modo EDICIÓN.
 *      (En modo lectura el botón de Macros no aparece.)
 *   2. Pestaña VISTA → botón Macros.
 *   3. Creá una macro nueva con el botón +, ponele el nombre exacto que
 *      figura en cada bloque de abajo, y pegá su código.
 *   4. Repetí para las tres.
 *   5. Volvé a la hoja, clic derecho sobre cada botón de color →
 *      "Asignar macro" → elegí la que corresponde.
 *
 * Los nombres importan: los botones de la hoja los buscan por nombre.
 *
 * La tabla vive en A5:F14, con los encabezados en la fila 5.
 * Recordá que GetRangeByNumber cuenta desde 0: la fila 5 de la hoja es
 * el índice 4.
 * ===================================================================== */


/* =====================================================================
 * MACRO 1 — macroEstandarizarDatos
 * ---------------------------------------------------------------------
 * Deja los datos listos para trabajar: saca espacios sobrantes que se
 * cuelan al copiar y pegar, normaliza mayúsculas y acomoda las columnas.
 *
 * Equivale a un Trim + Proper de VBA recorriendo el rango.
 * ===================================================================== */
(function () {
    var sheet = Api.GetActiveSheet();

    var FILA_ENCABEZADO = 5;   // fila donde están los títulos
    var PRIMERA_FILA    = 6;   // primera fila con datos
    var ULTIMA_FILA     = 14;  // última fila con datos

    var corregidas = 0;

    for (var fila = PRIMERA_FILA; fila <= ULTIMA_FILA; fila++) {

        // --- Código: siempre en mayúsculas y sin espacios (ej. "sol-001" -> "SOL-001")
        var celdaCodigo = sheet.GetRange("A" + fila);
        var codigo = celdaCodigo.GetValue();
        if (codigo) {
            var codigoLimpio = String(codigo).trim().toUpperCase();
            if (codigoLimpio !== String(codigo)) {
                celdaCodigo.SetValue(codigoLimpio);
                corregidas++;
            }
        }

        // --- Solicitud y Responsable: cada palabra con inicial mayúscula
        //     ("  compra de  insumos " -> "Compra De Insumos")
        var columnasTexto = ["B", "C"];
        for (var c = 0; c < columnasTexto.length; c++) {
            var celda = sheet.GetRange(columnasTexto[c] + fila);
            var texto = celda.GetValue();
            if (texto) {
                // trim quita los extremos; el replace colapsa espacios internos repetidos
                var limpio = String(texto).trim().replace(/\s+/g, " ");
                // Inicial mayúscula en cada palabra
                limpio = limpio.toLowerCase().replace(/(^|\s)\S/g, function (letra) {
                    return letra.toUpperCase();
                });
                if (limpio !== String(texto)) {
                    celda.SetValue(limpio);
                    corregidas++;
                }
            }
        }

        // --- Estado: mayúscula inicial, para que las comparaciones sean confiables
        var celdaEstado = sheet.GetRange("D" + fila);
        var estado = celdaEstado.GetValue();
        if (estado) {
            var e = String(estado).trim().toLowerCase();
            e = e.charAt(0).toUpperCase() + e.slice(1);
            if (e !== String(estado)) {
                celdaEstado.SetValue(e);
                corregidas++;
            }
        }
    }

    // --- Anchos fijos. OnlyOffice no tiene AutoFit, así que se asignan a mano
    //     con valores pensados para el contenido de cada columna.
    sheet.GetRange("A" + FILA_ENCABEZADO).SetColumnWidth(14);  // Código
    sheet.GetRange("B" + FILA_ENCABEZADO).SetColumnWidth(34);  // Solicitud
    sheet.GetRange("C" + FILA_ENCABEZADO).SetColumnWidth(22);  // Responsable
    sheet.GetRange("D" + FILA_ENCABEZADO).SetColumnWidth(14);  // Estado
    sheet.GetRange("E" + FILA_ENCABEZADO).SetColumnWidth(16);  // Importe
    sheet.GetRange("F" + FILA_ENCABEZADO).SetColumnWidth(14);  // Fecha

    Api.ShowAlert("Datos estandarizados.\n\nCeldas corregidas: " + corregidas);
})();


/* =====================================================================
 * MACRO 2 — macroEstiloCorporativo
 * ---------------------------------------------------------------------
 * Aplica el formato ejecutivo completo: encabezado azul, bordes finos,
 * importes como moneda y una fila de total con fórmula viva.
 *
 * La fórmula se escribe con SetValue("=SUMA(...)"), igual que .Formula
 * en VBA: queda recalculando, no es un número fijo.
 * ===================================================================== */
(function () {
    var sheet = Api.GetActiveSheet();

    var FILA_ENCABEZADO = 5;
    var PRIMERA_FILA    = 6;
    var ULTIMA_FILA     = 14;
    var FILA_TOTAL      = ULTIMA_FILA + 1;

    // Paleta corporativa, definida una vez para no repetir números por todos lados
    var AZUL_OSCURO = Api.CreateColorFromRGB(31, 78, 121);
    var BLANCO      = Api.CreateColorFromRGB(255, 255, 255);
    var GRIS_BORDE  = Api.CreateColorFromRGB(190, 195, 199);
    var GRIS_SUAVE  = Api.CreateColorFromRGB(242, 244, 246);
    var NEGRO_TEXTO = Api.CreateColorFromRGB(33, 37, 41);

    // --- Encabezado: fondo azul, texto blanco en negrita, centrado
    var encabezado = sheet.GetRange("A" + FILA_ENCABEZADO + ":F" + FILA_ENCABEZADO);
    encabezado.SetFillColor(AZUL_OSCURO);
    encabezado.SetFontColor(BLANCO);
    encabezado.SetBold(true);
    encabezado.SetFontSize(11);
    encabezado.SetAlignHorizontal("center");
    encabezado.SetAlignVertical("center");

    // --- Cuerpo de la tabla
    var cuerpo = sheet.GetRange("A" + PRIMERA_FILA + ":F" + ULTIMA_FILA);
    cuerpo.SetFontName("Calibri");
    cuerpo.SetFontSize(10);
    cuerpo.SetFontColor(NEGRO_TEXTO);

    // Bordes finos alrededor y entre celdas: se aplican lado por lado
    var lados = ["Top", "Bottom", "Left", "Right", "InsideHorizontal", "InsideVertical"];
    for (var i = 0; i < lados.length; i++) {
        cuerpo.SetBorders(lados[i], "Thin", GRIS_BORDE);
    }

    // --- Filas alternadas en gris muy claro, para leer sin perder el renglón
    for (var fila = PRIMERA_FILA; fila <= ULTIMA_FILA; fila++) {
        if ((fila - PRIMERA_FILA) % 2 === 1) {
            sheet.GetRange("A" + fila + ":F" + fila).SetFillColor(GRIS_SUAVE);
        }
    }

    // --- Importes como moneda, alineados a la derecha
    var importes = sheet.GetRange("E" + PRIMERA_FILA + ":E" + ULTIMA_FILA);
    importes.SetNumberFormat("$ #,##0.00");
    importes.SetAlignHorizontal("right");

    // --- Fechas con formato corto
    var fechas = sheet.GetRange("F" + PRIMERA_FILA + ":F" + ULTIMA_FILA);
    fechas.SetNumberFormat("dd/mm/yyyy");
    fechas.SetAlignHorizontal("center");

    // --- Códigos y estados centrados
    sheet.GetRange("A" + PRIMERA_FILA + ":A" + ULTIMA_FILA).SetAlignHorizontal("center");
    sheet.GetRange("D" + PRIMERA_FILA + ":D" + ULTIMA_FILA).SetAlignHorizontal("center");

    // --- Fila de total, con fórmula real para que siga viva al editar los datos
    sheet.GetRange("D" + FILA_TOTAL).SetValue("TOTAL");
    sheet.GetRange("D" + FILA_TOTAL).SetBold(true);
    sheet.GetRange("D" + FILA_TOTAL).SetAlignHorizontal("right");

    var celdaTotal = sheet.GetRange("E" + FILA_TOTAL);
    celdaTotal.SetValue("=SUM(E" + PRIMERA_FILA + ":E" + ULTIMA_FILA + ")");
    celdaTotal.SetBold(true);
    celdaTotal.SetNumberFormat("$ #,##0.00");
    celdaTotal.SetAlignHorizontal("right");

    var filaTotal = sheet.GetRange("A" + FILA_TOTAL + ":F" + FILA_TOTAL);
    filaTotal.SetFillColor(Api.CreateColorFromRGB(222, 229, 236));
    filaTotal.SetBorders("Top", "Medium", AZUL_OSCURO);

    Api.ShowAlert("Formato corporativo aplicado.");
})();


/* =====================================================================
 * MACRO 3 — macroResaltarAlertas
 * ---------------------------------------------------------------------
 * Semáforo sobre la columna Estado: amarillo lo pendiente, rojo suave lo
 * vencido, verde lo aprobado. El resto queda sin pintar.
 *
 * Equivale a un If/ElseIf dentro de un For, pero comparando en minúsculas
 * para que "VENCIDO", "Vencido" y "vencido" cuenten igual.
 * ===================================================================== */
(function () {
    var sheet = Api.GetActiveSheet();

    var PRIMERA_FILA = 6;
    var ULTIMA_FILA  = 14;

    var AMARILLO   = Api.CreateColorFromRGB(255, 242, 204);
    var ROJO_SUAVE = Api.CreateColorFromRGB(248, 215, 218);
    var VERDE      = Api.CreateColorFromRGB(212, 237, 218);
    var SIN_COLOR  = Api.CreateColorFromRGB(255, 255, 255);

    var pendientes = 0;
    var vencidos   = 0;
    var aprobados  = 0;

    for (var fila = PRIMERA_FILA; fila <= ULTIMA_FILA; fila++) {
        var celda  = sheet.GetRange("D" + fila);
        var valor  = celda.GetValue();
        var estado = valor ? String(valor).trim().toLowerCase() : "";

        if (estado === "pendiente") {
            celda.SetFillColor(AMARILLO);
            pendientes++;
        } else if (estado === "vencido") {
            celda.SetFillColor(ROJO_SUAVE);
            celda.SetBold(true);
            vencidos++;
        } else if (estado === "aprobado") {
            celda.SetFillColor(VERDE);
            aprobados++;
        } else {
            // Se limpia el color por si la fila cambió de estado desde la última corrida
            celda.SetFillColor(SIN_COLOR);
        }
    }

    Api.ShowAlert(
        "Revisión de estados terminada.\n\n" +
        "Vencidos:   " + vencidos + "\n" +
        "Pendientes: " + pendientes + "\n" +
        "Aprobados:  " + aprobados
    );
})();
