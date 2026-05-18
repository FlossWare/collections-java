# PowerShell version of rev-version.sh
# Exit on any error
$ErrorActionPreference = "Stop"

Write-Host "Starting version bump process..." -ForegroundColor Cyan

# --- CI Identity Setup ---
# Required for clean CI environments to allow git commits
git config user.name "FlossWare CI"
git config user.email "ci@flossware.org"

# --- Versioning Logic ---
# Extract current version from pom.xml (the single point of truth)
Write-Host "Extracting current version from pom.xml..." -ForegroundColor Yellow
$CURRENT_VERSION = mvn help:evaluate -Dexpression=project.version -q -DforceStdout
if ($LASTEXITCODE -ne 0) {
    throw "Failed to extract version from pom.xml"
}

# Parse X.Y format
$versionParts = $CURRENT_VERSION.Split('.')
$MAJOR = $versionParts[0]
$MINOR = [int]$versionParts[1]

# Increment the minor version
$NEXT_MINOR = $MINOR + 1
$NEXT_VERSION = "$MAJOR.$NEXT_MINOR"

Write-Host "Reving version from $CURRENT_VERSION to $NEXT_VERSION..." -ForegroundColor Green

# Update the pom.xml using the versions plugin
mvn versions:set -DnewVersion="$NEXT_VERSION" -DgenerateBackupPoms=false
if ($LASTEXITCODE -ne 0) {
    throw "Failed to set new version in pom.xml"
}

# --- Git Lifecycle ---
# Capture the branch name to ensure we push back to the correct place
$CURRENT_BRANCH = git rev-parse --abbrev-ref HEAD
if ($LASTEXITCODE -ne 0) {
    throw "Failed to get current branch name"
}

Write-Host "Committing version change to $CURRENT_BRANCH..." -ForegroundColor Yellow
git add pom.xml
if ($LASTEXITCODE -ne 0) {
    throw "Failed to stage pom.xml"
}

# [ci skip] prevents the version bump from triggering another build cycle
git commit -m "chore: bump version to $NEXT_VERSION [ci skip]"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to commit version change"
}

Write-Host "Creating tag v$NEXT_VERSION..." -ForegroundColor Yellow
git tag -a "v$NEXT_VERSION" -m "Release version $NEXT_VERSION"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create git tag"
}

Write-Host "Pushing changes and tags to origin..." -ForegroundColor Yellow
git push origin "$CURRENT_BRANCH"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to push branch to origin"
}

git push origin "v$NEXT_VERSION"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to push tag to origin"
}

Write-Host "CI/CD Lifecycle complete for org.flossware:jcollections:$NEXT_VERSION" -ForegroundColor Green
