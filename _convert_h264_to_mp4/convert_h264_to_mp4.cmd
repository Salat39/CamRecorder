@echo off
setlocal EnableExtensions EnableDelayedExpansion
title H264 to MP4 converter

set "SCRIPT_DIR=%~dp0"
set "FFMPEG_EXE="
set "PYTHON_EXE="
set "PYTHON_ARGS="
set "EXIT_CODE=0"

pushd "%SCRIPT_DIR%" >nul

echo.
echo ==========================================
echo   H264 to MP4 converter
echo ==========================================
echo   Folder: "%SCRIPT_DIR%"
echo.

call :resolveFfmpeg
if errorlevel 1 (
    set "EXIT_CODE=1"
    goto :finalize
)

echo [STEP] Searching for .h264 files...
dir /b "*.h264" >nul 2>&1
if errorlevel 1 (
    echo [INFO] No .h264 files found in "%SCRIPT_DIR%"
    goto :finalize
)

echo [STEP] Starting conversion...
echo.

for %%F in (*.h264) do call :convertOne "%%~fF" "%%~nF" "%%~dpnF.mp4" "%%~nxF"

echo.
echo [DONE] All files processed.
goto :finalize

:resolveFfmpeg
echo [STEP] Looking for ffmpeg...

if exist "%SCRIPT_DIR%ffmpeg.exe" (
    set "FFMPEG_EXE=%SCRIPT_DIR%ffmpeg.exe"
    echo [INFO] Using local ffmpeg: "%FFMPEG_EXE%"
    exit /b 0
)

for /f "delims=" %%I in ('where ffmpeg 2^>nul') do (
    set "FFMPEG_EXE=%%~fI"
    echo [INFO] Using ffmpeg from PATH: "%FFMPEG_EXE%"
    exit /b 0
)

for /f "delims=" %%I in ('where /r "%SCRIPT_DIR%tools" ffmpeg.exe 2^>nul') do (
    set "FFMPEG_EXE=%%~fI"
    echo [INFO] Using previously downloaded ffmpeg: "%FFMPEG_EXE%"
    exit /b 0
)

echo [INFO] ffmpeg not found.
echo [STEP] Downloading ffmpeg...
call :downloadFfmpeg
if errorlevel 1 exit /b 1

exit /b 0

:resolvePython
if defined PYTHON_EXE exit /b 0

echo [STEP] Looking for Python...

if exist "%SCRIPT_DIR%python.exe" (
    set "PYTHON_EXE=%SCRIPT_DIR%python.exe"
    set "PYTHON_ARGS="
    echo [INFO] Using local python: "%PYTHON_EXE%"
    exit /b 0
)

for /f "delims=" %%I in ('where py 2^>nul') do (
    set "PYTHON_EXE=%%~fI"
    set "PYTHON_ARGS=-3"
    echo [INFO] Using py launcher: "%PYTHON_EXE%" %PYTHON_ARGS%
    exit /b 0
)

for /f "delims=" %%I in ('where python 2^>nul') do (
    set "PYTHON_EXE=%%~fI"
    set "PYTHON_ARGS="
    echo [INFO] Using python from PATH: "%PYTHON_EXE%"
    exit /b 0
)

echo [ERROR] Python 3 not found.
exit /b 1

:downloadFfmpeg
set "FFMPEG_URL=https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
set "FFMPEG_SHA_URL=https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256"
set "DOWNLOAD_ROOT=%TEMP%\ffmpeg_auto_%RANDOM%%RANDOM%"
set "ZIP_FILE=%DOWNLOAD_ROOT%\ffmpeg-release-essentials.zip"
set "SHA_FILE=%DOWNLOAD_ROOT%\ffmpeg-release-essentials.zip.sha256"
set "EXTRACT_ROOT=%SCRIPT_DIR%tools\ffmpeg"

echo [INFO] Creating temp directory...
mkdir "%DOWNLOAD_ROOT%" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Failed to create temp directory.
    exit /b 1
)

echo [INFO] Downloading ffmpeg archive...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Invoke-WebRequest -UseBasicParsing -Uri '%FFMPEG_URL%' -OutFile '%ZIP_FILE%'"
if errorlevel 1 (
    echo [ERROR] Failed to download FFmpeg archive.
    rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
    exit /b 1
)

echo [INFO] Downloading checksum...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Invoke-WebRequest -UseBasicParsing -Uri '%FFMPEG_SHA_URL%' -OutFile '%SHA_FILE%'"
if errorlevel 1 (
    echo [ERROR] Failed to download FFmpeg checksum.
    rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
    exit /b 1
)

echo [STEP] Verifying SHA256...
set "EXPECTED_HASH="
set "ACTUAL_HASH="

for /f "usebackq tokens=1" %%H in ("%SHA_FILE%") do (
    if not defined EXPECTED_HASH set "EXPECTED_HASH=%%H"
)

