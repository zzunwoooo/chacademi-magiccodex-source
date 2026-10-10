@echo off
setlocal
set "ROOT=%~dp0"
set "LOG=%ROOT%build-log.txt"
echo START > "%LOG%"
cd /d "%ROOT%mod"
call gradlew.bat build --console=plain >> "%LOG%" 2>&1
if errorlevel 1 goto fail
cd /d "%ROOT%plugin"
call gradlew.bat build -x test --console=plain >> "%LOG%" 2>&1
if errorlevel 1 goto fail
if not exist "%ROOT%out" mkdir "%ROOT%out"
copy /y "%ROOT%mod\build\libs\chacademy-story-0.1.0.jar" "%ROOT%out\" >> "%LOG%" 2>&1
copy /y "%ROOT%plugin\build\libs\chacademy-story-plugin-0.1.0.jar" "%ROOT%out\" >> "%LOG%" 2>&1
echo ALL_DONE >> "%LOG%"
exit /b 0
:fail
echo ALL_FAILED >> "%LOG%"
exit /b 1
