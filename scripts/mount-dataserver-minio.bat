@echo off
REM =====================================================================
REM  Office Platform - Mount the MinIO bucket as a Windows drive
REM
REM  Exposes the object storage that backs Office Platform as a normal
REM  drive letter, so existing tools and users can reach the documents
REM  without going through the web interface.
REM
REM  Requires Rclone (https://rclone.org/downloads/) and WinFsp
REM  (https://winfsp.dev/), which Rclone needs to present a drive.
REM
REM  Usage:  mount-dataserver-minio.bat [DRIVE_LETTER]
REM  Example: mount-dataserver-minio.bat P
REM =====================================================================

setlocal enabledelayedexpansion

REM ---- Settings -------------------------------------------------------
REM Point MINIO_ENDPOINT at the server's address when running from a
REM workstation; localhost only works on the machine hosting MinIO.
set "REMOTE_NAME=minio"
set "BUCKET=office-platform"
set "MINIO_ENDPOINT=http://localhost:9000"
set "MINIO_ACCESS_KEY=minioadmin"
set "MINIO_SECRET_KEY=minioadmin"

set "DRIVE=%~1"
if "%DRIVE%"=="" set "DRIVE=P"
set "DRIVE=%DRIVE::=%"

echo.
echo  Office Platform - mounting %BUCKET% on %DRIVE%:
echo  Endpoint: %MINIO_ENDPOINT%
echo.

REM ---- Preconditions --------------------------------------------------
where rclone >nul 2>&1
if errorlevel 1 (
    echo  [ERROR] rclone is not on PATH.
    echo          Install it from https://rclone.org/downloads/ and reopen this terminal.
    exit /b 1
)

if exist "%DRIVE%:\" (
    echo  [ERROR] Drive %DRIVE%: is already in use. Pass a free letter, for example:
    echo          %~nx0 R
    exit /b 1
)

REM ---- Remote definition ----------------------------------------------
REM Written with `rclone config create`, which updates rclone.conf in
REM place instead of overwriting remotes that already exist.
echo  Configuring the "%REMOTE_NAME%" remote...
rclone config create "%REMOTE_NAME%" s3 ^
    provider=Minio ^
    access_key_id="%MINIO_ACCESS_KEY%" ^
    secret_access_key="%MINIO_SECRET_KEY%" ^
    endpoint="%MINIO_ENDPOINT%" ^
    acl=private ^
    --non-interactive >nul
if errorlevel 1 (
    echo  [ERROR] Could not write the rclone remote.
    exit /b 1
)

REM ---- Connectivity check --------------------------------------------
REM Done before mounting: a failed mount leaves a drive letter that looks
REM present but answers errors on every access, which is harder to diagnose.
echo  Checking the bucket is reachable...
rclone lsd "%REMOTE_NAME%:%BUCKET%" >nul 2>&1
if errorlevel 1 (
    echo  [ERROR] Cannot reach %REMOTE_NAME%:%BUCKET% at %MINIO_ENDPOINT%.
    echo          Check that MinIO is running, that the endpoint is reachable
    echo          from this machine, and that the credentials are correct.
    exit /b 1
)

REM ---- Mount ----------------------------------------------------------
REM --vfs-cache-mode writes is required for Office applications: they open
REM documents for random read/write, which a cacheless mount cannot serve.
echo  Mounting on %DRIVE%: ...
start "" /b rclone mount "%REMOTE_NAME%:%BUCKET%" "%DRIVE%:" ^
    --vfs-cache-mode writes ^
    --vfs-cache-max-age 24h ^
    --dir-cache-time 30s ^
    --volname "Office Platform" ^
    --log-level INFO ^
    --log-file "%TEMP%\rclone-office-platform.log"

REM Give the mount a moment to appear before reporting success.
timeout /t 5 /nobreak >nul

if exist "%DRIVE%:\" (
    echo.
    echo  [OK] %BUCKET% is available on %DRIVE%:
    echo       Log: %TEMP%\rclone-office-platform.log
    echo       To unmount, close the rclone process ^(taskkill /IM rclone.exe^).
    echo.
) else (
    echo.
    echo  [ERROR] The drive did not appear. Check the log:
    echo          %TEMP%\rclone-office-platform.log
    echo          The usual cause is WinFsp not being installed: https://winfsp.dev/
    echo.
    exit /b 1
)

endlocal
