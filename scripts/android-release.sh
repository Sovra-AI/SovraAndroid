#!/usr/bin/env bash
# Build the Play Store bundle: Rust transport for every shipped ABI, unit tests, then a
# release AAB signed with the upload key.
#   scripts/android-release.sh
# The upload key defaults to ~/.config/sovra/android-upload.jks, with its password in the
# macOS Keychain (service sovra-android-upload). Override with SOVRA_UPLOAD_KEYSTORE and
# SOVRA_UPLOAD_PASSWORD. Bump versionCode in app/build.gradle.kts before each upload.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

export SOVRA_UPLOAD_KEYSTORE="${SOVRA_UPLOAD_KEYSTORE:-$HOME/.config/sovra/android-upload.jks}"
[ -f "$SOVRA_UPLOAD_KEYSTORE" ] || { echo "Upload keystore not found at $SOVRA_UPLOAD_KEYSTORE." >&2; exit 1; }
if [ -z "${SOVRA_UPLOAD_PASSWORD:-}" ]; then
  SOVRA_UPLOAD_PASSWORD="$(security find-generic-password -a sovra-android-upload -s sovra-android-upload -w)"
  export SOVRA_UPLOAD_PASSWORD
fi

# Matches abiFilters in app/build.gradle.kts, so no ABI ships without its library.
ABIS="arm64-v8a x86_64" scripts/build-rust.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:bundleRelease

aab="app/build/outputs/bundle/release/app-release.aab"
jarsigner -verify "$aab" >/dev/null || { echo "$aab is not signed." >&2; exit 1; }
keytool -printcert -jarfile "$aab" | grep -m1 "SHA256:"
echo "Built $aab"
