#!/bin/bash
# Exit on any error
set -e

# --- CI Identity Setup ---
# Required for clean CI environments to allow git commits
git config user.name "FlossWare CI"
git config user.email "ci@flossware.org"

# --- Versioning Logic ---
# Extract current version from pom.xml (the single point of truth)
CURRENT_VERSION="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"

# Parse X.Y format
MAJOR="$(echo "${CURRENT_VERSION}" | cut -d. -f1)"
MINOR="$(echo "${CURRENT_VERSION}" | cut -d. -f2)"

# Increment the minor version
NEXT_MINOR="$(( ${MINOR} + 1 ))"
NEXT_VERSION="${MAJOR}.${NEXT_MINOR}"

echo "Reving version from ${CURRENT_VERSION} to ${NEXT_VERSION}..."

# Update the pom.xml using the versions plugin
mvn versions:set -DnewVersion="${NEXT_VERSION}" -DgenerateBackupPoms=false

# --- Git Lifecycle ---
# Capture the branch name to ensure we push back to the correct place
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

echo "Committing version change to ${CURRENT_BRANCH}..."
git add pom.xml
# [ci skip] prevents the version bump from triggering another build cycle
git commit -m "chore: bump version to ${NEXT_VERSION} [ci skip]"

echo "Creating tag v${NEXT_VERSION}..."
git tag -a "v${NEXT_VERSION}" -m "Release version ${NEXT_VERSION}"

echo "Pushing changes and tags to origin..."
git push origin "${CURRENT_BRANCH}"
git push origin "v${NEXT_VERSION}"

echo "CI/CD Lifecycle complete for org.flossware:jcollections:${NEXT_VERSION}"
