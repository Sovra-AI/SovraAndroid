#!/usr/bin/env bash
# Build the Iroh transport for Android and generate its Kotlin bindings.
#   scripts/build-rust.sh            arm64-v8a (phones and Apple-silicon emulators)
#   ABIS="arm64-v8a x86_64" scripts/build-rust.sh
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$sdk"/ndk/* | sort -V | tail -1)}"
abis=(${ABIS:-arm64-v8a})
jni="$root/app/src/main/jniLibs"

triple() { case "$1" in arm64-v8a) echo aarch64-linux-android ;; x86_64) echo x86_64-linux-android ;; *) echo "unknown ABI $1" >&2; exit 1 ;; esac; }
for abi in "${abis[@]}"; do
  (cd "$root/rust" && cargo ndk -t "$abi" -P 24 build --release)
  # Only the transport: it links iroh statically (cargo-ndk's -o would also copy dependency dylibs).
  mkdir -p "$jni/$abi"
  cp "$root/rust/target/$(triple "$abi")/release/libsovra_remote_ffi.so" "$jni/$abi/"
done

# Kotlin bindings from the library's own metadata (library mode needs the crate's directory).
lib="$jni/${abis[0]}/libsovra_remote_ffi.so"
(cd "$root/rust" && cargo run --quiet --release --manifest-path "$root/tools/uniffi-bindgen/Cargo.toml" -- \
  generate --library "$lib" --language kotlin --no-format --out-dir "$root/app/src/main/java")
echo "Built ${abis[*]} and Kotlin bindings."
