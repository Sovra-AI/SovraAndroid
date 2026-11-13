# Documentation

SovraAndroid is the native Android app for controlling Sovra on your computer. These guides
describe the current checkout. Start with the [project README](../README.md) for
installation and the [changelog](../CHANGELOG.md) for release history.

The desktop owns the phone-control protocol. Its contract is
[SovraHarness `docs/remote-control.md`](https://github.com/Sovra-AI/SovraHarness/blob/main/docs/remote-control.md);
the iPhone app with the same screens is [SovraiOS](https://github.com/Sovra-AI/SovraiOS).

## Start here

- [Project README](../README.md#quick-start): pair the app with Sovra on your computer and build it.
- [Development](development.md): prerequisites, builds, tests, and the project layout.

## Develop and test

- [Contributing](../CONTRIBUTING.md): issues, branches, pull requests, and required checks.
- [Development](development.md): the Rust transport, Gradle builds, and generated files.
- [Documentation maintenance](documentation.md): writing and checking these guides.

## Understand the implementation

- [Architecture](architecture.md): layers, protocol rules, pairing and secrets, and tests.

See [Security](../SECURITY.md) for vulnerability reporting and
[Code of Conduct](../CODE_OF_CONDUCT.md) for participation expectations.
