<#
.SYNOPSIS
    Instala los plugins de OnlyOffice que este proyecto monta en Document Server.

.DESCRIPTION
    La imagen onlyoffice/documentserver NO trae ningún plugin, pese a que su
    plugin-list-default.json los declara. Antes se instalaban a mano dentro del
    contenedor, así que desaparecían la primera vez que se recreaba — incluido el
    plugin de Macros que necesita la pestaña Vista.

    docker-compose.yml monta cada plugin desde onlyoffice-plugins\, y este script
    llena esa carpeta. Los plugins NO se versionan: son 28 MB de código de terceros.

    Ejecutar una vez después de clonar el repositorio, y luego `docker compose up -d`.

.PARAMETER Force
    Vuelve a clonar los plugins aunque ya estén instalados.

.EXAMPLE
    .\scripts\install-onlyoffice-plugins.ps1

.EXAMPLE
    .\scripts\install-onlyoffice-plugins.ps1 -Force
#>

[CmdletBinding()]
param([switch] $Force)

$ErrorActionPreference = 'Stop'

$repoRoot  = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $repoRoot 'onlyoffice-plugins'

# Nombre del repositorio -> carpeta GUID que busca el editor.
# El GUID no es decorativo: Document Server resuelve los plugins por el GUID exacto de
# su config.json, y app.js tiene fijo el de Macros para colocar su botón.
$plugins = [ordered]@{
    'plugin-macros'        = '{E6978D28-0441-4BD7-8346-82FAD68BCA3B}'
    'plugin-highlightcode' = '{BE5CBF95-C0AD-4842-B157-AC40FEDD9841}'
    'plugin-photoeditor'   = '{07FD8DFA-DFE0-4089-AL24-0730933CC80A}'
    'plugin-youtube'       = '{38E022EA-AD92-45FC-B22B-49DF39746DB4}'
    'plugin-ocr'           = '{440EBF13-9B19-4BD8-8621-05200E58140B}'
    'plugin-translator'    = '{7327FC95-16DA-41D9-9AF2-0E7F449F6800}'
    'plugin-mendeley'      = '{BE5CBF95-C0AD-4842-B157-AC40FEDD9441}'
    'plugin-thesaurus'     = '{BE5CBF95-C0AD-4842-B157-AC40FEDD9840}'
    'plugin-speech'        = '{D71C2EF0-F15B-47C7-80E9-86D671F9C595}'
    'plugin-drawio'        = '{DB38923B-A8C0-4DE9-8AEE-A61BB5C901A5}'
    'plugin-zotero'        = '{BFC5D5C6-89DE-4168-9565-ABD8D1E48711}'
}

Write-Host ''
Write-Host '  Office Platform - Plugins de OnlyOffice' -ForegroundColor White
Write-Host "  Destino: $targetDir" -ForegroundColor DarkGray
Write-Host ''

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'git no está disponible en el PATH.'
}

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

$installed = 0; $skipped = 0; $failed = 0

foreach ($repo in $plugins.Keys) {
    $guid = $plugins[$repo]
    $dest = Join-Path $targetDir $guid

    if ((Test-Path (Join-Path $dest 'config.json')) -and -not $Force) {
        Write-Host ("  [--] {0,-22} ya instalado" -f $repo) -ForegroundColor DarkGray
        $skipped++
        continue
    }

    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ([System.Guid]::NewGuid().ToString())
    try {
        git clone --depth 1 -q "https://github.com/ONLYOFFICE/$repo.git" (Join-Path $tmp 'src') 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'clone falló' }

        # Verificado, no asumido: un plugin cuyo GUID no coincide con la carpeta donde se
        # monta es ignorado en silencio por el editor, y eso es dificilísimo de diagnosticar.
        $cfg = Get-Content (Join-Path $tmp 'src\config.json') -Raw -ErrorAction Stop
        if ($cfg -notlike "*$guid*") { throw 'el GUID de config.json no coincide' }

        Remove-Item (Join-Path $tmp 'src\.git') -Recurse -Force -ErrorAction SilentlyContinue
        if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
        Move-Item (Join-Path $tmp 'src') $dest

        Write-Host ("  [OK] {0,-22} {1}" -f $repo, $guid) -ForegroundColor Green
        $installed++
    }
    catch {
        Write-Host ("  [!!] {0,-22} {1}" -f $repo, $_.Exception.Message) -ForegroundColor Yellow
        $failed++
    }
    finally {
        Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host "  Instalados: $installed   Ya presentes: $skipped   Fallidos: $failed"
Write-Host ''

if ($failed -gt 0) {
    Write-Host '  Algunos plugins no se instalaron. El editor funciona igual, pero esos' -ForegroundColor Yellow
    Write-Host '  botones no aparecerán en la pestaña Extensiones.' -ForegroundColor Yellow
    Write-Host ''
}

Write-Host '  Siguiente paso:  docker compose up -d onlyoffice' -ForegroundColor Cyan
Write-Host '  El botón Macros aparece en la pestaña VISTA al abrir un documento en edición.' -ForegroundColor DarkGray
Write-Host ''
