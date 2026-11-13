#!/usr/bin/env bash
# Build the Iroh transport for Android and generate its Kotlin bindings.
# The crate is SovraiOS's (one copy for both apps): a sibling checkout of SovraiOS
# (or its older name, SovraReactNative), or RUST_CRATE=/path/to/modules/sovra-remote/rust.
#   scripts/build-rust.sh            arm64-v8a (phones and Apple-silicon emulators)
#   ABIS="arm64-v8a x86_64" scripts/build-rust.sh
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
crate="${RUST_CRATE:-}"
if [ -z "$crate" ]; then
  for sibling in SovraiOS SovraReactNative; do
    [ -f "$root/../$sibling/modules/sovra-remote/rust/Cargo.toml" ] && { crate="$root/../$sibling/modules/sovra-remote/rust"; break; }
  done
fi
crate="${crate:-$root/../SovraiOS/modules/sovra-remote/rust}"
[ -f "$crate/Cargo.toml" ] || { echo "Transport crate not found at $crate (set RUST_CRATE)." >&2; exit 1; }
crate="$(cd "$crate" && pwd)"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$sdk"/ndk/* | sort -V | tail -1)}"
abis=(${ABIS:-arm64-v8a})
jni="$root/app/src/main/jniLibs"

triple() { case "$1" in arm64-v8a) echo aarch64-linux-android ;; x86_64) echo x86_64-linux-android ;; *) echo "unknown ABI $1" >&2; exit 1 ;; esac; }
for abi in "${abis[@]}"; do
  (cd "$crate" && cargo ndk -t "$abi" -P 24 build --release)
  # Only the transport: it links iroh statically (cargo-ndk's -o would also copy dependency dylibs).
  mkdir -p "$jni/$abi"
  cp "$crate/target/$(triple "$abi")/release/libsovra_remote_ffi.so" "$jni/$abi/"
done

# Kotlin bindings from the library's own metadata. Library mode reads cargo metadata from
# the crate's directory; this repo's uniffi.toml adds the Kotlin-only renames.
lib="$jni/${abis[0]}/libsovra_remote_ffi.so"
(cd "$crate" && cargo run --quiet --release --manifest-path "$root/tools/uniffi-bindgen/Cargo.toml" -- \
  generate --library "$lib" --language kotlin --no-format --config "$root/uniffi.toml" --out-dir "$root/app/src/main/java")
echo "Built ${abis[*]} and Kotlin bindings from $crate."
