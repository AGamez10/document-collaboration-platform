"""Inyecta autoformas redondeadas reales en Plantilla_Maestra_Macros.xlsx.

openpyxl no sabe crear autoformas, y una celda combinada con color NO sirve:
en OnlyOffice la opción "Asignar macro" existe únicamente en el menú contextual
de una FORMA. Sin forma real, el botón es decorativo.

Se post-procesa el ZIP del xlsx añadiendo xl/drawings/drawing1.xml con tres
shapes roundRect, su relación desde la hoja, y el content-type correspondiente.
"""
import os
import re
import shutil
import zipfile

SRC = os.path.join("samples", "Plantilla_Maestra_Macros.xlsx")
TMP = SRC + ".tmp"

# (texto, color de relleno, fila ancla) — anclados por celda para que acompañen el layout
BUTTONS = [
    ("\U0001F504  Estandarizar datos", "2E7D32", 4),   # fila 5 (0-based)
    ("\U0001F3A8  Aplicar formato", "1F4E79", 6),      # fila 7
    ("\U0001F6A6  Resaltar alertas", "C62828", 8),     # fila 9
]

COL_FROM, COL_TO = 7, 10  # columnas H..J (0-based)


def shape_xml(idx, text, color, row):
    """Un twoCellAnchor con una autoforma roundRect y su texto centrado."""
    return f"""<xdr:twoCellAnchor editAs="oneCell">
<xdr:from><xdr:col>{COL_FROM}</xdr:col><xdr:colOff>38100</xdr:colOff><xdr:row>{row}</xdr:row><xdr:rowOff>19050</xdr:rowOff></xdr:from>
<xdr:to><xdr:col>{COL_TO}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>{row + 1}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>
<xdr:sp macro="" textlink="">
<xdr:nvSpPr>
<xdr:cNvPr id="{idx}" name="Boton{idx}"/>
<xdr:cNvSpPr/>
</xdr:nvSpPr>
<xdr:spPr>
<a:xfrm><a:off x="0" y="0"/><a:ext cx="2286000" cy="371475"/></a:xfrm>
<a:prstGeom prst="roundRect"><a:avLst><a:gd name="adj" fmla="val 22000"/></a:avLst></a:prstGeom>
<a:solidFill><a:srgbClr val="{color}"/></a:solidFill>
<a:ln w="0"><a:noFill/></a:ln>
<a:effectLst><a:outerShdw blurRad="38100" dist="19050" dir="5400000" algn="ctr" rotWithShape="0"><a:srgbClr val="000000"><a:alpha val="18000"/></a:srgbClr></a:outerShdw></a:effectLst>
</xdr:spPr>
<xdr:style>
<a:lnRef idx="0"><a:scrgbClr r="0" g="0" b="0"/></a:lnRef>
<a:fillRef idx="1"><a:scrgbClr r="0" g="0" b="0"/></a:fillRef>
<a:effectRef idx="0"><a:scrgbClr r="0" g="0" b="0"/></a:effectRef>
<a:fontRef idx="minor"><a:schemeClr val="lt1"/></a:fontRef>
</xdr:style>
<xdr:txBody>
<a:bodyPr vertOverflow="clip" horzOverflow="clip" rtlCol="0" anchor="ctr"/>
<a:lstStyle/>
<a:p><a:pPr algn="ctr"/><a:r>
<a:rPr lang="es-CO" sz="1100" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:latin typeface="Calibri"/></a:rPr>
<a:t>{text}</a:t>
</a:r></a:p>
</xdr:txBody>
</xdr:sp>
<xdr:clientData/>
</xdr:twoCellAnchor>"""


drawing = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" '
    'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
    + "".join(shape_xml(i + 2, t, c, r) for i, (t, c, r) in enumerate(BUTTONS))
    + "</xdr:wsDr>"
)

sheet_rels = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    '<Relationship Id="rIdDrawing1" '
    'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" '
    'Target="../drawings/drawing1.xml"/>'
    "</Relationships>"
)

src = zipfile.ZipFile(SRC)
names = src.namelist()
out = zipfile.ZipFile(TMP, "w", zipfile.ZIP_DEFLATED)

for name in names:
    data = src.read(name)

    if name == "xl/worksheets/sheet1.xml":
        xml = data.decode("utf-8")
        # <drawing/> debe ir al final, después de los elementos que el esquema exige antes.
        if "<drawing " not in xml:
            xml = xml.replace("</worksheet>", '<drawing r:id="rIdDrawing1"/></worksheet>')
            # El prefijo r: tiene que estar declarado en la raíz.
            if 'xmlns:r=' not in xml.split(">", 2)[1]:
                xml = re.sub(
                    r"(<worksheet\b[^>]*?)>",
                    r'\1 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">',
                    xml, count=1)
        data = xml.encode("utf-8")

    elif name == "[Content_Types].xml":
        xml = data.decode("utf-8")
        if "drawing1.xml" not in xml:
            xml = xml.replace(
                "</Types>",
                '<Override PartName="/xl/drawings/drawing1.xml" '
                'ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/></Types>')
        data = xml.encode("utf-8")

    elif name == "xl/worksheets/_rels/sheet1.xml.rels":
        continue  # se reescribe abajo

    out.writestr(name, data)

out.writestr("xl/worksheets/_rels/sheet1.xml.rels", sheet_rels)
out.writestr("xl/drawings/drawing1.xml", drawing)
out.close()
src.close()

shutil.move(TMP, SRC)
print("formas inyectadas en", SRC, os.path.getsize(SRC), "bytes")
