#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(cd "$FRONTEND_DIR/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/apps/backend"
OPENAPI_TARGET_DIR="$BACKEND_DIR/target/generated-openapi"
OPENAPI_SPEC_PATH="$ROOT_DIR/pdd/design/openapi-v0.1.yaml"

"$BACKEND_DIR/mvnw" -q -f "$BACKEND_DIR/pom.xml" \
  package \
  -DskipTests \
  -Dquarkus.smallrye-openapi.store-schema-directory=target/generated-openapi

cp "$OPENAPI_TARGET_DIR/openapi.yaml" "$OPENAPI_SPEC_PATH"
