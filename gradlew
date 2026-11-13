#!/usr/bin/env sh
# Lightweight Gradle launcher. Downloads the pinned Gradle distribution on first use.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VERSION=$(sed -n 's/^distributionUrl=.*gradle-\([0-9.]*\)-bin.zip$/\1/p' "$ROOT/gradle/wrapper/gradle-wrapper.properties")
if [ -z "$VERSION" ]; then echo "Could not read Gradle version" >&2; exit 1; fi
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-$VERSION-bin/pruthvi"
GRADLE="$CACHE/gradle-$VERSION/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  mkdir -p "$CACHE"
  ZIP="$CACHE/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  else echo "Install curl or wget to download Gradle" >&2; exit 1; fi
  unzip -q -o "$ZIP" -d "$CACHE"
  rm -f "$ZIP"
fi
exec "$GRADLE" -p "$ROOT" "$@"
