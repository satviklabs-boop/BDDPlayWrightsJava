@echo off
REM ---------------------------------------------------------------------------
REM Convenience wrapper so the suite can be run without Maven on PATH.
REM
REM It uses a locally provisioned Maven if MAVEN_HOME is set, otherwise falls
REM back to `mvn` on PATH. Pass Maven goals as arguments, e.g.:
REM
REM     run-tests.cmd test
REM     run-tests.cmd test "-Dcucumber.filter.tags=@smoke"
REM ---------------------------------------------------------------------------

if "%JAVA_HOME%"=="" (
  if exist "C:\Program Files\Java\jdk-17" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
  )
)
echo JAVA_HOME=%JAVA_HOME%

if not "%MAVEN_HOME%"=="" (
  set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
) else (
  set "MVN_CMD=mvn"
)

call "%MVN_CMD%" %*
