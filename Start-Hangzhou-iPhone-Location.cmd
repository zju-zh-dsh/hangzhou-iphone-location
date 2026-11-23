@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Hangzhou iPhone Location
set "SOURCE=%~dp0"

if /i "%PROCESSOR_ARCHITECTURE%"=="AMD64" goto platform_supported
if /i "%PROCESSOR_ARCHITEW6432%"=="AMD64" goto platform_supported
echo This release requires a 64-bit x64 edition of Windows 10 or Windows 11.
goto failed

:platform_supported
if not exist "%SOURCE%server.py" goto missing_files
if not exist "%SOURCE%requirements.lock" goto missing_files
set "RUNTIME=%LOCALAPPDATA%\Hangzhou-iPhone-Location"
if defined HANGZHOU_LOCATION_RUNTIME set "RUNTIME=%HANGZHOU_LOCATION_RUNTIME%"
set "UV_CACHE_DIR=%RUNTIME%\cache"
set "UV_PYTHON_INSTALL_DIR=%RUNTIME%\python"
set "UV_NO_CONFIG=1"
set "UV_NO_PROGRESS=1"
set "UV_ASSET=uv-x86_64-pc-windows-msvc"
set "UV_TOOL=%RUNTIME%\uv-0.13.9-x64.exe"
set "VENV=%RUNTIME%\venv-3.13.18-pmd11.63.1"
set "APP_PYTHON=%VENV%\Scripts\python.exe"
if not exist "%RUNTIME%\" (
    mkdir "%RUNTIME%" || goto setup_failed
)
if exist "%UV_TOOL%" goto setup_python

echo Downloading the verified runtime installer on first launch...
set "DOWNLOAD_DIR=%TEMP%\Hangzhou-Location-%RANDOM%-%RANDOM%-%RANDOM%"
mkdir "%DOWNLOAD_DIR%" || goto setup_failed
set "HZ_UV_ZIP=%DOWNLOAD_DIR%\%UV_ASSET%.zip"
set "HZ_UV_EXTRACT=%DOWNLOAD_DIR%\uv"
set "HZ_UV_SHA=edd61f7f5728a56cc62455386661860bdfc7bab65872f39b0c25bdcc1d8e1beb"
"%SystemRoot%\System32\curl.exe" -fLsS --proto "=https" --proto-redir "=https" --connect-timeout 12 --max-time 150 --retry 3 "https://github.com/astral-sh/uv/releases/download/0.13.9/%UV_ASSET%.zip" -o "%HZ_UV_ZIP%" || goto download_failed
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -Command "$ErrorActionPreference = 'Stop'; if ((Get-FileHash -LiteralPath $env:HZ_UV_ZIP -Algorithm SHA256).Hash -ne $env:HZ_UV_SHA) { throw 'uv download SHA-256 mismatch' }; Expand-Archive -LiteralPath $env:HZ_UV_ZIP -DestinationPath $env:HZ_UV_EXTRACT" || goto download_failed
if not exist "%HZ_UV_EXTRACT%\uv.exe" goto download_failed
move /y "%HZ_UV_EXTRACT%\uv.exe" "%UV_TOOL%" > nul || goto download_failed
rmdir /s /q "%DOWNLOAD_DIR%"

:setup_python
if not exist "%VENV%\.location-ready" goto install_python
if exist "%APP_PYTHON%" goto run_tool

:install_python
echo Installing the pinned Python runtime and USB dependencies. This may take a few minutes...
"%UV_TOOL%" venv --quiet --allow-existing --managed-python --python 3.13.18 "%VENV%" || goto setup_failed
"%UV_TOOL%" pip sync --quiet --require-hashes --only-binary :all: --default-index "https://pypi.org/simple" --python "%APP_PYTHON%" "%SOURCE%requirements.lock" || goto setup_failed
type nul > "%VENV%\.location-ready" || goto setup_failed

:run_tool
echo Starting Hangzhou iPhone Location...
"%APP_PYTHON%" -X utf8 -B -I -c "import runpy, sys; sys.path.insert(0, sys.argv.pop(1)); runpy.run_path(sys.argv.pop(1), run_name='__main__')" "%SOURCE%." "%SOURCE%server.py" %* || goto launch_failed
exit /b 0

:missing_files
echo Extract the whole ZIP archive before opening this launcher.
goto failed

:download_failed
rmdir /s /q "%DOWNLOAD_DIR%"
echo Could not download or verify the runtime installer. Check the connection to GitHub and retry.
goto failed

:setup_failed
echo Could not set up the Python runtime. Check the connection to GitHub and PyPI and retry.
goto failed

:launch_failed
echo The location tool stopped with an error.

:failed
pause
exit /b 1
