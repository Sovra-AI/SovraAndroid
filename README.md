# Sovra for Android

Native Android (Kotlin + Jetpack Compose) version of the Sovra phone app: pair with
Sovra Harness on your computer by scanning its QR code, then use Sovra from your phone.
It matches the Expo app ([SovraReactNative](https://github.com/Sovra-AI/SovraReactNative))
screen for screen:

- **Chat** — the conversation as bubbles, Sovra's replies with formatting, a chat box that
  types through Sovra's real one, `/` skills with a chip, Stop.
- **Chats** — every chat in Sovra's sidebar, search, show older chats, new chat.
- **Skills** — Sovra's `/` skills, searchable; picking one starts a message with it.
- **Computer** — page shortcuts, a live picture of the window or a list of its controls,
  keyboard keys, and a typing bar for any box there.

The desktop contract is canonical: `SovraHarness/docs/remote-control.md`.

## Build

Needs JDK 17+, the Android SDK (compileSdk 37), the NDK, Rust and `cargo-ndk`.

```bash
scripts/build-rust.sh                 # Iroh transport for arm64-v8a + Kotlin bindings
ABIS="arm64-v8a x86_64" scripts/build-rust.sh   # also Intel emulators
./gradlew :app:testDebugUnitTest      # protocol tests against the desktop fixtures
./gradlew :app:installDebug           # debug build (id ai.sovra.remote.native)
```

Debug builds use the id `ai.sovra.remote.native` so they sit beside the Expo app's dev
build; release builds use `ai.sovra.remote` and replace it.

## Layout

| Path | What |
| --- | --- |
| `rust/` | The phone transport (Iroh 1.4.1 QUIC, ALPN `sovra/remote-control/1`), shared with the Expo app's `modules/sovra-remote/rust`. `uniffi.toml` renames two Kotlin clashes. |
| `tools/uniffi-bindgen/` | Host-only generator for the Kotlin bindings. |
| `app/src/main/java/ai/sovra/remote/protocol/` | Pure Kotlin protocol: wire contract, pairing ticket, client, and the readers for Sovra's screens. Ported from the Expo app's `src/remote/`. |
| `app/src/main/java/ai/sovra/remote/data/` | Storage (Keystore-encrypted phone key), the native transport and app state. |
| `app/src/main/java/ai/sovra/remote/ui/` | Compose screens and the Sovra theme. |
