# Changelog

All notable changes to SovraAndroid are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). No store release is published yet.

Add entries under **Unreleased** as part of the change that introduces them.

## [Unreleased]

### Added

- **Made for phones, simpler everywhere.** A bottom tab bar (Chat, Chats, Skills, More) with
  icons replaces the segmented tabs, and wide screens get a side rail and a column capped at
  640 wide. The top bar has the computer's name and status, a pencil for a new chat and a
  settings icon; the tab bar steps aside while typing.
- **First-run tour.** Four short pages (what the app does, pairing, asking, privacy) with
  Skip and Next. Replay it from Settings → Show the tour again or Pair → How does this work?
- **More tab instead of the Computer tab.** Sovra's other pages, and pages a `/` command opens
  such as `/help`, are rebuilt from the desktop's accessibility snapshot as native screens:
  headings and text, buttons as rows, switches as switches, tabs and choices with a check,
  and a typing bar for text boxes. `openPage` reads them, with a unit test.
- **Simpler chat box.** A sparkle button opens skills, round Send and Stop buttons, and an
  empty chat suggests a few skills to start with.
- `OnboardingTest` (device UI test) replaces `ZoomBoxTest`.
- `ScreenGalleryTest` renders every remote screen (light and dark) against the in-memory
  desktop and saves PNGs for review without a paired computer.
- An open page no longer repeats its title as its first heading.
- **Checked against a real desktop Sovra.** Paired with SovraHarness, opened `/help`, More,
  Team, Chats, Skills and a real conversation, then forgot the phone on both sides. Fixes from it:
  - Cards on a page (a teammate, a model…) are read card by card, buttons first, with a
    divider between cards, so a switch stays with its person instead of a row of identical
    "On the team" switches.
  - Pages that do not name themselves take the name of what was opened (the `/help` skill,
    the More row) instead of "Sovra".
  - Training, My AI's, Knowledge and Connections get their own icons in More.
  - Pairing from a link on a fresh install shows the tour once, then the chat.
  - Cards with one button keep their text with it (a service's name, description and
    "Linked" read before its button), and a card reads text, then buttons, then switches.
  - A button offered twice on a page or in one card (a header and an empty state) shows
    once; the same button in different cards (Edit per teammate) stays.
  - A page that starts by repeating its own name drops the repeat and any stray divider.
  - Counts read naturally: "Web search, 1 skills" from the desktop shows as "1 skill".
- **Scan screen:** when camera access is denied, an Open Settings button goes straight to the
  app's settings, and scanning starts by itself on return once the camera is allowed. A
  device without a camera says so and points to pasting the link.
- `ScannerTest` reads the desktop's pairing link from camera-like frames (a padded Y plane) and
  ignores other codes and empty frames; the scanner and the test share `readPairingCode`.
  - A page without a title no longer loses its first item (the title check matched null).
- When the computer cannot be used (phone control off, unreachable, busy), only the card that
  explains what to do is shown: no dimmed stale screen, no tab bar, and no second red banner
  repeating the same error.
- While the `/` skills list is open, the empty-chat welcome steps aside instead of being cut off.
- `FakeDesktop` moved to `app/src/sharedTest/` and now reports `checked` and `selected`.
- **Play Store bundle.** `scripts/android-release.sh` builds both shipped ABIs, runs the unit
  tests and writes a release AAB signed with the upload key from the Keychain. Without that
  key, release builds stay debug-signed for local installs. See [Google Play release](docs/play-store.md).

- **Transport log.** Developer mode (Advanced settings) shows iroh's own log of relay dials,
  TLS results and lookups, kept in memory only, with Copy details; failed pairings and retries
  offer Show technical details without it. The shared transport also dials through every
  default relay, so a computer that moved relay after pairing is found without address lookup.

- **Connects from anywhere, not only the same Wi-Fi.** The shared transport resolves relay and
  address-lookup hosts with the phone's own DNS (DNS64/NAT64, VPN and Private DNS aware)
  instead of fixed public resolvers that many mobile networks block, so the phone reaches its
  computer over mobile data or another network through Iroh's encrypted relay.
- **Encrypted relay only (Advanced settings).** Skips direct connections so the phone takes the
  same encrypted relay path a strict network forces; useful for checking a setup away from
  home. The transport also keeps idle connections alive with a QUIC ping every 10 s.
- **Clear connection progress.** While connecting, the remote screen shows each step, how long
  it has been trying, the attempt, the last failure and a countdown with **Try now**; pairing
  shows a ticking elapsed time. Once connected the status says "Connected directly" or
  "Connected through an encrypted relay". Same wording and tests as SovraiOS.
- **Fair-code license and public repository files.** Source is under Sovra's Sustainable
  Use License, word for word the same as SovraHarness and SovraiOS, with a
  Code of Conduct, security policy, contributing guide, issue and pull request templates,
  and development, architecture, and documentation guides in the shared Sovra style. The
  README uses the Sovra layout with the product logo.
- Native Android app (Kotlin, Jetpack Compose, AGP 9.5, Kotlin 2.4, Compose BOM 2026.09,
  minSdk 26, targetSdk 36) matching SovraiOS: pairing by QR scan (CameraX + ZXing),
  `sovra://remote-control` links or a pasted link; Chat, Chats, Skills and Computer tabs;
  Settings with Advanced options.
- Builds SovraiOS's Iroh transport crate (one copy for both apps) with `cargo-ndk` and
  binds it with uniffi's Kotlin generator (`scripts/build-rust.sh`).
- Pinch to zoom on the Computer tab's picture (instrumented test:
  `connectedDebugAndroidTest`); the typing bar takes the keyboard at once; status bar icons
  follow the app's appearance.
- Kotlin port of the protocol client and screen readers, with JUnit tests against the
  desktop's own fixtures and an in-memory desktop.
- The phone's key is encrypted with an Android Keystore key and excluded from backups.

### Removed

- Screenshots and the picture of the computer's window: the Show screenshots setting, the
  zoomable mirror and the picture fallback for the conversation (now its plain text).

### Changed

- License text, Code of Conduct and code owners match SovraHarness and SovraiOS word for word
  (Sovra's Sustainable Use License). The README matches SovraiOS's and describes the current
  screens; `scripts/build-rust.sh` finds a sibling `SovraiOS` checkout by itself.
- CI builds the shared transport, runs the unit tests and builds the app on every pull
  request; Dependabot keeps GitHub Actions and Gradle dependencies current.

### Fixed

- **Switching networks recovers.** After a failed try the phone binds a fresh endpoint, so
  moving from Wi-Fi to mobile data no longer leaves it retrying on the old network's sockets.
  A failure now says which half failed: this network reaching the encrypted relay, or the
  computer behind it.
- Leaving the Chat tab while it brought Sovra's chat up left the app "working" for good,
  disabling every control. Flows now run in the app's scope and always finish.
- Enabled primary buttons (e.g. Type) could lose their colour, leaving white text on white.
