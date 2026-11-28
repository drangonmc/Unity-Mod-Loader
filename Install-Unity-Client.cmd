@echo off
setlocal
set "UNITY_LAUNCHER=%~1"
if not defined UNITY_LAUNCHER set "UNITY_LAUNCHER=%APPDATA%\.minecraft"
set "UNITY_LOADER=%~dp0unity-loader-0.3.4-Beta.jar"
if not exist "%UNITY_LOADER%" set "UNITY_LOADER=%~dp0versions\0.3.4-Beta\unity-loader-0.3.4-Beta.jar"
java -jar "%UNITY_LOADER%" --install-client "%UNITY_LAUNCHER%"
if errorlevel 1 exit /b 1
echo Create an installation for unity-standalone-0.3.4-Beta-26.2.
echo Game directory: "%~dp0standalone-client"
