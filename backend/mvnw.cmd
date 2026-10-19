@REM Maven Wrapper startup script for Windows

@echo off
setlocal

set SCRIPT_DIR=%~dp0
if "%SCRIPT_DIR:~-1%"=="\" set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%
set APP_BASE_NAME=%~n0
set APP_HOME=%SCRIPT_DIR%

@REM Add default JVM options
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@REM Build the classpath
set CLASSPATH=%APP_HOME%\.mvn\wrapper\maven-wrapper.jar

@REM Download wrapper jar if not exists
set WRAPPER_JAR=%APP_HOME%\.mvn\wrapper\maven-wrapper.jar
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
)

@REM Execute Maven
java %DEFAULT_JVM_OPTS% -Dmaven.multiModuleProjectDirectory="%APP_HOME%" -classpath "%CLASSPATH%" org.apache.maven.wrapper.MavenWrapperMain %*
