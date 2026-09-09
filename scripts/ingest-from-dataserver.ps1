<#
.SYNOPSIS
    Migra una carpeta del DataServer (G:\ o un recurso de red) hacia Office Platform.

.DESCRIPTION
    Recorre un arbol de carpetas de Windows y lo reproduce en la plataforma via API REST,
    conservando la jerarquia y la autoria.

    El script es IDEMPOTENTE en carpetas: si una carpeta ya existe en el destino la reutiliza en
    lugar de duplicarla, asi que una ingesta interrumpida se puede reanudar sin ensuciar el arbol.
    Los archivos NO se deduplican: subir dos veces el mismo archivo crea dos entradas. Es
    deliberado, porque decidir que dos archivos con el mismo nombre son "el mismo" es una decision
    de negocio que este script no puede tomar por nadie. Usa -WhatIf antes de cada corrida real.

.PARAMETER Source
    Carpeta de origen. Puede ser una unidad mapeada (G:\Documentos) o una ruta UNC
    (\\SERVER116\Documentos).

.PARAMETER Server
    URL base de Office Platform. Ejemplo: http://127.0.0.1:8080

.PARAMETER ApiKey
    API key del proyecto destino. Se envia en la cabecera X-Api-Key.

.PARAMETER UserId
    Cedula que quedara como autora de lo ingestado. Aparece en la trazabilidad de cada archivo.

.PARAMETER UserName
    Nombre visible de esa persona.

.PARAMETER Scope
    'shared' (por defecto) deja el contenido en el espacio compartido del proyecto.
    'private' lo deja en "Mis Archivos" de UserId.

.PARAMETER ParentFolderId
    Carpeta de la plataforma bajo la cual colgar el arbol. Sin este parametro cuelga de la raiz.

.PARAMETER MaxFileSizeMB
    Archivos mas grandes se omiten con aviso. Por defecto 20, que es el limite del servidor.

.PARAMETER WhatIf
    Muestra exactamente que se crearia y que se subiria, sin escribir nada.

.EXAMPLE
    .\ingest-from-dataserver.ps1 -Source "G:\Calidad" -Server "http://127.0.0.1:8080" `
        -ApiKey "opk_..." -UserId "1004356866" -UserName "Ana Perez" -WhatIf

.EXAMPLE
    .\ingest-from-dataserver.ps1 -Source "\\SERVER116\Calidad" -Server "http://127.0.0.1:8080" `
        -ApiKey "opk_..." -UserId "1004356866" -UserName "Ana Perez"
#>

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)] [string] $Source,
    [Parameter(Mandatory = $true)] [string] $Server,
    [Parameter(Mandatory = $true)] [string] $ApiKey,
    [Parameter(Mandatory = $true)] [string] $UserId,
    [Parameter(Mandatory = $true)] [string] $UserName,
    [ValidateSet('shared', 'private')] [string] $Scope = 'shared',
    [long] $ParentFolderId = 0,
    [int] $MaxFileSizeMB = 20
)

$ErrorActionPreference = 'Stop'

# Extensiones que el servidor acepta. Mantener alineado con ALLOWED_EXTENSIONS del widget y con
# allowed-mime-types: subir algo fuera de esta lista devuelve 400 y solo genera ruido en el log.
$AllowedExtensions = @(
    '.docx', '.doc', '.docm', '.dotm',
    '.xlsx', '.xls', '.xlsm', '.xltm', '.xlsb',
    '.pptx', '.ppt', '.pptm', '.potm',
    '.odt', '.ods', '.odp', '.pdf'
)

# El servidor exige que la extension y el Content-Type declarado concuerden: enviar
# application/octet-stream para un .docx se rechaza con "la extension no corresponde al tipo
# declarado". Este mapa es el espejo de MIME_TYPE_BY_EXTENSION en MimeUtils.java.
$MimeByExtension = @{
    '.pdf'  = 'application/pdf'
    '.doc'  = 'application/msword'
    '.docx' = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    '.docm' = 'application/vnd.ms-word.document.macroEnabled.12'
    '.dotm' = 'application/vnd.ms-word.template.macroEnabled.12'
    '.xls'  = 'application/vnd.ms-excel'
    '.xlsx' = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    '.xlsm' = 'application/vnd.ms-excel.sheet.macroEnabled.12'
    '.xltm' = 'application/vnd.ms-excel.template.macroEnabled.12'
    '.xlsb' = 'application/vnd.ms-excel.sheet.binary.macroEnabled.12'
    '.ppt'  = 'application/vnd.ms-powerpoint'
    '.pptx' = 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
    '.pptm' = 'application/vnd.ms-powerpoint.presentation.macroEnabled.12'
    '.potm' = 'application/vnd.ms-powerpoint.template.macroEnabled.12'
    '.odt'  = 'application/vnd.oasis.opendocument.text'
    '.ods'  = 'application/vnd.oasis.opendocument.spreadsheet'
    '.odp'  = 'application/vnd.oasis.opendocument.presentation'
}

