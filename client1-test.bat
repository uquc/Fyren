@echo off
cd /d D:\develp\Fyren
set JAR=target\Fyren-1.0-SNAPSHOT.jar

java -cp "%JAR%" com.Fyren.GameMain client 115.29.230.57 9876 10001 --preset kage > client1.log 2>&1
echo Exit code: %ERRORLEVEL% >> client1.log
pause
