@echo off
title Mod Enanos - Minecraft 26.3
cd /d "C:\Users\fsilv\OneDrive\Documentos\Mod Minecraft\dwarfmod"
for /d %%J in ("C:\Users\fsilv\OneDrive\Documentos\Mod Minecraft\tools\jdk-25*") do set "JAVA_HOME=%%J"
call gradlew.bat runClient
pause
