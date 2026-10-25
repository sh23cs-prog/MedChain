@echo off
setlocal

set "JAVA_HOME=C:\Users\kshri\tools\jdk17\jdk-17.0.11+9"
set "MVN=C:\Users\kshri\tools\maven\apache-maven-3.9.6\bin\mvn.cmd"
set "MONGOD=C:\Users\kshri\tools\mongodb\mongodb-win32-x86_64-windows-7.0.14\bin\mongod.exe"
set "MONGO_DATA=C:\Users\kshri\tools\mongodb-data"
set "PATH=%JAVA_HOME%\bin;C:\Users\kshri\tools\maven\apache-maven-3.9.6\bin;%PATH%"

echo ===================================================
echo             MedChain Multi-Service Launcher
echo ===================================================

echo [1/5] Starting MongoDB Database...
start "MongoDB Engine (27017)" "%MONGOD%" --dbpath "%MONGO_DATA%" --port 27017

echo [2/5] Starting Hardhat Local EVM Blockchain Node...
start "Hardhat EVM Blockchain Node (8545)" cmd /k "cd /d %~dp0blockchain && npx hardhat node --hostname 127.0.0.1"

echo Waiting 5 seconds for Hardhat RPC readiness...
ping -n 6 127.0.0.1 >nul

echo Deploying MedChain Smart Contracts to Hardhat EVM...
start "Hardhat Contract Deployment" cmd /c "cd /d "%~dp0blockchain" && npx hardhat run scripts/deploy.js --network localhost"

echo [3/5] Starting FastAPI Sentinel ML Anomaly Service...
start "FastAPI ML Anomaly Service (8000)" cmd /k "cd /d "%~dp0ml" && python -m uvicorn api.anomaly_service:app --host 0.0.0.0 --port 8000 --reload"

echo [4/5] Starting Spring Boot Core Backend...
start "Spring Boot Core Backend (8080)" cmd /k "set "JAVA_HOME=%JAVA_HOME%" && set "PATH=%JAVA_HOME%\bin;C:\Users\kshri\tools\maven\apache-maven-3.9.6\bin;%PATH%" && set "SPRING_MAIL_HOST=smtp.gmail.com" && set "SPRING_MAIL_PORT=587" && set "SPRING_MAIL_USERNAME=medchainteam23@gmail.com" && set "SPRING_MAIL_PASSWORD=ccintweojwvwbkzr" && set "SPRING_MAIL_AUTH=true" && set "SPRING_MAIL_STARTTLS=true" && set "BLOCKCHAIN_ENABLED=true" && set "BLOCKCHAIN_RPC_URL=http://localhost:8545" && set "IPFS_ENABLED=false" && cd /d "%~dp0backend" && mvn spring-boot:run"

echo [5/5] Starting React Dashboard Frontend...
start "React Dashboard SPA (3000)" cmd /k "cd /d "%~dp0frontend" && npm start"

echo.
echo ===================================================
echo All MedChain Services Initiated:
echo   1. MongoDB Engine      -^> mongodb://localhost:27017
echo   2. Hardhat EVM Node    -^> http://localhost:8545
echo   3. Sentinel ML API     -^> http://localhost:8000
echo   4. Spring Boot Backend -^> http://localhost:8080
echo   5. React Dashboard     -^> http://localhost:3000
echo ===================================================
echo.
pause
