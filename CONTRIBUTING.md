# Contributing to SovraAndroid

Contributions from outside and inside Sovra are welcome: bug reports,
documentation, tests, and focused code changes. Search existing issues first;
for a substantial change, open an issue to discuss the approach before coding.
Follow our [Code of Conduct](CODE_OF_CONDUCT.md).

Report security issues through [SECURITY.md](SECURITY.md), not public issues.

Source on the default branch `main` is under **Sovra's Sustainable Use License**
(see [LICENSE](LICENSE)). The model is fair-code: source-available, and free
to use and extend for your own internal business purposes or for
non-commercial or personal use, with the limits in that license. It is not an OSI open-source license.
Files named `.ee.` or kept in a directory whose name contains `.ee` are under
[LICENSE_EE.md](LICENSE_EE.md) instead.

The canonical engineering rules live in [AGENTS.md](AGENTS.md) and apply to
humans and coding agents alike. This file covers the workflow around them; it
does not duplicate them.

## Getting started

Fork the repository on GitHub if you do not have write access, clone your fork,
and create a branch from the upstream `main`. Install the
[prerequisites](docs/development.md#prerequisites), then run:

```sh
scripts/build-rust.sh         # build the Rust transport and Kotlin bindings
./gradlew :app:installDebug   # debug build on a device or emulator
```

Read before writing code:

- [docs/architecture.md](docs/architecture.md) — layers and the protocol rules the client enforces
- [docs/development.md](docs/development.md) — builds, tests, and generated files
- [docs/README.md](docs/README.md) — documentation map
- [docs/documentation.md](docs/documentation.md) — writing and verifying documentation

## Best practices

- **Definition of done:** `./gradlew :app:testDebugUnitTest` passes locally.
- **Decide in `protocol/`, move bytes in Rust, render in `ui/`.** Protocol logic
  is pure Kotlin that takes a `PhoneTransport`, so JUnit can test it.
- **The desktop contract is canonical.** A wire change starts in SovraHarness;
  copy its fixtures into `app/src/test/resources/fixtures/` and keep
  `ProtocolTest` byte-equal. Make the same change in
  [SovraiOS](https://github.com/Sovra-AI/SovraiOS).
- **Bug fix ⇒ regression test first.**
- **Dependencies:** declare versions in `gradle/libs.versions.toml`. Keep `iroh`
  pinned to the desktop's version.
- **UI:** colours only from `ui/Theme.kt`, 44 dp touch targets, and a label on
  every control.
- **Never commit secrets or pairing links**, and never log, store, or route the
  pairing link; see [SECURITY.md](SECURITY.md).

## Branches, commits, and review

Day-to-day work goes through pull requests so tests, changelog, and review happen
before `main` moves.

1. Branch from the latest upstream `main`. Keep the branch short-lived and focused.
   Name it `feat/<topic>`, `fix/<topic>`, `docs/<topic>`, or `chore/<topic>`.
2. Prefer small, reviewable commits. Prefix every commit message with a
   gitmoji and conventional scope (`✨ feat(chat): …`, `🐛 fix(protocol): …`,
   `📝 docs: …`). No attribution or co-author trailers.
3. **Changelog and README:** every change that adds, changes, fixes, or removes
   behaviour updates [CHANGELOG.md](CHANGELOG.md) under **Unreleased**, and
   [README.md](README.md) whenever user-visible features, tabs, or commands change.
4. Open a pull request against the upstream `main`, including from your fork.
   Fill in the template and record the checks run and any failures.
5. Maintainers squash-merge the pull request. The squash title is the PR title
   (gitmoji prefix, same style as commits). Delete the branch on merge.

Do not force-push `main`.

## Continuous integration

This repository has no GitHub Actions workflow yet. Reviewers rely on the checks
recorded in the pull request, so run `./gradlew :app:testDebugUnitTest` and say
which device or emulator you used for UI changes.
