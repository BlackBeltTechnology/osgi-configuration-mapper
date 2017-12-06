#!/usr/bin/env bash
set -o pipefail

backend_builder.sh | tee ${BUILD_OUTPUT:?BUILD_OUTPUT must be set!}/build.log

COMMAND_RESULT=$?

buildkite-agent artifact upload "$BUILD_OUTPUT/*.log"
buildkite-agent artifact upload "$BUILD_OUTPUT/*.tgz"

exit $COMMAND_RESULT
