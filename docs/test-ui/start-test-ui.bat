@echo off
setlocal
cd /d "%~dp0"

if "%ATOM_GATEWAY_URL%"=="" (
  set "ATOM_GATEWAY_URL=http://localhost:8081"
)

echo Starting A.T.O.M. test UI...
echo Static UI: http://127.0.0.1:3000
echo Proxy target: %ATOM_GATEWAY_URL%
echo Press Ctrl+C to stop.
echo.

node "%~dp0server.js"
