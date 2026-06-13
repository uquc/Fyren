@echo off
title Fyren E2E Test Suite
cd /d D:\develp\Fyren
set JAR=D:\develp\Fyren\Fyren-1.0-SNAPSHOT.jar

echo ===================================
echo   Fyren End-to-End Match Test
echo ===================================
echo.
echo Launching 2 clients to ECS 115.29.230.57:9876
echo.

echo [1/2] Player 10001 - Kage (WASD, JKLU)
start "Fyren P1-Kage" cmd /c "java -cp %JAR% com.Fyren.GameMain client 115.29.230.57 9876 10001 --preset kage & pause"

echo [2/2] Player 10002 - Takeshi (Arrow keys, 123)
start "Fyren P2-Takeshi" cmd /c "java -cp %JAR% com.Fyren.GameMain client 115.29.230.57 9876 10002 --preset takeshi & pause"

echo.
echo Both windows launched. They should appear on your taskbar.
echo Once both connect, the matchmaker pairs them in 1-2 seconds.
echo.
echo P1: WASD move, J=punch, K=kick, U=special
echo P2: Arrows move, 1=punch, 2=kick, 3=special
echo.
pause
