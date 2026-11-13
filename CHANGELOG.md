# Changelog

All notable changes to SovraAndroid are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). No store release is published yet.

Add entries under **Unreleased** as part of the change that introduces them.

## [Unreleased]

### Added

- **Connecting shows its progress.** While the phone connects, it shows each step in plain
  words, how long it has been trying, and a **Try now** button between attempts; after
  repeated failures it lists what to check on the computer. Once connected, the status says
  whether the link is direct or through an encrypted relay.
- **Connects from anywhere, not only the same Wi-Fi.** The shared transport resolves relay and
  address-lookup hosts with the phone's own DNS (DNS64/NAT64, VPN and Private DNS aware)
  instead of fixed public resolvers that many mobile networks block, so the phone reaches its
  computer over mobile data or another network through Iroh's encrypted relay.
- **Clear connection progress.** While connecting, the remote screen shows each step, how long
  it has been trying, the attempt, the last failure and a countdown with **Try now**; pairing
  shows a ticking elapsed time. Once connected the status says "Connected directly" or
  "Connected through an encrypted relay". Same wording and tests as SovraiOS.

### Changed

- License text, Code of Conduct and code owners match SovraHarness and SovraiOS word for word
  (Sovra's Sustainable Use License). The README matches SovraiOS's and describes the current
  screens; `scripts/build-rust.sh` finds a sibling `SovraiOS` checkout by itself.
- CI builds the shared transport, runs the unit tests and builds the app on every pull
  request; Dependabot keeps GitHub Actions and Gradle dependencies current.

### Fixed

- Leaving the Chat tab while it brought Sovra's chat up left the app "working" for good,
  disabling every control. Flows now run in the app's scope and always finish.
- Enabled primary buttons (e.g. Type) could lose their colour, leaving white text on white.

### Added

- **Fair-code license and public repository files.** Source is under Sovra's Sustainable
  Use License, word for word the same as SovraHarness and SovraiOS, with a
  Code of Conduct, security policy, contributing guide, issue and pull request templates,
  and development, architecture, and documentation guides in the shared Sovra style. The
  README uses the Sovra layout with the product logo.
- Native Android app (Kotlin, Jetpack Compose, AGP 9.5, Kotlin 2.4, Compose BOM 2026.09,
  minSdk 26, targetSdk 36) matching the Expo app: pairing by QR scan (CameraX + ZXing),
  `sovra://remote-control` links or a pasted link; Chat, Chats, Skills and Computer tabs;
  Settings with Advanced options.
- Builds the Expo app's Iroh transport crate (one copy for both apps) with `cargo-ndk` and
  binds it with uniffi's Kotlin generator (`scripts/build-rust.sh`).
- Pinch to zoom on the Computer tab's picture (instrumented test: `connectedDebugAndroidTest`); the typing bar takes the keyboard at once;
  status bar icons follow the app's appearance.
- Kotlin port of the protocol client and screen readers, with JUnit tests against the
  desktop's own fixtures and an in-memory desktop.
- The phone's key is encrypted with an Android Keystore key and excluded from backups.
