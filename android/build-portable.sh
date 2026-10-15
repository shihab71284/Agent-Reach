#!/usr/bin/env bash
# Build a signed debug APK without Gradle or a preinstalled Android SDK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"
CACHE="${AGENT_REACH_ANDROID_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/agent-reach-android}"
BUILD="$ROOT/app/build/portable"
OUTPUT="${1:-$ROOT/app/build/outputs/apk/debug/agent-reach-debug.apk}"

for command in python git npm node openssl zip unzip base64; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done
mkdir -p "$CACHE" "$BUILD/classes" "$BUILD/package" "$(dirname "$OUTPUT")"
rm -rf "$BUILD/classes" "$BUILD/package"
mkdir -p "$BUILD/classes" "$BUILD/package"

if command -v java >/dev/null; then
  JAVA="$(command -v java)"
else
  if [[ ! -x "$CACHE/jdk4py/jdk4py/java-runtime/bin/java" ]]; then
    echo "[1/7] Installing a private Java runtime in $CACHE..."
    python -m pip install --quiet --no-deps --target "$CACHE/jdk4py" 'jdk4py==17.0.9.2'
  fi
  JAVA="$CACHE/jdk4py/jdk4py/java-runtime/bin/java"
fi

fetch_github_file() {
  local repo="$1" ref="$2" path="$3" output="$4"
  [[ -s "$output" ]] && return
  echo "      fetching $repo/$path"
  local raw="https://raw.githubusercontent.com/$repo/$ref/$path"
  if command -v curl >/dev/null && curl -fsSL --retry 2 "$raw" -o "$output.tmp"; then
    mv "$output.tmp" "$output"
    return
  fi
  rm -f "$output.tmp"
  if command -v gh >/dev/null; then
    local sha
    sha="$(gh api "repos/$repo/contents/$path?ref=$ref" --jq .sha)"
    gh api "repos/$repo/git/blobs/$sha" --jq .content | tr -d '\n' | base64 -d > "$output"
    return
  fi
  echo "Could not download $path. Install curl or the authenticated GitHub CLI." >&2
  exit 1
}

if command -v javac >/dev/null; then
  JAVAC=("$(command -v javac)")
  compile_java() {
    local boot="$1" out="$2" list="$3"
    "${JAVAC[@]}" -encoding UTF-8 -source 7 -target 7 -bootclasspath "$boot" -d "$out" "@$list"
  }
else
  if [[ ! -s "$CACHE/tools.jar" ]]; then
    echo "[2/7] Fetching the OpenJDK compiler..."
    temp="$(mktemp -d)"
    npm pack --silent dataslope-tools-jar@1.0.0 --pack-destination "$temp" >/dev/null
    tar -xzf "$temp"/*.tgz -C "$temp"
    cp "$temp/package/tools.jar" "$CACHE/tools.jar"
    rm -rf "$temp"
  fi
  if command -v sha256sum >/dev/null; then
    echo 'd7c44c575209ffec22ed6ca40f2a3ef0e0b710c303d1bf8c51cbc937ea29942a  '"$CACHE/tools.jar" | sha256sum -c - >/dev/null
  fi
  compile_java() {
    local boot="$1" out="$2" list="$3"
    "$JAVA" -cp "$CACHE/tools.jar" com.sun.tools.javac.Main \
      -encoding UTF-8 -source 7 -target 7 -bootclasspath "$boot" -d "$out" "@$list"
  }
fi

ANDROID_PLATFORMS_COMMIT="1e98db1a199e8f7f85541af26bfc27019501b132"
fetch_github_file Sable/android-platforms "$ANDROID_PLATFORMS_COMMIT" android-35/android.jar "$CACHE/android-35.jar"
fetch_github_file Sable/android-platforms "$ANDROID_PLATFORMS_COMMIT" android-28/android.jar "$CACHE/android-28.jar"
if command -v sha256sum >/dev/null; then
  echo '4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b  '"$CACHE/android-35.jar" | sha256sum -c - >/dev/null
  echo 'd20853d289a5161d9cf1c1268d7923076487d4fa68c970b0733e2d5a0ccfc3c5  '"$CACHE/android-28.jar" | sha256sum -c - >/dev/null
fi

if [[ ! -f "$CACHE/dx-classes/com/android/dx/command/Main.class" ]]; then
  echo "[3/7] Building the open-source Android dex compiler..."
  rm -rf "$CACHE/dx-source" "$CACHE/dx-classes"
  mkdir -p "$CACHE/dx-source"
  git -C "$CACHE/dx-source" init --quiet
  git -C "$CACHE/dx-source" remote add origin https://github.com/CodingGay/BlackObfuscator.git
  git -C "$CACHE/dx-source" fetch --quiet --depth 1 origin acc54be3ea6bfee12ae32087478f27509443c1ec
  git -C "$CACHE/dx-source" checkout --quiet FETCH_HEAD
  mkdir -p "$CACHE/dx-classes"
  find "$CACHE/dx-source/dx/src/main/java" -name '*.java' | sort > "$CACHE/dx-sources.txt"
  compile_java "$CACHE/android-28.jar" "$CACHE/dx-classes" "$CACHE/dx-sources.txt"
fi

printf '%s\n' '[4/7] Compiling Agent Reach...'
find "$ROOT/app/src/main/java" -name '*.java' | sort > "$BUILD/app-sources.txt"
compile_java "$CACHE/android-35.jar" "$BUILD/classes" "$BUILD/app-sources.txt"

printf '%s\n' '[5/7] Converting bytecode to Dalvik dex...'
"$JAVA" -Xmx512m -cp "$CACHE/dx-classes" com.android.dx.command.Main \
  --dex --min-sdk-version=26 --output="$BUILD/package/classes.dex" "$BUILD/classes"
python "$ROOT/tools/axml_manifest.py" "$BUILD/package/AndroidManifest.xml"
(
  cd "$BUILD/package"
  zip -q -0 "$BUILD/agent-reach-unsigned.apk" AndroidManifest.xml classes.dex
)

printf '%s\n' '[6/7] Signing APK (v1 + v2 + v3)...'
if [[ ! -d "$CACHE/signer/node_modules/apk_sign_ts" ]]; then
  mkdir -p "$CACHE/signer"
  npm install --silent --prefix "$CACHE/signer" apk_sign_ts@1.0.1
fi
if [[ -n "${APK_SIGNING_KEY:-}" && -n "${APK_SIGNING_CERT:-}" ]]; then
  KEY="$APK_SIGNING_KEY"
  CERT="$APK_SIGNING_CERT"
else
  KEY="$CACHE/debug-key.pem"
  CERT="$CACHE/debug-cert.pem"
  if [[ ! -s "$KEY" || ! -s "$CERT" ]]; then
    openssl req -x509 -newkey rsa:2048 -keyout "$KEY" -out "$CERT" -sha256 \
      -days 10000 -nodes -subj '/CN=Agent Reach Debug/O=Agent Reach/C=BD' >/dev/null 2>&1
  fi
fi
node "$ROOT/tools/sign-apk.mjs" "$BUILD/agent-reach-unsigned.apk" "$OUTPUT" \
  "$KEY" "$CERT" "$CACHE/signer"
unzip -tq "$OUTPUT"

printf '%s\n' '[7/7] Build complete'
if command -v sha256sum >/dev/null; then sha256sum "$OUTPUT"; fi
ls -lh "$OUTPUT"
echo "Install with: adb install -r '$OUTPUT'"
