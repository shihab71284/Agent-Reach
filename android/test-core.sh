#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${TMPDIR:-/tmp}/agent-reach-core-tests"
rm -rf "$OUT"
mkdir -p "$OUT"

javac -encoding UTF-8 -source 8 -target 8 -d "$OUT" \
  "$ROOT/app/src/main/java/com/agentreach/mobile/SmartRouter.java" \
  "$ROOT/app/src/main/java/com/agentreach/mobile/LocalSummarizer.java" \
  "$ROOT/tools/CoreLogicSelfTest.java"
java -cp "$OUT" com.agentreach.mobile.CoreLogicSelfTest
