@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ===========================================================
rem MedChain Local Multi-Service Deployment Script
rem -----------------------------------------------------------
rem Starts required services, tracks their PIDs in deploy_pids.txt
rem and performs health‑checks. Re‑uses already running healthy services.
rem ===========================================================

rem ----- Configuration -------------------------------------------------
set "PROJECT_ROOT=%~dp0"
set "PID_FILE=%PROJECT_ROOT%deploy_pids.txt"
if exist "%PID_FILE%" del "%PID_FILE%"

rem ----- Helper: Record PID -------------------------------------------
:record_pid
rem   %1 = PID, %2 = Service name, %3 = STARTED|REUSED
if "%~1"=="" (
    echo ERROR: record_pid received an empty PID.
    exit /b 1
)
if "%~2"=="" (
    echo ERROR: record_pid received an empty service name.
    exit /b 1
)
if "%~3"=="" (
    echo ERROR: record_pid received an empty mode.
    exit /b 1
)
>>"%PID_FILE%" echo %~1 %~2 %~3
if errorlevel 1 (
    echo ERROR: Failed to write PID file: "%PID_FILE%"
    exit /b 1
)
exit /b 0

rem ----- Helper: Check Port ------------------------------------------
:check_port

set "PORT=%~1"
set "PID_USED="
rem First try netstat to find a listening PID
for /f "tokens=5" %%a in ('netstat -ano ^| findstr " :%PORT% " ^| findstr LISTENING') do set "PID_USED=%%a"
if defined PID_USED (
    rem Determine service name based on port
    if "%PORT%"=="8080" set "SERVICE=Backend"
    if "%PORT%"=="8545" set "SERVICE=Hardhat"
    if "%PORT%"=="8000" set "SERVICE=Sentinel"
    if "%PORT%"=="3000" set "SERVICE=Frontend"
    if defined SERVICE (
        echo Port %PORT% is in use by PID %PID_USED% (assumed %SERVICE%).
        set "SKIP_%PORT%=1"
        set "PID_%PORT%=%PID_USED%"
        call :record_pid !PID_USED! !SERVICE! REUSED
    ) else (
        echo WARNING: Could not determine service for port %PORT% with PID %PID_USED%.
    )
    exit /b 0
)
rem Fallback: use process inspection based on known command patterns
if "%PORT%"=="8545" (
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'hardhat' -and $_.Name -eq 'node.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
) else if "%PORT%"=="8000" (
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'uvicorn' -and $_.Name -eq 'python.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
) else if "%PORT%"=="8080" (
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'medchain-backend-1.0.0.jar' -and $_.Name -eq 'java.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
) else if "%PORT%"=="3000" (
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'serve -s' -and $_.Name -eq 'node.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
)
if defined PID_USED (
    rem Determine service name based on port
    if "%PORT%"=="8080" set "SERVICE=Backend"
    if "%PORT%"=="8545" set "SERVICE=Hardhat"
    if "%PORT%"=="8000" set "SERVICE=Sentinel"
    if "%PORT%"=="3000" set "SERVICE=Frontend"
    echo Detected running %SERVICE% process PID %PID_USED% for port %PORT%.
    set "SKIP_%PORT%=1"
    set "PID_%PORT%=%PID_USED%"
    call :record_pid !PID_USED! !SERVICE! REUSED
    exit /b 0
)
rem No process found; port is free
exit /b 0

rem ----- Verify required ports ---------------------------------------
set "REQUIRED_PORTS=8545 8000 8080 3000"
for %%P in (%REQUIRED_PORTS%) do (
    call :check_port %%P
    if errorlevel 1 (
        echo Failed port check for %%P. Exiting.
        exit /b 1
    )
)

rem ---------------------------------------------------------------------------
rem Start Hardhat (8545) if not skipped
rem ---------------------------------------------------------------------------
rem Reset PID_USED before checking reuse
set "PID_USED="
if defined SKIP_8545 (
    echo Reusing existing Hardhat node (PID %PID_USED%).
) else (
    start "Hardhat" cmd /c "cd /d "%PROJECT_ROOT%blockchain" && npx hardhat node --hostname 127.0.0.1"
    timeout /t 5 >nul
    set "ATTEMPTS=0"
    :hardhat_pid_retry
    set /a ATTEMPTS+=1
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'hardhat' -and $_.Name -eq 'node.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
    if defined PID_USED (
        call :record_pid !PID_USED! Hardhat STARTED
    ) else (
        if %ATTEMPTS% GEQ 5 (
            echo ERROR: Failed to capture Hardhat PID after multiple attempts.
            exit /b 1
        ) else (
            timeout /t 2 >nul
            goto :hardhat_pid_retry
        )
    )
    powershell -Command "if(!(Test-NetConnection -ComputerName localhost -Port 8545 -InformationLevel Quiet)){exit 1}"
    if errorlevel 1 (
        echo Hardhat node failed to start.
        exit /b 1
    ) else (
        echo Hardhat started successfully.
    )
)

rem ---------------------------------------------------------------------------
rem Deploy contract (requires Hardhat running)
rem ---------------------------------------------------------------------------
pushd "%PROJECT_ROOT%blockchain"
for /f "delims=" %%L in ('npx hardhat run scripts/deploy.js --network localhost') do set "DEPLOY_OUTPUT=%%L"
popd
rem Extract contract address from deployed.json
for /f "delims=" %%A in ('type "%PROJECT_ROOT%blockchain\deployed.json" ^| findstr /i "address"') do set "ADDRESS_LINE=%%A"
for /f "tokens=2 delims=:," %%B in ("%ADDRESS_LINE%") do set "CONTRACT_ADDR=%%~B"
set "CONTRACT_ADDR=%CONTRACT_ADDR:~1,-1%"
if "%CONTRACT_ADDR%"=="" (
    echo Failed to obtain contract address from deployment.
    exit /b 1
)
echo Deployed MedChain contract at %CONTRACT_ADDR%

