@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Determine project root (directory of this script)
set "PROJECT_ROOT=%~dp0"
set "PID_FILE=%PROJECT_ROOT%deploy_pids.txt"

if not exist "%PID_FILE%" (
    echo No PID file found at "%PID_FILE%". Nothing to stop.
    exit /b 1
)

echo Stopping MedChain services started by this deployment...

for /f "usebackq tokens=1,2,3" %%A in ("%PID_FILE%") do (
    set "PID=%%A"
    set "SERVICE=%%B"
    set "MODE=%%C"
    if /I "!MODE!"=="STARTED" (
        echo Stopping !SERVICE! (PID !PID!)...
        taskkill /PID !PID! /F >nul 2>&1
        if errorlevel 1 (
            echo Failed to terminate PID !PID! for !SERVICE!.
        ) else (
            echo Successfully stopped !SERVICE!.
        )
    ) else (
        echo Skipping !SERVICE! (PID !PID!) - marked as REUSED.
    )
)

rem Delete PID file after stopping services
del "%PID_FILE%"

echo All done.
endlocal