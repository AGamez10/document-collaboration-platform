"""Genera samples/Plantilla_Maestra_Macros.xlsx.

Los datos se dejan a propósito "sucios" (espacios de más, mayúsculas
inconsistentes, sin formato) para que las tres macros de ejemplo tengan algo
real que arreglar cuando el usuario las ejecute.
"""
import datetime
import os

from openpyxl import Workbook
from openpyxl.drawing.spreadsheet_drawing import AbsoluteAnchor
from openpyxl.drawing.xdr import XDRPoint2D, XDRPositiveSize2D
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils.units import pixels_to_EMU

OUT = os.path.join("samples", "Plantilla_Maestra_Macros.xlsx")

wb = Workbook()
ws = wb.active
ws.title = "Solicitudes"

# ── Título ────────────────────────────────────────────────────────────────
ws["A1"] = "PLANTILLA MAESTRA DE MACROS"
ws["A1"].font = Font(name="Calibri", size=16, bold=True, color="1F4E79")
ws["A2"] = "Hoja de ejemplo — Office Platform"
ws["A2"].font = Font(name="Calibri", size=10, italic=True, color="6C757D")
ws["A3"] = ("Los datos de abajo están desordenados a propósito. "
            "Usá los botones de la derecha para verlos ordenarse.")
ws["A3"].font = Font(name="Calibri", size=9, color="6C757D")

# ── Encabezados (fila 5) ──────────────────────────────────────────────────
headers = ["Código", "Solicitud", "Responsable", "Estado", "Importe", "Fecha"]
for col, name in enumerate(headers, start=1):
    c = ws.cell(row=5, column=col, value=name)
    c.font = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
    c.fill = PatternFill("solid", fgColor="1F4E79")
    c.alignment = Alignment(horizontal="center", vertical="center")

# ── Datos deliberadamente sucios (filas 6..14) ────────────────────────────
# Espacios sobrantes, mayúsculas inconsistentes y dobles espacios: material
# para macroEstandarizarDatos.
rows = [
    ("sol-001", "  compra de  insumos de oficina ", "maria  gonzalez", "pendiente", 1250000.5, datetime.date(2026, 1, 15)),
    ("SOL-002", "mantenimiento DE maquinaria  ", "  Carlos RUIZ", "APROBADO", 8750000.0, datetime.date(2026, 1, 18)),
    ("sol-003 ", "  servicio de transporte", "ana  martinez  ", "vencido", 3400000.75, datetime.date(2025, 12, 2)),
    ("SOL-004", "renovacion  licencias software", "JORGE  perez", "Pendiente", 15600000.0, datetime.date(2026, 2, 3)),
    ("sol-005", "capacitacion  personal planta ", "  lucia Ramirez", "aprobado", 4200000.25, datetime.date(2026, 2, 10)),
    ("SOL-006 ", " auditoria externa  anual", "miguel TORRES ", "VENCIDO", 22000000.0, datetime.date(2025, 11, 20)),
    ("sol-007", "repuestos linea  produccion", "  sandra Lopez", "pendiente", 6890000.4, datetime.date(2026, 2, 14)),
    ("SOL-008", "  consultoria  de calidad ", "andres GOMEZ", "Aprobado", 9150000.0, datetime.date(2026, 2, 20)),
    ("sol-009 ", "actualizacion  equipos computo", "paula  Diaz ", "pendiente", 11300000.6, datetime.date(2026, 3, 1)),
]
for i, row in enumerate(rows, start=6):
    for col, value in enumerate(row, start=1):
        ws.cell(row=i, column=col, value=value)

# Anchos iniciales angostos: se ven mal a propósito hasta correr la macro 1.
for col, w in zip("ABCDEF", [10, 20, 14, 10, 12, 11]):
    ws.column_dimensions[col].width = w
ws.row_dimensions[5].height = 22

# ── Botones ───────────────────────────────────────────────────────────────
# openpyxl no crea autoformas, así que los botones se dibujan como celdas
# combinadas con relleno y borde redondeado visual. El usuario les asigna la
# macro con clic derecho igual que a una forma.

BUTTONS = [
    ("H5:J5", "🔄  Estandarizar datos", "2E7D32", "Quita espacios, normaliza mayúsculas y ajusta columnas"),
    ("H7:J7", "🎨  Aplicar formato", "1F4E79", "Encabezado azul, bordes, moneda y fila de total"),
    ("H9:J9", "🚦  Resaltar alertas", "C62828", "Amarillo lo pendiente, rojo lo vencido, verde lo aprobado"),
]

# Las celdas quedan VACÍAS: encima se inyectan autoformas roundRect reales
# (add_shapes.py). Una celda combinada con color parecería un botón pero no lo
# sería: en OnlyOffice "Asignar macro" solo existe en el menú de una forma.
for rng, label, color, hint in BUTTONS:
    first = rng.split(":")[0]
    row = int("".join(ch for ch in first if ch.isdigit()))
    ws.row_dimensions[row].height = 30
    # Descripción debajo del botón, fuera del área que ocupa la forma
    hint_cell = ws.cell(row=row + 1, column=8, value=hint)
    hint_cell.font = Font(name="Calibri", size=8, color="6C757D")
    ws.merge_cells(start_row=row + 1, start_column=8, end_row=row + 1, end_column=10)
    hint_cell.alignment = Alignment(horizontal="center", vertical="top", wrap_text=True)

for col, w in zip(["H", "I", "J"], [12, 12, 12]):
    ws.column_dimensions[col].width = w

# ── Instrucciones ─────────────────────────────────────────────────────────
ws["H12"] = "CÓMO ACTIVAR LOS BOTONES"
ws["H12"].font = Font(name="Calibri", size=10, bold=True, color="1F4E79")
ws.merge_cells("H12:J12")

steps = [
    "1. Pestaña VISTA → Macros",
    "2. Creá las 3 macros de samples/macros_plantilla.js",
    "3. Clic derecho en cada botón → Asignar macro",
    "4. Listo: hacé clic y mirá qué pasa",
]
for k, step in enumerate(steps, start=13):
    ws.cell(row=k, column=8, value=step).font = Font(name="Calibri", size=9, color="333333")
    ws.merge_cells(start_row=k, start_column=8, end_row=k, end_column=10)

ws.sheet_view.showGridLines = False
ws.freeze_panes = "A6"

os.makedirs("samples", exist_ok=True)
wb.save(OUT)
print("generado:", OUT, os.path.getsize(OUT), "bytes")
