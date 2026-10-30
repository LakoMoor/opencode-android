#!/bin/sh
set -eu
# Git, Python, curl, ripgrep, ssh and CA certificates are bundled in the APK (see LocalRuntime.ensureTools).
cd /workspace
exec /usr/local/bin/opencode serve --hostname 127.0.0.1 --port "$OPENCODE_ANDROID_PORT"
