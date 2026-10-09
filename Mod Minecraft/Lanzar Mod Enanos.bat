@echo off
title Mod Enanos - Minecraft 26.3
cd /d "%~dp0dwarfmod"
rem Usa el JDK 25 portable en ..\tools si existe; si no, usa el JAVA_HOME del sistema (debe ser JDK 25).
for /d %%J in ("%~dp0tools\jdk-25*") do set "JAVA_HOME=%%J"
call gradlew.bat runClient
pause
