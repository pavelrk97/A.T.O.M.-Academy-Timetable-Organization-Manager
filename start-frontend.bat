@echo off
setlocal

cd /d "%~dp0"

echo Starting frontend in a new window...
start "A.T.O.M Frontend" cmd /k "cd /d ""%~dp0docs\V0 UI"" && npm run dev"

echo.
echo Frontend window opened.
echo UI: http://localhost:3000
