@echo off
title Reparando Microsoft Office
echo ===============================
echo  Reparador Automatico Office
echo ===============================
echo.

set OFFICEC2R=

if exist "C:\Program Files\Microsoft Office\Office16\OfficeC2RClient.exe" (
    set OFFICEC2R="C:\Program Files\Microsoft Office\Office16\OfficeC2RClient.exe"
)

if exist "C:\Program Files (x86)\Microsoft Office\Office16\OfficeC2RClient.exe" (
    set OFFICEC2R="C:\Program Files (x86)\Microsoft Office\Office16\OfficeC2RClient.exe"
)

if "%OFFICEC2R%"=="" (
    echo Office nao encontrado.
    pause
    exit
)

echo Office encontrado em:
echo %OFFICEC2R%
echo.

echo Iniciando Quick Repair...
%OFFICEC2R% /repair user

if errorlevel 1 (
    echo.
    echo Quick Repair falhou. Tentando Full Repair...
    %OFFICEC2R% /repair full
)

echo.
echo Processo iniciado.
echo O Office pode abrir janelas proprias.
pause
