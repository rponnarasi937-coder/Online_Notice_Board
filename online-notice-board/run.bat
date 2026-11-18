@echo off
REM Compile and start the Online Notice Board.  Usage: run.bat [port]
cd /d "%~dp0"
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin src\noticeboard\*.java
if errorlevel 1 exit /b 1
java -cp bin noticeboard.Main %*