rem ---------------------------------------------------------------------------
rem Update backend config with contract address
rem ---------------------------------------------------------------------------
set "CONFIG_FILE=%PROJECT_ROOT%backend\src\main\resources\application-dev.yml"
powershell -Command "(Get-Content -Path '%CONFIG_FILE%') -replace '(?s)blockchain:\\s*enabled:.*', 'blockchain:\n  enabled: true\n  rpc:\n    url: http://localhost:8545\n  contract:\n    address: %CONTRACT_ADDR%' | Set-Content -Path '%CONFIG_FILE%'"

rem ---------------------------------------------------------------------------
rem Start Sentinel AI (8000) if not skipped
rem ---------------------------------------------------------------------------
if defined SKIP_8000 (
    echo Reusing existing Sentinel AI (PID %PID_USED%).
) else (
    start "Sentinel AI" cmd /c "cd /d "%PROJECT_ROOT%ml" && python -m uvicorn api.anomaly_service:app --host 0.0.0.0 --port 8000"
    timeout /t 5 >nul
    set "ATTEMPTS=0"
    :sentinel_pid_retry
    set /a ATTEMPTS+=1
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'uvicorn' -and $_.Name -eq 'python.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
    if defined PID_USED (
        call :record_pid !PID_USED! Sentinel STARTED
    ) else (
        if %ATTEMPTS% GEQ 5 (
            echo ERROR: Failed to capture Sentinel AI PID.
            exit /b 1
        ) else (
            timeout /t 2 >nul
            goto :sentinel_pid_retry
        )
    )
    powershell -Command "if(!(Invoke-RestMethod -Uri http://localhost:8000/health -UseBasicParsing -ErrorAction SilentlyContinue)){exit 1}"
    if errorlevel 1 (
        echo Sentinel AI failed to start.
        exit /b 1
    ) else (
        echo Sentinel AI started successfully.
    )
)

rem ---------------------------------------------------------------------------
rem Start Backend (8080) if not skipped
rem ---------------------------------------------------------------------------
if defined SKIP_8080 (
    echo Reusing existing Backend (PID %PID_USED%).
) else (
    start "Backend" cmd /c "cd /d "%PROJECT_ROOT%backend" && set JAVA_HOME=C:\Users\kshri\tools\jdk17\jdk-17.0.11+9 && set PATH=%JAVA_HOME%\bin;%PATH% && set BLOCKCHAIN_ENABLED=true && set BLOCKCHAIN_RPC_URL=http://localhost:8545 && set ML_SERVICE_URL=http://localhost:8000 && set IPFS_ENABLED=false && set SPRING_PROFILES_ACTIVE=dev && java -jar target\medchain-backend-1.0.0.jar"
    timeout /t 10 >nul
    set "ATTEMPTS=0"
    :backend_pid_retry
    set /a ATTEMPTS+=1
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'medchain-backend-1.0.0.jar' -and $_.Name -eq 'java.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
    if defined PID_USED (
        call :record_pid !PID_USED! Backend STARTED
    ) else (
        if %ATTEMPTS% GEQ 5 (
            echo ERROR: Failed to capture Backend PID.
            exit /b 1
        ) else (
            timeout /t 2 >nul
            goto :backend_pid_retry
        )
    )
    powershell -Command "$r = Invoke-RestMethod -Uri http://localhost:8080/actuator/health -UseBasicParsing -ErrorAction SilentlyContinue; if(!$r){exit 1}"
    if errorlevel 1 (
        echo Backend failed to start.
        exit /b 1
    ) else (
        echo Backend started successfully.
    )
)

rem ---------------------------------------------------------------------------
rem Start Frontend (3000) if not skipped
rem ---------------------------------------------------------------------------
if defined SKIP_3000 (
    echo Reusing existing Frontend (PID %PID_USED%).
) else (
    start "Frontend" cmd /c "cd /d "%PROJECT_ROOT%frontend" && npx serve -s build -l 3000"
    timeout /t 5 >nul
    set "ATTEMPTS=0"
    :frontend_pid_retry
    set /a ATTEMPTS+=1
    for /f "usebackq tokens=*" %%i in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process ^| Where-Object { $_.CommandLine -match 'serve -s' -and $_.Name -eq 'node.exe' } ^| Select-Object -ExpandProperty ProcessId"`) do set "PID_USED=%%i"
    if defined PID_USED (
        call :record_pid !PID_USED! Frontend STARTED
    ) else (
        if %ATTEMPTS% GEQ 5 (
            echo ERROR: Failed to capture Frontend PID after multiple attempts.
            exit /b 1
        ) else (
            timeout /t 2 >nul
            goto :frontend_pid_retry
        )
    )
    powershell -Command "try {$r = Invoke-WebRequest http://localhost:3000 -UseBasicParsing; if($r.StatusCode -ne 200){exit 1}} catch {exit 1}"
    if errorlevel 1 (
        echo Frontend failed to start.
        exit /b 1
    ) else (
        echo Frontend started successfully.
    )
)

rem ---------------------------------------------------------------------------
rem Final health‑check summary
rem ---------------------------------------------------------------------------

echo.
echo Deployment PID file (%PID_FILE%):
type "%PID_FILE%"

endlocal
