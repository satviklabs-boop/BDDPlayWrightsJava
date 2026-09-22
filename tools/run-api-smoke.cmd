@echo off
REM ---------------------------------------------------------------------------
REM Runs the API layer smoke check against jsonplaceholder.typicode.com.
REM
REM Use this when reqres.in is rate limited (HTTP 429): it proves the API layer
REM itself works - real HTTP, status codes, JSON parsing - against a target with
REM no daily quota.
REM
REM   tools\run-api-smoke.cmd
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
call %MVN_CMD% -B test ^
  -Dcucumber.filter.tags="@api-smoke" ^
  -DAPI_BASE_URL=https://jsonplaceholder.typicode.com

endlocal
