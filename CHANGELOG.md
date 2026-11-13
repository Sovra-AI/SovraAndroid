# Changelog

All notable changes to SovraAndroid are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). No store release is published yet.

Add entries under **Unreleased** as part of the change that introduces them.

## [Unreleased]

### Added

- **Fair-code license and public repository files.** Source is under n8n's Sustainable
  Use License with Sovra as the licensor, the same as SovraHarness and SovraiOS, with a
  Code of Conduct, security policy, contributing guide, issue and pull request templates,
  and development, architecture, and documentation guides in the shared Sovra style. The
  README uses the Sovra layout with the product logo.
- Native Android app (Kotlin, Jetpack Compose, AGP 9.5, Kotlin 2.4, Compose BOM 2026.09,
  minSdk 26, targetSdk 36) matching the Expo app: pairing by QR scan (CameraX + ZXing),
  `sovra://remote-control` links or a pasted link; Chat, Chats, Skills and Computer tabs;
  Settings with Advanced options.
- The Expo app's Iroh transport crate, built with `cargo-ndk` and bound with uniffi's Kotlin
  generator (`scripts/build-rust.sh`).
- Kotlin port of the protocol client and screen readers, with JUnit tests against the
  desktop's own fixtures and an in-memory desktop.
- The phone's key is encrypted with an Android Keystore key and excluded from backups.
