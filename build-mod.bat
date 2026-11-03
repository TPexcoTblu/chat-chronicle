@echo off
setlocal
pushd "%~dp0"
call gradlew.bat build %*
set "chronicle_result=%ERRORLEVEL%"
if "%chronicle_result%"=="0" echo Builds ready in: %~dp0build\releases
popd
exit /b %chronicle_result%
