@echo off
setlocal
pushd "%~dp0" || exit /b 1
if not exist "standalone-instance\" mkdir "standalone-instance" || exit /b 1
if not exist "plugins\" mkdir "plugins" || exit /b 1
set "UNITY_LOADER=%~dp0unity-loader-0.3.4-Beta.jar"
if not exist "%UNITY_LOADER%" set "UNITY_LOADER=%~dp0versions\0.3.4-Beta\unity-loader-0.3.4-Beta.jar"
pushd "standalone-instance" || exit /b 1
java "-Dmc.unity.pluginsDir=%~dp0plugins" -Xms256m -Xmx2g -jar "%UNITY_LOADER%" --runtime-dir "..\cache\0.3.4-Beta" nogui %*
set "UNITY_STATUS=%ERRORLEVEL%"
popd
popd
exit /b %UNITY_STATUS%
