@echo off
setlocal
pushd "%~dp0" || exit /b 1
python tools\unity.py samples
exit /b %ERRORLEVEL%
