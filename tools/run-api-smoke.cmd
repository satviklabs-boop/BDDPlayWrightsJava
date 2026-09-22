@echo off
REM ---------------------------------------------------------------------------
REM Runs only the API scenarios.
REM
REM The default API target needs no key and has no daily quota, so this is a fast
REM (browser-free) way to check the HTTP layer in isolation.
REM
REM   tools\run-api-smoke.cmd
REM   tools\run-api-smoke.cmd @smoke     (only @smoke scenarios)
REM
REM Requires JAVA_HOME to point at a JDK 17+ install, and Maven (`mvn`) on PATH.
REM Narrow the run with TAGS, never with -Dcucumber.features: that property makes
REM Cucumber ignore the suite's classpath discovery and fail to discover tests.
REM ---------------------------------------------------------------------------
setlocal

if "%JAVA_HOME%"=="" (
  echo [ERROR] JAVA_HOME is not set. Set it to your JDK 17+ directory, e.g.
  echo         set JAVA_HOME=C:\Program Files\Java\jdk-17
  exit /b 1
)

set "TAGS=@api"
if not "%~1"=="" set "TAGS=@api and %~1"

pushd "%~dp0.."
call mvn -B test -Dcucumber.filter.tags="%TAGS%"
set "EXIT_CODE=%ERRORLEVEL%"
popd

if "%EXIT_CODE%"=="0" (
  echo [PASSED] API scenarios completed successfully.
) else (
  echo [FAILED] API scenarios failed with exit code %EXIT_CODE%.
)

endlocal & exit /b %EXIT_CODE%
