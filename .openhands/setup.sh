#!/usr/bin/env bash
set -euo pipefail

# OpenHands automatic setup — runs once per fresh workspace.
# Installs JDK, Android SDK, and pre-warms Gradle so the agent
# doesn't have to wait for downloads.

echo "=== Oak auto-setup ==="

# ---- JDK 21 (Temurin) ----
if ! java -version 2>&1 | grep -q "openjdk.*21"; then
  echo "[1/4] Installing JDK 21 (Temurin) via apt…"
  # Use Adoptium's APT repo for reliable Temurin packages
  apt-get update -qq
  apt-get install -y -qq curl gnupg
  curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | gpg --dearmor -o /usr/share/keyrings/adoptium-archive-keyring.gpg
  echo "deb [signed-by=/usr/share/keyrings/adoptium-archive-keyring.gpg] \
    https://packages.adoptium.net/artifactory/deb bookworm main" \
    > /etc/apt/sources.list.d/adoptium.list
  apt-get update -qq
  apt-get install -y -qq temurin-21-jdk
fi

export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
echo "JAVA_HOME=$JAVA_HOME"

# ---- Gradle wrapper ----
echo "[2/4] Making gradlew executable…"
chmod +x gradlew

# ---- Android SDK (only if needed) ----
if [ -z "${ANDROID_HOME:-}" ] && [ -x /usr/local/bin/sdkmanager ] 2>/dev/null; then
  echo "[3/4] Android SDK already present — skipping install"
elif [ -z "${ANDROID_HOME:-}" ]; then
  echo "[3/4] Installing Android SDK (minimal)…"
  apt-get install -y -qq unzip xz-utils

  ANDROID_SDK_ROOT=/opt/android-sdk
  mkdir -p "$ANDROID_SDK_ROOT"
  cd "$ANDROID_SDK_ROOT"

  # Download command-line tools
  CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  curl -fsSL "$CMDLINE_URL" -o cmdline-tools.zip
  unzip -q cmdline-tools.zip
  rm cmdline-tools.zip

  mkdir -p cmdline-tools/latest
  mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
  # fix nested directory when the zip extracts to cmdline-tools/
  if [ -d cmdline-tools/latest/cmdline-tools ]; then
    mv cmdline-tools/latest/cmdline-tools/* cmdline-tools/latest/
    rmdir cmdline-tools/latest/cmdline-tools
  fi

  export ANDROID_HOME=$ANDROID_SDK_ROOT
  export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

  # Accept licences and install platform + build-tools
  yes | sdkmanager --sdk_root="$ANDROID_HOME" \
    "platforms;android-36" \
    "build-tools;36.0.0" \
    "build-tools;29.0.3" \
    > /dev/null 2>&1 || true

  cd /workspace/project/Oak
fi

echo "ANDROID_HOME=${ANDROID_HOME:-unset (not needed for desktop-only tasks)}"

# ---- Pre-warm Gradle ----
echo "[4/4] Pre-warming Gradle cache (desktop dependencies)…"
./gradlew --no-daemon --build-cache :composeApp:dependencies > /dev/null 2>&1 || true

echo "=== Oak auto-setup complete ==="
