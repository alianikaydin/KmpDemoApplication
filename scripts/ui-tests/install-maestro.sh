#!/usr/bin/env bash
# Installs the pinned Maestro CLI version (MAESTRO_VERSION).
set -euo pipefail

: "${MAESTRO_VERSION:?MAESTRO_VERSION must be set}"

curl -fsSL "https://get.maestro.mobile.dev" | MAESTRO_VERSION="$MAESTRO_VERSION" bash

export PATH="$HOME/.maestro/bin:$PATH"
if [ -n "${GITHUB_PATH:-}" ]; then
  echo "$HOME/.maestro/bin" >> "$GITHUB_PATH"
fi

installed="$(maestro --version | tail -n 1 | tr -d '[:space:]')"
if [ "$installed" != "$MAESTRO_VERSION" ]; then
  echo "::error title=Infrastructure failure::Maestro $installed installed, expected $MAESTRO_VERSION"
  exit 2
fi
echo "Maestro $installed installed"
