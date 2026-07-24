@echo off
setlocal

set "JAVA_HOME=C:\Users\kshri\tools\jdk17\jdk-17.0.11+9"
set "MVN=C:\Users\kshri\tools\maven\apache-maven-3.9.6\bin\mvn.cmd"
set "MONGOD=C:\Users\kshri\tools\mongodb\mongodb-win32-x86_64-windows-7.0.14\bin\mongod.exe"
set "MONGO_DATA=C:\Users\kshri\tools\mongodb-data"
set "PATH=%JAVA_HOME%\bin;C:\Users\kshri\tools\maven\apache-maven-3.9.6\bin;%PATH%"

echo [1/4] Starting MongoDB...
start "MongoDB" "%MONGOD%" --dbpath "%MONGO_DATA%" --port 27017

echo Waiting for MongoDB...
timeout /t 5 /nobreak >nul

echo [2/4] Starting ML Service...
start "ML Service" cmd /k "cd /d %~dp0ml && pip install -r requirements.txt -q && python -m uvicorn api.anomaly_service:app --host 0.0.0.0 --port 8000"

echo [3/4] Starting Backend...
start "Backend" cmd /k "set JAVA_HOME=%JAVA_HOME% && set PATH=%JAVA_HOME%\bin;C:\Users\kshri\tools\maven\apache-maven-3.9.6\bin;%PATH% && cd /d %~dp0backend && mvn spring-boot:run"

echo [4/4] Starting Frontend...
start "Frontend" cmd /k "cd /d %~dp0frontend && npm start"

echo.
echo All services starting:
echo   MongoDB  -^> mongodb://localhost:27017
echo   ML       -^> http://localhost:8000
echo   Backend  -^> http://localhost:8080
echo   Frontend -^> http://localhost:3000
echo.
pause
