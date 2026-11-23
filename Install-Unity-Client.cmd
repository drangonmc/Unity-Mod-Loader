@echo off
setlocal
set "UNITY_LAUNCHER=%~1"
if not defined UNITY_LAUNCHER set "UNITY_LAUNCHER=%APPDATA%\.minecraft"
java -jar "%~dp0versions\0.2.2-Beta\unity-loader-0.2.2-Beta.jar" --install-client "%UNITY_LAUNCHER%"
if errorlevel 1 exit /b 1
if not exist "%~dp0standalone-client\unity-mods\" (
    mkdir "%~dp0standalone-client\unity-mods" || exit /b 1
    copy /y "%~dp0versions\0.2.2-Beta\unity-client-witness.jar" "%~dp0standalone-client\unity-mods\" >nul || exit /b 1
)
echo Create an installation for unity-standalone-0.2.2-Beta-26.2.
echo Game directory: "%~dp0standalone-client"
