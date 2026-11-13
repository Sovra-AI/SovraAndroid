# Development

This guide covers building the app from source, the tests that gate a commit, and where code
lives. Engineering rules for humans and coding agents are in [AGENTS.md](../AGENTS.md).

## Prerequisites

| Needs | Notes |
| --- | --- |
| JDK 17+ | For Gradle and the Android Gradle Plugin |
| Android SDK and NDK | compileSdk 37; `scripts/build-rust.sh` uses `ANDROID_HOME` and the newest NDK unless `ANDROID_NDK_HOME` is set |
| Rust through rustup | `rustup target add aarch64-linux-android x86_64-linux-android` |
| `cargo-ndk` | `cargo install cargo-ndk` |
| SovraiOS checkout | Holds the shared transport crate; point `RUST_CRATE` at its `modules/sovra-remote/rust`, or check it out next to this repo as `../SovraiOS` |

## Build and run

```sh
export RUST_CRATE=../SovraiOS/modules/sovra-remote/rust
scripts/build-rust.sh                           # arm64-v8a: phones and Apple-silicon emulators
ABIS="arm64-v8a x86_64" scripts/build-rust.sh   # also Intel emulators
./gradlew :app:installDebug                     # id ai.sovra.remote.native
```

The script builds the shared Iroh transport with `cargo-ndk`, copies `libsovra_remote_ffi.so` into
`app/src/main/jniLibs/`, and generates the Kotlin bindings into `app/src/main/java/uniffi/`
with the host-only generator in `tools/uniffi-bindgen/` and the renames in `uniffi.toml`.
Rerun it after the transport crate changes.

Debug builds use the application id `ai.sovra.remote.native`; release builds use
`ai.sovra.remote`.

## Checks

```sh
./gradlew :app:testDebugUnitTest
```

Run it before every commit. This repository has no CI workflow yet, so record the checks you
ran in the pull request.

## Stack

| Concern | Choice |
| --- | --- |
| App | Kotlin 2.4, Jetpack Compose, AGP 9.5, minSdk 26, targetSdk 36 |
| QR scanning | CameraX and ZXing |
| Secure storage | Android Keystore AES key encrypting the phone key in private preferences |
| Transport | SovraiOS's Rust crate `modules/sovra-remote/rust` (Iroh 1.4.1 QUIC, same version as the desktop), bound with uniffi's Kotlin generator |
| Tests | JUnit for the protocol and screen readers |

Dependency versions live in [gradle/libs.versions.toml](../gradle/libs.versions.toml).

## Layout

| Path | What |
| --- | --- |
| `uniffi.toml` | Kotlin-only renames for the shared transport's bindings (`close` and `message` clashes). |
| `tools/uniffi-bindgen/` | Host-only generator for the Kotlin bindings. |
| `app/src/main/java/ai/sovra/remote/protocol/` | Pure Kotlin protocol: wire contract, pairing ticket, client, and the readers for Sovra's screens. |
| `app/src/main/java/ai/sovra/remote/data/` | Storage, the native transport, and app state. |
| `app/src/main/java/ai/sovra/remote/ui/` | Compose screens and the Sovra theme (`Theme.kt`, ported from the desktop). |
| `app/src/test/` | JUnit tests and the desktop fixtures. |
| `scripts/build-rust.sh` | Builds the transport and generates bindings. |
