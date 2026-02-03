@echo off

echo [INFO] Iniciando limpeza do sistema...

:: TEMP usuario
echo [STEP] Limpando TEMP do usuario
del /f /s /q "%temp%\*" >nul 2>&1
for /d %%i in ("%temp%\*") do rmdir /s /q "%%i"

:: TEMP Windows
echo [STEP] Limpando TEMP do Windows
del /f /s /q "C:\Windows\Temp\*" >nul 2>&1
for /d %%i in ("C:\Windows\Temp\*") do rmdir /s /q "%%i"

:: Prefetch
echo [STEP] Limpando Prefetch
del /f /s /q "C:\Windows\Prefetch\*" >nul 2>&1

:: Cache Windows Update
echo [STEP] Limpando cache do Windows Update
net stop wuauserv >nul
del /f /s /q "C:\Windows\SoftwareDistribution\Download\*" >nul 2>&1
net start wuauserv >nul

:: Desativa hibernacao
echo [STEP] Desativando hibernacao
powercfg -h off >nul

:: Desativa inicializacao rapida
echo [STEP] Desativando inicializacao rapida
reg add "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Power" ^
/v HiberbootEnabled /t REG_DWORD /d 0 /f >nul

echo [DONE] Limpeza concluida
echo [REBOOT_REQUIRED] true

exit /b 0