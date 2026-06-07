@echo off
REM Fyren GWT/WebGL Compilation Script (Windows)
REM Compiles Java → JavaScript for browser play (demo mode only)
REM Prerequisites: mvn compile must have been run first

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

set GDX_VER=1.12.1
set GWT_VER=2.8.2

REM Find Maven repo (use mvn to auto-detect, or fallback)
for /f "tokens=*" %%i in ('mvn help:evaluate -Dexpression^=settings.localRepository -q -DforceStdout 2^>nul') do set MVN_REPO=%%i
if "%MVN_REPO%"=="" (
    if exist "%USERPROFILE%\.m2\repository" set MVN_REPO=%USERPROFILE%\.m2\repository
    if exist "D:\soft\repository" set MVN_REPO=D:\soft\repository
)
echo Maven repo: %MVN_REPO%

set GDX=%MVN_REPO%\com\badlogicgames\gdx\gdx\%GDX_VER%\gdx-%GDX_VER%.jar
set GDX_SRC=%MVN_REPO%\com\badlogicgames\gdx\gdx\%GDX_VER%\gdx-%GDX_VER%-sources.jar
set GDX_GWT=%MVN_REPO%\com\badlogicgames\gdx\gdx-backend-gwt\%GDX_VER%\gdx-backend-gwt-%GDX_VER%.jar
set GDX_GWT_SRC=%MVN_REPO%\com\badlogicgames\gdx\gdx-backend-gwt\%GDX_VER%\gdx-backend-gwt-%GDX_VER%-sources.jar
set GWT_USER=%MVN_REPO%\com\google\gwt\gwt-user\%GWT_VER%\gwt-user-%GWT_VER%.jar
set GWT_DEV=%MVN_REPO%\com\google\gwt\gwt-dev\%GWT_VER%\gwt-dev-%GWT_VER%.jar
set JSINTEROP=%MVN_REPO%\com\google\jsinterop\jsinterop-annotations\1.0.2\jsinterop-annotations-1.0.2.jar
set VALIDATION=%MVN_REPO%\javax\validation\validation-api\1.0.0.GA\validation-api-1.0.0.GA.jar

REM GWT compiler transitive dependencies
set JSINTEROP_SRC=%MVN_REPO%\com\google\jsinterop\jsinterop-annotations\1.0.2\jsinterop-annotations-1.0.2-sources.jar
set VALIDATION_SRC=%MVN_REPO%\javax\validation\validation-api\1.0.0.GA\validation-api-1.0.0.GA-sources.jar
set ANT_JAR=%MVN_REPO%\org\apache\ant\ant\1.9.6\ant-1.9.6.jar
set ANT_LAUNCHER=%MVN_REPO%\org\apache\ant\ant-launcher\1.9.6\ant-launcher-1.9.6.jar
set COLT=%MVN_REPO%\colt\colt\1.2.0\colt-1.2.0.jar
set ASM=%MVN_REPO%\org\ow2\asm\asm\5.0.3\asm-5.0.3.jar
set ASM_UTIL=%MVN_REPO%\org\ow2\asm\asm-util\5.0.3\asm-util-5.0.3.jar
set ASM_COMMONS=%MVN_REPO%\org\ow2\asm\asm-commons\5.0.3\asm-commons-5.0.3.jar
set GSON=%MVN_REPO%\com\google\code\gson\gson\2.6.2\gson-2.6.2.jar
set JSR305=%MVN_REPO%\com\google\code\findbugs\jsr305\3.0.2\jsr305-3.0.2.jar
set TAPESTRY=%MVN_REPO%\tapestry\tapestry\4.0.2\tapestry-4.0.2.jar

REM Ensure gwt-dev is downloaded
if not exist "%GWT_DEV%" (
    echo Downloading gwt-dev...
    call mvn dependency:get -Dartifact=com.google.gwt:gwt-dev:%GWT_VER% -q
)

set CLASSES=target\classes
set SRC=src\main\java

echo === Fyren GWT Compilation ===
echo Output: target\gwt-out\
echo.

REM Build classpath (Windows uses ; as separator)
set CP=%GWT_DEV%;%GWT_USER%;%GDX_GWT%;%GDX_GWT_SRC%;%GDX%;%GDX_SRC%;%JSINTEROP%;%JSINTEROP_SRC%;%VALIDATION%;%VALIDATION_SRC%;%SRC%;%CLASSES%;%ANT_JAR%;%ANT_LAUNCHER%;%COLT%;%ASM%;%ASM_UTIL%;%ASM_COMMONS%;%GSON%;%JSR305%;%TAPESTRY%

echo Running GWT compiler...
java -cp "%CP%" com.google.gwt.dev.Compiler -war target\gwt-out -style PRETTY -logLevel INFO com.Fyren.FyrenGwt

if %ERRORLEVEL% EQU 0 (
    echo.
    echo === GWT Compilation Complete ===
    echo Output: target\gwt-out\
    dir /b target\gwt-out\fyren\ 2>nul
) else (
    echo.
    echo === GWT Compilation FAILED ===
    echo Check errors above.
)
