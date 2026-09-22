@echo off
REM ---------------------------------------------------------------------------
REM Runs only the API scenarios.
REM
REM The default API target needs no key and has no daily quota, so this is a fast
REM (browser-free) way to check the HTTP layer in isolation.
REM
REM   tools\run-api-smoke.cmd
REM	tools\run-api-smoke.cmd @smoke     (only @smoke scenarios)
REM ---------------------------------------------------------------------------
setlocal

if "%JAVA_HOME%"=="" (
  echo [ERROR] JAVA_HOME is not set. Set it to your JDK 17+ directory, e.g.
  echo         set JAVA_HOME=C:\Program Files\Java\jdk-17
  exit /b 1
)

pushd "%~dp0.."
set "MVN_CMD=mvn"
if exist "mvnw.cmd" set "MVN_CMD=mvnw.cmd"

REM Narrow the run with TAGS, not with -Dcucumber.features. Setting the features
REM property makes Cucumber ignore the suite's classpath discovery and fail with
REM "TestEngine with ID 'cucumber' failed to discover tests".
set "TAGS=@api"
if not "%~1"=="" set "TAGS=@api and %~1"

call %MVN_CMD% -B test -Dcucumber.filter.tags="%TAGS%"

endlocal
