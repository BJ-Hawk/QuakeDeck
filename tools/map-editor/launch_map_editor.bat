@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 (
    py -3 server.py
    goto :eof
)
where python >nul 2>nul
if %errorlevel%==0 (
    python server.py
    goto :eof
)
echo Python 3 was not found on PATH.
echo Please install Python 3, or launch server.py with your preferred Python installation.
pause
