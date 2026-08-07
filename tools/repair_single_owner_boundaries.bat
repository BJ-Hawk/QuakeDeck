@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 (
    py -3 repair_single_owner_boundaries.py
    goto :done
)
where python >nul 2>nul
if %errorlevel%==0 (
    python repair_single_owner_boundaries.py
    goto :done
)
echo Python 3 was not found on PATH.
exit /b 1
:done
if errorlevel 1 (
    echo.
    echo Topology repair failed.
    pause
    exit /b 1
)
echo.
echo Topology repair completed successfully.
pause