$Server = $Server.TrimEnd('/')
$Headers = @{ 'X-Api-Key' = $ApiKey }
$MaxBytes = $MaxFileSizeMB * 1MB

$Stats = [ordered]@{
    FoldersCreated = 0
    FoldersReused  = 0
    FilesUploaded  = 0
    FilesSkipped   = 0
    FilesFailed    = 0
    BytesUploaded  = 0
}

function Get-IdentityQuery {
    "userId=$([uri]::EscapeDataString($UserId))&userName=$([uri]::EscapeDataString($UserName))&scope=$Scope"
}

<#
    Devuelve el id de una carpeta en la plataforma, creandola si hace falta.

    Primero lista lo que ya hay bajo el padre y reutiliza la coincidencia por nombre. Sin esto,
    reanudar una ingesta interrumpida duplicaria todo el arbol.
#>
function Resolve-RemoteFolder {
    param([string] $Name, [long] $ParentId)

    $query = Get-IdentityQuery
    if ($ParentId -gt 0) { $query += "&parentId=$ParentId" }

    try {
        $listing = Invoke-RestMethod -Method Get -Uri "$Server/api/folders?$query" -Headers $Headers
        $existing = $listing.data | Where-Object { $_.name -eq $Name } | Select-Object -First 1
        if ($existing) {
            $Stats.FoldersReused++
            return [long] $existing.id
        }
    } catch {
        Write-Warning "No se pudo listar carpetas bajo $ParentId : $($_.Exception.Message)"
    }

    if ($PSCmdlet.ShouldProcess("$Name (bajo carpeta $ParentId)", 'Crear carpeta')) {
        $body = @{ name = $Name }
        if ($ParentId -gt 0) { $body.parentId = $ParentId }
        $created = Invoke-RestMethod -Method Post -Uri "$Server/api/folders?$(Get-IdentityQuery)" `
            -Headers $Headers -ContentType 'application/json' `
            -Body ($body | ConvertTo-Json -Compress)
        $Stats.FoldersCreated++
        return [long] $created.data.id
    }

    # En -WhatIf no hay id real. Se devuelve un negativo para que el recorrido siga mostrando el
    # arbol completo en vez de cortarse en el primer nivel.
    $Stats.FoldersCreated++
    return -1
}

<#
    Sube un archivo con HttpClient de .NET en vez de Invoke-RestMethod.

    Dos motivos, los dos descubiertos corriendo el script de verdad y no leyendolo:

    1. Invoke-RestMethod -Form solo existe desde PowerShell 7, y las estaciones corren Windows
       PowerShell 5.1. Alli falla con "no se encuentra ningun parametro que coincida".
    2. Armar el multipart a mano y pasarlo como -Body tampoco alcanza: PowerShell recodifica el
       cuerpo y el servidor responde "Required part 'file' is not present". Los bytes del binario
       no sobreviven ese viaje.

    HttpClient construye el multipart el mismo y envia los bytes intactos, en 5.1 y en 7.
#>
function Invoke-FileUpload {
    param([System.IO.FileInfo] $File, [string] $Uri, [string] $MetaJson, [string] $Key, [string] $Mime)

    Add-Type -AssemblyName System.Net.Http

    $client = New-Object System.Net.Http.HttpClient
    try {
        $client.DefaultRequestHeaders.Add('X-Api-Key', $Key)

        $content = New-Object System.Net.Http.MultipartFormDataContent

        $meta = New-Object System.Net.Http.StringContent($MetaJson, [System.Text.Encoding]::UTF8, 'application/json')
        $content.Add($meta, 'request')

        $bytes = [System.IO.File]::ReadAllBytes($File.FullName)
        $fileContent = New-Object System.Net.Http.ByteArrayContent(, $bytes)
        $fileContent.Headers.ContentType =
            [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($Mime)
        $content.Add($fileContent, 'file', $File.Name)

        $response = $client.PostAsync($Uri, $content).GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            $detail = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            throw "HTTP $([int] $response.StatusCode): $detail"
        }
    } finally {
        $client.Dispose()
    }
}

