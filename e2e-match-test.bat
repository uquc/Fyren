@echo off
title Fyren E2E Match Test (Automated)
cd /d D:\develp\Fyren
set JAR=D:\develp\Fyren\Fyren-1.0-SNAPSHOT.jar
set SERVER=115.29.230.57:9876

echo ===================================
echo   Fyren E2E — Match Flow Test
echo   %date% %time%
echo ===================================
echo.

echo [1/4] Starting P1 (Kage, ID=20001)...
start "Fyren-P1" cmd /c "java -cp %JAR% com.Fyren.GameMain client 115.29.230.57 9876 20001 --preset kage > p1_match.log 2>&1"
timeout /t 2 /nobreak >nul

echo [2/4] Starting P2 (Takeshi, ID=20002)...
start "Fyren-P2" cmd /c "java -cp %JAR% com.Fyren.GameMain client 115.29.230.57 9876 20002 --preset takeshi > p2_match.log 2>&1"
timeout /t 3 /nobreak >nul

echo [3/4] Waiting for match (up to 15s)...
set FOUND=0
for /l %%i in (1,1,15) do (
    timeout /t 1 /nobreak >nul
    findstr /c:"找到对手" p1_match.log >nul 2>&1 && set FOUND=1 && goto :check_p2
)
goto :timeout

:check_p2
findstr /c:"找到对手" p2_match.log >nul 2>&1 && set FOUND=1
if %FOUND%==1 goto :success

:timeout
echo.
echo [FAIL] Match not found within timeout.
echo.
echo --- P1 Log (last 5 lines) ---
tail -5 p1_match.log 2>nul || type p1_match.log
echo.
echo --- P2 Log (last 5 lines) ---
tail -5 p2_match.log 2>nul || type p2_match.log
echo.
goto :cleanup

:success
echo [4/4] SUCCESS! Both players matched.
echo.
echo --- P1 Log ---
type p1_match.log
echo.
echo --- P2 Log ---
type p2_match.log
echo.

:cleanup
echo Cleaning up...
taskkill /fi "WINDOWTITLE eq Fyren-P1*" /f >nul 2>&1
taskkill /fi "WINDOWTITLE eq Fyren-P2*" /f >nul 2>&1
echo Done.
pause
