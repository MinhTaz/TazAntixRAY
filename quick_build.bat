@echo off
echo Building TazAntixRAY...

REM Check if pom.xml exists
if not exist pom.xml (
    echo Error: pom.xml not found
    pause
    exit /b 1
)

REM Try Maven wrapper first
if exist mvnw.cmd (
    echo Using Maven wrapper...
    mvnw.cmd clean package -DskipTests
    goto :check_result
)

REM Try system Maven
where mvn >nul 2>&1
if %errorlevel% equ 0 (
    echo Using system Maven...
    mvn clean package -DskipTests
    goto :check_result
)

echo Maven not found. Please install Maven.
pause
exit /b 1

:check_result
if exist "target\TazAntixRAY-1.0.1.jar" (
    echo.
    echo SUCCESS! JAR file created: target\TazAntixRAY-1.0.1.jar
    dir target\TazAntixRAY-1.0.1.jar
) else (
    echo.
    echo Build may have failed. Check output above.
)

pause
