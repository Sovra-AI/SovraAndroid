# Maintaining documentation

Documentation describes behavior present in this checkout. Update the relevant guide in the
same pull request as a behavior change, and keep the README concise. Record release history
in [CHANGELOG.md](../CHANGELOG.md), not in dated appendices to guides. Track proposals and
future work in GitHub issues.

SovraAndroid, [SovraiOS](https://github.com/Sovra-AI/SovraiOS), and
[SovraHarness](https://github.com/Sovra-AI/SovraHarness) share one documentation style, and
the same license files. When you change the shape of a README, guide, or community file here,
make the matching change in the other repositories.

## Choose the right guide

Use the [documentation index](README.md) to find an existing home before adding a page. Keep
first-use instructions, task guides, technical reference, and design explanations distinct.
A task guide should state prerequisites, concrete steps, what success looks like, and
relevant failure cases. Link to implementation explanations instead of putting them between
the steps.

This structure follows [Diátaxis](https://diataxis.fr/start-here/). Contributor entry points
follow [GitHub's contribution-guideline guidance](https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/setting-guidelines-for-repository-contributors).

## Write from evidence

- Verify commands against Gradle files, `scripts/`, and manifests.
- Verify visible labels against the screens and behavior against `protocol/` and its tests.
- Describe the desktop protocol by linking to the desktop contract; do not restate it.
- State relevant limitations without promising future features or delivery dates.
- Use plain language, descriptive headings, short paragraphs, and relative links. Link to
  other repositories with full GitHub URLs, not sibling-checkout paths.
- Keep examples synthetic. Never commit keys, pairing links, personal logs, or machine paths.

## Documentation inventory

Every document in this repository, what kind it is, and what it must be checked against
when it changes. Each was reviewed against this guide (evidence, plain language, relative
links, no dates or promises, no machine paths or secrets) when the open-source files were
aligned with SovraHarness.

| Document | Kind | Verify against |
| --- | --- | --- |
| [README](../README.md) | Overview and quick start | Screens and their labels, Gradle tasks, `scripts/`, SovraiOS's README (same shape) |
| [CONTRIBUTING](../CONTRIBUTING.md) | Contributor guide | [ci.yml](../.github/workflows/ci.yml), SovraHarness's CONTRIBUTING |
| [SECURITY](../SECURITY.md) | Policy | `data/Storage.kt`, the manifest and data-extraction rules, the desktop's SECURITY |
| [CODE_OF_CONDUCT](../CODE_OF_CONDUCT.md), [LICENSE](../LICENSE), [LICENSE_EE](../LICENSE_EE.md) | Policy | SovraHarness's copies, word for word apart from the product name |
| [AGENTS](../AGENTS.md) | Engineering rules | Code layout, Gradle tasks, `scripts/` |
| [CHANGELOG](../CHANGELOG.md) | Release history | Commits since the last release; one Added, Changed and Fixed section each |
| [Development](development.md) | How-to | [build.gradle.kts](../app/build.gradle.kts), [libs.versions.toml](../gradle/libs.versions.toml), [build-rust.sh](../scripts/build-rust.sh), [ci.yml](../.github/workflows/ci.yml) |
| [Architecture](architecture.md) | Explanation | `protocol/`, `data/`, the shared Rust transport, and their tests |
| [Test parity](test-parity.md) | Reference | SovraiOS's Jest tests and `app/src/test/` |
| [Documentation index](README.md) | Index | This inventory |
| [Pull request](../.github/pull_request_template.md) and issue templates | Contributor forms | The checks in CONTRIBUTING |

## Verify a change

Check that every relative link resolves, run `./gradlew :app:testDebugUnitTest` and `git diff --check`, and
review Markdown and Mermaid rendering on GitHub before merging. Run documented commands when
feasible and report checks that need unavailable devices, accounts, or a desktop checkout.
When you add, move, or remove a document, update the inventory above.
