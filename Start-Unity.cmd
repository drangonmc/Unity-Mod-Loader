@echo off
setlocal
pushd "%~dp0" || exit /b 1
if not exist "standalone-instance\unity-mods\" (
    mkdir "standalone-instance\unity-mods" || exit /b 1
    copy /y "versions\0.2.9-Beta\unity-world-marker.jar" "standalone-instance\unity-mods\" >nul || exit /b 1
)
pushd "standalone-instance" || exit /b 1
java -Xms256m -Xmx2g -jar "..\versions\0.2.9-Beta\unity-loader-0.2.9-Beta.jar" --runtime-dir "..\cache\0.2.9-Beta" nogui %*
set "UNITY_STATUS=%ERRORLEVEL%"
popd
popd
exit /b %UNITY_STATUS%
