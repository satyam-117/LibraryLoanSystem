@echo off
REM ============================================================
REM  build.bat  —  Compile and run Library Loan Management System
REM  Requires: Java 8+  and  derby.jar in the lib\ folder
REM ============================================================

setlocal

set MAIN_CLASS=com.library.ui.MainApp
set SRC_DIR=src\main\java
set OUT_DIR=out
set LIB=lib\derby.jar

echo.
echo ============================================================
echo   Library Loan Management System — Build Script
echo ============================================================

REM Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: java not found. Install JDK 8+ and add it to PATH.
    exit /b 1
)

REM Check Derby jar
if not exist %LIB% (
    echo ERROR: %LIB% not found.
    echo.
    echo Please download Apache Derby from:
    echo   https://db.apache.org/derby/derby_downloads.html
    echo.
    echo Copy  db-derby-XX.XX.X.X-bin\lib\derby.jar  into the lib\ folder.
    exit /b 1
)

REM Create output folders
if not exist %OUT_DIR% mkdir %OUT_DIR%
if not exist output  mkdir output

echo.
echo [1/2] Compiling sources...
dir /b /s %SRC_DIR%\*.java > sources.txt
javac -d %OUT_DIR% -cp %LIB% @sources.txt
if errorlevel 1 (
    echo COMPILATION FAILED.
    del sources.txt
    exit /b 1
)
del sources.txt
echo       Done.

echo.
echo [2/2] Running application...
echo.
java -cp "%OUT_DIR%;%LIB%" %MAIN_CLASS%

endlocal
