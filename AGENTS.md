# Sovra for Android — agent rules

Native Kotlin/Compose twin of the Expo app in `../SovraReactNative`. The desktop contract is
canonical: `../SovraHarnessRust/docs/remote-control.md`. Keep behaviour identical to the
Expo app: a change to its `src/remote/` belongs here too, and the other way round.

## Commands

```bash
scripts/build-rust.sh                 # Rust transport + Kotlin bindings (needs NDK, cargo-ndk)
./gradlew :app:testDebugUnitTest      # must pass before done
./gradlew :app:installDebug
```

## Rules

- **Protocol logic is pure Kotlin in `protocol/`** and takes a `PhoneTransport`. The Rust
  crate only moves bytes; decisions in Rust can't be unit-tested here.
- **Never replay input.** Only `stale_observation` may be retried. Mutations need a revision
  from an inspection taken immediately before, in the same session.
- **Never log, store or route the pairing link.** No analytics, no saved state, no clipboard
  leftovers; the activity drops it from its intent once read.
- Wire changes: copy the desktop fixtures into `app/src/test/resources/fixtures/` and keep
  `ProtocolTest` byte-equal. Keep `iroh` pinned to the desktop's version.
- `app/src/main/java/uniffi/` and `app/src/main/jniLibs/` are generated; never commit them.
- Colours only from `ui/Theme.kt` (ported from the desktop). 44 dp touch targets and a
  label on every control.
- Commits use gitmoji (`✨ feat(chat): …`) and update `CHANGELOG.md` under `[Unreleased]`.
