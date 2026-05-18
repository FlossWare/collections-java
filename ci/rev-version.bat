@echo off
REM Batch file version of rev-version.sh
REM Exit on any error
setlocal enabledelayedexpansion

echo Starting version bump process...

REM --- CI Identity Setup ---
REM Required for clean CI environments to allow git commits
git config user.name "FlossWare CI"
if errorlevel 1 goto :error
git config user.email "ci@flossware.org"
if errorlevel 1 goto :error

REM --- Versioning Logic ---
REM Extract current version from pom.xml (the single point of truth)
echo Extracting current version from pom.xml...
for /f "delims=" %%i in ('mvn help:evaluate -Dexpression=project.version -q -DforceStdout') do set CURRENT_VERSION=%%i
if errorlevel 1 goto :error

REM Parse X.Y format
for /f "tokens=1,2 delims=." %%a in ("%CURRENT_VERSION%") do (
    set MAJOR=%%a
    set MINOR=%%b
)

REM Increment the minor version
set /a NEXT_MINOR=%MINOR%+1
set NEXT_VERSION=%MAJOR%.%NEXT_MINOR%

echo Reving version from %CURRENT_VERSION% to %NEXT_VERSION%...

REM Update the pom.xml using the versions plugin
mvn versions:set -DnewVersion="%NEXT_VERSION%" -DgenerateBackupPoms=false
if errorlevel 1 goto :error

REM --- Git Lifecycle ---
REM Capture the branch name to ensure we push back to the correct place
for /f "delims=" %%i in ('git rev-parse --abbrev-ref HEAD') do set CURRENT_BRANCH=%%i
if errorlevel 1 goto :error

echo Committing version change to %CURRENT_BRANCH%...
git add pom.xml
if errorlevel 1 goto :error

REM [ci skip] prevents the version bump from triggering another build cycle
git commit -m "chore: bump version to %NEXT_VERSION% [ci skip]"
if errorlevel 1 goto :error

echo Creating tag v%NEXT_VERSION%...
git tag -a "v%NEXT_VERSION%" -m "Release version %NEXT_VERSION%"
if errorlevel 1 goto :error

echo Pushing changes and tags to origin...
git push origin "%CURRENT_BRANCH%"
if errorlevel 1 goto :error

git push origin "v%NEXT_VERSION%"
if errorlevel 1 goto :error

echo CI/CD Lifecycle complete for org.flossware:jcollections:%NEXT_VERSION%
goto :success

:error
echo ERROR: Version bump failed!
exit /b 1

:success
exit /b 0