function Send-RemoteFile {
    param([System.IO.FileInfo] $File, [long] $FolderId)

    if ($AllowedExtensions -notcontains $File.Extension.ToLower()) {
        Write-Verbose "Omitido (extension no admitida): $($File.FullName)"
        $Stats.FilesSkipped++
        return
    }
    if ($File.Length -gt $MaxBytes) {
        Write-Warning "Omitido (supera $MaxFileSizeMB MB): $($File.FullName)"
        $Stats.FilesSkipped++
        return
    }
    if ($File.Length -eq 0) {
        Write-Verbose "Omitido (vacio): $($File.FullName)"
        $Stats.FilesSkipped++
        return
    }

    if (-not $PSCmdlet.ShouldProcess($File.FullName, "Subir a carpeta $FolderId")) {
        $Stats.FilesUploaded++
        $Stats.BytesUploaded += $File.Length
        return
    }

    $query = Get-IdentityQuery
    if ($FolderId -gt 0) { $query += "&folderId=$FolderId" }

    try {
        $meta = @{ originalFileName = $File.Name; description = "Ingestado desde $($File.DirectoryName)" }
        Invoke-FileUpload -File $File -Uri "$Server/api/files/upload?$query" `
            -MetaJson ($meta | ConvertTo-Json -Compress) -Key $ApiKey `
            -Mime $MimeByExtension[$File.Extension.ToLower()]
        $Stats.FilesUploaded++
        $Stats.BytesUploaded += $File.Length
        Write-Verbose "Subido: $($File.FullName)"
    } catch {
        # Un archivo que falla no puede detener la migracion completa: se anota y se sigue.
        Write-Warning "Fallo al subir $($File.FullName): $($_.Exception.Message)"
        $Stats.FilesFailed++
    }
}

function Copy-Tree {
    param([string] $LocalPath, [long] $RemoteParentId, [int] $Depth = 0)

    $indent = ' ' * ($Depth * 2)
    foreach ($file in Get-ChildItem -LiteralPath $LocalPath -File -ErrorAction SilentlyContinue) {
        Write-Host "$indent  - $($file.Name)"
        Send-RemoteFile -File $file -FolderId $RemoteParentId
    }
    foreach ($dir in Get-ChildItem -LiteralPath $LocalPath -Directory -ErrorAction SilentlyContinue) {
        Write-Host "$indent+ $($dir.Name)/"
        $childId = Resolve-RemoteFolder -Name $dir.Name -ParentId $RemoteParentId
        Copy-Tree -LocalPath $dir.FullName -RemoteParentId $childId -Depth ($Depth + 1)
    }
}

# ── Comprobaciones previas ───────────────────────────────────────────────────
if (-not (Test-Path -LiteralPath $Source)) {
    throw "El origen no existe o no es alcanzable: $Source"
}
try {
    Invoke-RestMethod -Method Get -Uri "$Server/api/folders?$(Get-IdentityQuery)" -Headers $Headers | Out-Null
} catch {
    throw "No se pudo contactar Office Platform en $Server con esa API key: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "Origen  : $Source"
Write-Host "Destino : $Server (scope=$Scope, autor=$UserName)"
if ($WhatIfPreference) {
    Write-Host "MODO SIMULACION: no se escribe nada." -ForegroundColor Yellow
}
Write-Host ""

Copy-Tree -LocalPath $Source -RemoteParentId $ParentFolderId

Write-Host ""
Write-Host "--- Resumen -----------------------------"
Write-Host "  Carpetas creadas    : $($Stats.FoldersCreated)"
Write-Host "  Carpetas reutilizadas: $($Stats.FoldersReused)"
Write-Host "  Archivos subidos    : $($Stats.FilesUploaded)"
Write-Host "  Archivos omitidos   : $($Stats.FilesSkipped)"
Write-Host "  Archivos con error  : $($Stats.FilesFailed)"
Write-Host "  Volumen             : $([math]::Round($Stats.BytesUploaded / 1MB, 2)) MB"
if ($WhatIfPreference) {
    Write-Host ""
    Write-Host "Nada de esto se ejecuto. Volve a correrlo sin -WhatIf para migrar." -ForegroundColor Yellow
}
