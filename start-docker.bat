@echo off
setlocal

cd /d "%~dp0"

if not exist ".env" (
    echo [.env] not found, copying from .env.example
    copy /Y ".env.example" ".env" >nul
)

echo Starting Docker stack...
docker compose up --build -d

if errorlevel 1 (
    echo Docker compose failed.
    exit /b 1
)

echo.
echo Backend is starting in Docker.
echo Gateway: http://localhost:8081
echo Identity: http://localhost:8082
echo Schedule: http://localhost:8080
echo Import:   http://localhost:8083

