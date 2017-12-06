#!/bin/bash
echo "New Project version: $PROJECT_VERSION"
echo "Project directory: $PROJECT_DIR"

mvn versions:set -DnewVersion=$PROJECT_VERSION -f $PROJECT_DIR

