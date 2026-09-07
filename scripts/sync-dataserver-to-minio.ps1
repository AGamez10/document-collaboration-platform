<#
.SYNOPSIS
    Synchronises a DataServer share into the Office Platform MinIO bucket.

.DESCRIPTION
    Copies a folder tree from a file server into object storage using Rclone, so
    documents that live on a network share become reachable through Office Platform.

    Defaults to `rclone copy`, which only adds and updates: nothing in the bucket is
    ever deleted. Mirroring — where files removed from the source are also removed
    from the bucket — has to be asked for explicitly with -Mirror, because it can
    delete data that only exists in the bucket.

    Always run it once with -WhatIf first: it prints exactly what would be
    transferred without touching anything.

.PARAMETER SourcePath
    Folder to read from, for example \\dataserver\Documentos\Calidad

.PARAMETER Prefix
    Destination folder inside the bucket. Defaults to the source folder's name.

.PARAMETER Mirror
    Use `rclone sync` instead of `rclone copy`: files missing from the source are
    DELETED from the bucket. Destructive — combine with -WhatIf before committing.

.PARAMETER WhatIf
    Dry run. Reports the planned transfers and changes nothing.

.EXAMPLE
    .\sync-dataserver-to-minio.ps1 -SourcePath \\dataserver\Documentos\Calidad -WhatIf

.EXAMPLE
    .\sync-dataserver-to-minio.ps1 -SourcePath D:\Documentos -Prefix documentos
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SourcePath,

    [string] $Prefix,

    [string] $RemoteName = 'minio',

    [string] $Bucket = 'office-platform',

    [string] $Endpoint = 'http://localhost:9000',

    [string] $AccessKey = 'minioadmin',

    [string] $SecretKey = 'minioadmin',

    [switch] $Mirror,

    [switch] $WhatIf
)

$ErrorActionPreference = 'Stop'

function Write-Step { param([string] $Message) Write-Host "  $Message" -ForegroundColor Cyan }
function Write-Ok   { param([string] $Message) Write-Host "  [OK] $Message" -ForegroundColor Green }
function Write-Warn { param([string] $Message) Write-Host "  [!]  $Message" -ForegroundColor Yellow }

Write-Host ''
Write-Host '  Office Platform - DataServer to MinIO' -ForegroundColor White
Write-Host ''

# --- Preconditions --------------------------------------------------------
if (-not (Get-Command rclone -ErrorAction SilentlyContinue)) {
    throw 'rclone is not on PATH. Install it from https://rclone.org/downloads/'
}

if (-not (Test-Path -LiteralPath $SourcePath)) {
    throw "The source path does not exist or is unreachable: $SourcePath"
}

if (-not $Prefix) {
    $Prefix = Split-Path -Leaf $SourcePath.TrimEnd('\')
}
$destination = "${RemoteName}:${Bucket}/${Prefix}"

# --- Remote definition ----------------------------------------------------
# `config create` updates rclone.conf in place, leaving other remotes alone.
Write-Step "Configuring the '$RemoteName' remote..."
rclone config create $RemoteName s3 `
    provider=Minio `
    access_key_id=$AccessKey `
    secret_access_key=$SecretKey `
    endpoint=$Endpoint `
    acl=private `
    --non-interactive | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not write the rclone remote.' }

# --- Connectivity ---------------------------------------------------------
# Checked before transferring so a wrong endpoint fails immediately with a clear
# message instead of part-way through a long copy.
Write-Step "Checking $RemoteName`:$Bucket is reachable..."
rclone lsd "${RemoteName}:${Bucket}" *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Cannot reach ${RemoteName}:${Bucket} at $Endpoint. Check that MinIO is running and the credentials are correct."
}
Write-Ok 'Bucket reachable.'

# --- Transfer -------------------------------------------------------------
$verb = if ($Mirror) { 'sync' } else { 'copy' }

if ($Mirror -and -not $WhatIf) {
    Write-Warn 'MIRROR mode: files absent from the source will be DELETED from the bucket.'
    $answer = Read-Host '  Type MIRROR to confirm'
    if ($answer -ne 'MIRROR') {
        Write-Host '  Cancelled.' -ForegroundColor Yellow
        exit 0
    }
}

$arguments = @(
    $verb
    $SourcePath
    $destination
    '--progress'
    '--transfers', '8'
    '--checkers', '16'
    '--retries', '3'
    '--log-level', 'INFO'
)
if ($WhatIf) { $arguments += '--dry-run' }

Write-Host ''
Write-Step "$($verb.ToUpper()): $SourcePath  ->  $destination"
if ($WhatIf) { Write-Warn 'Dry run: nothing will be written.' }
Write-Host ''

rclone @arguments

if ($LASTEXITCODE -ne 0) {
    throw "rclone finished with errors (exit code $LASTEXITCODE). Nothing was left in an unknown state: rerun to resume."
}

Write-Host ''
Write-Ok 'Synchronisation finished.'
Write-Host ''
Write-Host '  Note: this copies the binaries into the bucket. Files placed there directly' -ForegroundColor DarkGray
Write-Host '  are not registered in the Office Platform database and will not appear in' -ForegroundColor DarkGray
Write-Host '  the widget; upload them through the API for that.' -ForegroundColor DarkGray
Write-Host ''
