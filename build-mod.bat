@echo off
setlocal
pushd "%~dp0"
call gradlew.bat build
set "chronicle_result=%ERRORLEVEL%"
if "%chronicle_result%"=="0" echo Build ready in: %~dp0build\libs
popd
exit /b %chronicle_result%