for /f "usebackq delims=" %%H in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; (Get-FileHash -LiteralPath '%ZIP_FILE%' -Algorithm SHA256).Hash.ToLower()"`) do (
    if not defined ACTUAL_HASH set "ACTUAL_HASH=%%H"
)

if not defined EXPECTED_HASH (
    echo [ERROR] Failed to read expected SHA256.
    rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
    exit /b 1
)

if not defined ACTUAL_HASH (
    echo [ERROR] Failed to calculate actual SHA256.
    rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
    exit /b 1
)

set "EXPECTED_HASH=%EXPECTED_HASH: =%"
set "ACTUAL_HASH=%ACTUAL_HASH: =%"

if /I not "%EXPECTED_HASH%"=="%ACTUAL_HASH%" (
    echo [ERROR] SHA256 mismatch.
    echo         expected: %EXPECTED_HASH%
    echo         actual:   %ACTUAL_HASH%
    rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
    exit /b 1
)

echo [INFO] SHA256 verified.

if exist "%EXTRACT_ROOT%" (
    echo [INFO] Removing old downloaded ffmpeg...
    rmdir /s /q "%EXTRACT_ROOT%" >nul 2>&1
)

echo [STEP] Extracting ffmpeg...
mkdir "%EXTRACT_ROOT%" >nul 2>&1

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%EXTRACT_ROOT%' -Force"
if errorlevel 1 (
    echo [ERROR] Failed to extract FFmpeg archive.
    rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
    exit /b 1
)

for /f "delims=" %%I in ('where /r "%EXTRACT_ROOT%" ffmpeg.exe 2^>nul') do (
    set "FFMPEG_EXE=%%~fI"
    goto :downloadOk
)

echo [ERROR] ffmpeg.exe not found after extraction.
rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
exit /b 1

:downloadOk
echo [INFO] Downloaded ffmpeg: "%FFMPEG_EXE%"
echo [INFO] Cleaning temporary files...
rmdir /s /q "%DOWNLOAD_ROOT%" >nul 2>&1
exit /b 0

:runIndexedConverter
set "INPUT=%~1"
set "OUTPUT=%~2"
set "HELPER=%SCRIPT_DIR%convert_indexed_h264.py"

if not exist "%HELPER%" (
    echo [ERROR] Missing helper script: "%HELPER%"
    exit /b 1
)

call :resolvePython
if errorlevel 1 exit /b 1

if defined PYTHON_ARGS (
    "%PYTHON_EXE%" %PYTHON_ARGS% "%HELPER%" --ffmpeg "%FFMPEG_EXE%" --input "%INPUT%" --output "%OUTPUT%"
) else (
    "%PYTHON_EXE%" "%HELPER%" --ffmpeg "%FFMPEG_EXE%" --input "%INPUT%" --output "%OUTPUT%"
)
exit /b %ERRORLEVEL%

:convertOne
set "INPUT=%~1"
set "NAME_ONLY=%~2"
set "OUTPUT=%~3"
set "FILE_NAME=%~4"
set "INDEX_FILE=%INPUT%.idx"

set "FILE_TIME="
set "FILE_DATE="
set "DUMMY_DURATION="
set "FPS="
set "ALIAS="

for /f "tokens=1-5 delims=_" %%A in ("%NAME_ONLY%") do (
    set "FILE_TIME=%%A"
    set "FILE_DATE=%%B"
    set "DUMMY_DURATION=%%C"
    set "FPS=%%D"
    set "ALIAS=%%E"
)

if exist "%OUTPUT%" (
    echo [SKIP] %~n4.mp4 - output already exists
    exit /b 0
)

echo [CONVERT] %FILE_NAME%

if exist "%INDEX_FILE%" (
    echo           mode=indexed
    call :runIndexedConverter "%INPUT%" "%OUTPUT%"
    if errorlevel 1 (
        echo [ERROR] Failed to convert indexed file %FILE_NAME%
        set "EXIT_CODE=1"
    ) else (
        echo [OK] %~n4.mp4 created
    )
    echo.
    exit /b 0
)

if not defined FPS (
    echo [SKIP] %FILE_NAME% - unexpected filename format
    echo        expected: HH-mm-ss_dd.MM.yy_duration_fps_alias.h264
    exit /b 0
)

set /a FPS_CHECK=%FPS%+0 >nul 2>&1
if errorlevel 1 (
    echo [SKIP] %FILE_NAME% - fps is not numeric: %FPS%
    exit /b 0
)

echo           mode=legacy fps=%FPS% alias=%ALIAS%
"%FFMPEG_EXE%" -hide_banner -loglevel warning -fflags +genpts -f h264 -framerate %FPS% -i "%INPUT%" -c:v copy -movflags +faststart "%OUTPUT%"

if errorlevel 1 (
    echo [ERROR] Failed to convert %FILE_NAME%
    set "EXIT_CODE=1"
) else (
    echo [OK] %~n4.mp4 created
)

echo.
exit /b 0

:finalize
popd >nul
endlocal & exit /b %EXIT_CODE%
