@echo off
setlocal
pushd "%~dp0" || exit /b 1
python tools\unity.py fabric-api %*
exit /b %ERRORLEVEL%
