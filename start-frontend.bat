@echo off
setlocal

cd /d "%~dp0"

echo Starting frontend in a new window...
start "A.T.O.M Frontend" cmd /k "cd /d ""%~dp0frontend"" && npm run dev"

echo.
echo Frontend window opened.
echo UI: http://localhost:3000
