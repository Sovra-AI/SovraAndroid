## What

<!-- One or two sentences. -->

## Why

<!-- The problem this solves. -->

## Validation

<!-- Commands run and results; include failures or unavailable checks. For UI
changes, say which device or emulator you used. Mark inapplicable items N/A. -->

## Checklist

- [ ] `./gradlew :app:testDebugUnitTest` passed locally
- [ ] For changes to the Computer tab's picture, `./gradlew :app:connectedDebugAndroidTest` passed on a device or emulator
- [ ] `CHANGELOG.md` updated under **Unreleased**
- [ ] `README.md` updated if a user-visible feature, tab, or command changed
- [ ] Protocol changes match the desktop contract and are mirrored in SovraiOS
- [ ] Tests cover the change (bug fix ⇒ regression test first)
- [ ] No secrets, keys, pairing links, or machine-specific paths
