<div align="center">

# Aivora

**Local-first Android AI configuration manager**

<br>

<a href="https://github.com/Farewell-coder/Aivora/releases/latest"><img src="https://img.shields.io/github/v/release/Farewell-coder/Aivora?label=release" alt="Release"></a>
&nbsp;
<a href="LICENSE"><img src="https://img.shields.io/github/license/Farewell-coder/Aivora" alt="License"></a>
&nbsp;
<a href="README.md"><img src="https://img.shields.io/badge/language-%E4%B8%AD%E6%96%87-lightgrey?style=flat" alt="中文"></a>

</div>

---

Aivora is a **local-first** AI service configuration manager for Android. It keeps your endpoints, API keys, model lists and notes on one device: encrypted at rest, easy to search, testable on demand, and transferable to other apps through an encrypted file.

No account, no cloud sync, no key proxy, no backend service. Your data stays on your device.

## Contents

- [Overview](#overview)
- [Features](#features)
- [Download and install](#download-and-install)
- [AEX exchange protocol](#aex-exchange-protocol)
- [For third-party developers](#for-third-party-developers)
- [Ecosystem](#ecosystem)
- [Contributing](#contributing)
- [Changelog](#changelog)
- [License](#license)

## Overview

### The problem

If you use several AI services, your endpoints and API keys usually end up scattered across chat history, notes and bookmarks. Switching phones or clients, or recommending a service to a friend, means searching and copying all over again. Aivora collects them into one local app and turns "moving a configuration between apps" into a documented, encrypted, verifiable operation.

### Features

| Capability | Detail |
|---|---|
| Encrypted local storage | API keys are encrypted with a data key protected by Android Keystore; never stored as plaintext in the database |
| Configuration management | Create, edit, search and delete (with confirmation); only the name is required |
| Provider templates | Built-in endpoint templates for common providers, plus fully custom entries |
| Model diagnostics | Fetches `/models`, tests each model serially, reports status, HTTP code, latency and error summary; supports cancellation and per-model retry |
| Configuration exchange | Export an encrypted file protected by an independent credential; import requires that credential |
| Full backup | Aivora-specific encrypted backup; cross-device restore re-encrypts secrets with the new device key after an overwrite confirmation |
| Interface | Material 3, light / dark / system theme, optional system authentication |

### Design principles

- **Local first** — all data stays on the device; no feature depends on a server being reachable.
- **No security theatre** — protocol, algorithms and source are public. Security comes from cryptography, not from hidden implementation or obfuscation.
- **Compatibility first** — a new format never retires the old one; existing files and credentials stay valid.
- **Open exchange** — configurations should be able to move between apps instead of being locked into one.

## Features

### Service configurations

All configurations are listed as cards showing the name and the latest diagnostic status, with actions below.

Typical use: keep DeepSeek, a self-hosted gateway and a relay service side by side, distinguish them with notes, and find them instantly.

### Key diagnostics

A single tap fetches the model list and tests each model. Failures report the real HTTP status and the server's own message instead of a generic "invalid key".

Typical use: before trusting a new gateway key, run it once and see which models actually work, which are rate limited and which are out of quota.

> Diagnostics send one minimal real request (`max_tokens = 1`) and may consume a small amount of quota.

### Export and share

The export action on each configuration card opens a dialog with two side-by-side choices: **Export** saves the `.akcp` wherever you pick, **Share** hands it to the system share sheet. Both produce byte-identical files.

The dialog also shows the 52-character credential unique to that file. **The file itself carries no credential**, so either way the recipient needs that credential separately before it can be decrypted.

Typical use: send the file to another device of yours or to a client, and pass the credential through a different channel. Anyone intercepting the file alone still cannot read it.

### Settings and backup

Settings hosts the appearance choice (light / dark / system), the app lock and encrypted full backups. Backup files are protected by an independent credential; a cross-device restore re-encrypts secrets with the new device key and asks for confirmation before overwriting.

Typical use: export an encrypted backup before switching phones, then import and confirm it on the new device.

## Download and install

### Latest release

Current version: **0.0.5**

- Releases: [github.com/Farewell-coder/Aivora/releases/latest](https://github.com/Farewell-coder/Aivora/releases/latest)
- Artifact name: `Aivora-v0.0.5.apk`

### Installation

1. Download the `.apk` from the releases page.
2. Allow installation from the current browser or file manager when prompted.
3. Open Aivora — no registration or sign-in required.

Requirements: **Android 8.0 (API 26)+**, arm64 devices.

### About signing

Release builds are signed with a fixed certificate, so they upgrade in place. A locally built debug package uses a different signature and cannot be installed over a release build; uninstall it first (this clears app data, so export a backup beforehand).

## AEX exchange protocol

> **Status: AEX 1.0 is frozen and implemented since 0.0.5.**
> Configuration cards and multi-select both generate `.akcp` files delivered through the Android share sheet.
> The legacy AKCP 1.0 / `.aivora` import and export paths remain fully supported.

### Purpose

Make "move a configuration from app A to app B" follow a published standard. Third-party software does not need a private Aivora format, an Aivora SDK, an account or an Aivora server — only a compliant implementation of the public spec.

AEX is a **complete, standalone** exchange protocol, not a plugin or optional sub-module of the legacy format. The legacy format is kept only for historical compatibility; Aivora will not develop a second parallel exchange protocol.

### The `.akcp` file

`.akcp` is the **single** standard exchange format of AEX.

- Extension: `.akcp`
- MIME type: `application/vnd.aivora.akcp`
- Layout: fixed magic + JSON header + AES-256-GCM ciphertext
- Crypto: AES-256-GCM with PBKDF2-HMAC-SHA256 (600,000 iterations)
- The whole header is the AAD, so tampering with protocol or crypto parameters breaks decryption
- The header never exposes configuration names, endpoints or API keys

The payload inside is JSON, but that is only an internal serialization detail — **not a second export format**. Users only ever handle `.akcp` files.

### Sharing

Planned flow:

```text
Configuration → Export → generate .akcp (independent credential) → system share sheet → target app
```

- **Two entry points**: Export saves to a user-chosen location, Share opens the system share sheet; both produce byte-identical `.akcp` files
- Uses the native Android Sharesheet; no imitation share panel is drawn
- Sharing writes the file to the app-private cache and hands it out through a FileProvider `content://` URI; real paths are never exposed
- The file carries no credential: the recipient **must enter the 52-character credential separately** to decrypt it, and it is never sent alongside the file
- The user is warned that the file contains a complete API key

### Single and batch

One `.akcp` file can carry a single configuration, a selection, or the full set, always through the same `profiles` array. **No separate batch format**, and the crypto flow is identical.

### How third parties adapt

A receiver only needs to:

1. Validate extension, size and magic;
2. Parse and validate the header parameters;
3. Ask the user for the credential (52 Base32 characters);
4. Derive the key with PBKDF2 and decrypt with AES-GCM (header as AAD);
5. Validate the payload against the schema, show a masked preview and import after confirmation.

No SDK, account, server or source code needed.

## For third-party developers

- **Protocol overview**: [protocol/README.md](protocol/README.md)
- **Full specification**: [protocol/AEX-SPECIFICATION.md](protocol/AEX-SPECIFICATION.md)
- **Crypto and security**: [protocol/AEX-SECURITY.md](protocol/AEX-SECURITY.md)
- **Payload schema**: [protocol/AEX-SCHEMA.json](protocol/AEX-SCHEMA.json)
- **Adapter guide**: [protocol/AEX-ADAPTER-GUIDE.md](protocol/AEX-ADAPTER-GUIDE.md)
- **Test vectors**: [protocol/AEX-TEST-VECTORS.md](protocol/AEX-TEST-VECTORS.md)
- **Official test vectors**: [protocol/test-vectors/](protocol/test-vectors/)
- **Protocol changelog**: [protocol/AEX-CHANGELOG.md](protocol/AEX-CHANGELOG.md)
- **Legacy compatibility**: [protocol/compatibility/legacy-format.md](protocol/compatibility/legacy-format.md)
- **Examples**: [single](protocol/examples/single-profile.md) · [multiple](protocol/examples/multiple-profiles.md) · [encrypted package](protocol/examples/encrypted-package.md)

### Legacy protocol (implemented, still supported)

- **AKCP 1.0 specification**: [AKCP/SPECIFICATION.md](AKCP/SPECIFICATION.md)
- **Chinese adaptation guide**: [AKCP/ADAPTATION.zh-CN.md](AKCP/ADAPTATION.zh-CN.md)
- **Container layout**: [AKCP/format/container.md](AKCP/format/container.md)
- **Cryptography**: [AKCP/cryptography/encryption.md](AKCP/cryptography/encryption.md)
- **Security and error codes**: [AKCP/SECURITY.md](AKCP/SECURITY.md)
- **Python reference implementation**: [AKCP/implementations/python/aivora_akcp.py](AKCP/implementations/python/aivora_akcp.py)
- **Interoperability vectors**: [AKCP/test-vectors/](AKCP/test-vectors/)

## Ecosystem

AEX 1.0 is not yet implemented in any Aivora release, so **no third-party software is currently listed as supported**.

The adapter registry and submission template live in [protocol/ECOSYSTEM.md](protocol/ECOSYSTEM.md). Every entry is labelled with a status:

| Status | Meaning |
|---|---|
| Verified | Maintainers reproduced the import with the public test vectors and have a record |
| Self-declared | The submitter reports completion; not independently verified |
| In progress | Known to be under development |
| Planned | Intent recorded, work not started |

Unverified projects are never marked as fully supported.

Software that does not adapt AEX is unaffected: nothing breaks, it simply cannot receive `.akcp` files directly. Aivora does not require any software to adapt.

## Contributing

### Reporting issues

- **Bugs**: open an issue with reproduction steps, Aivora version, Android version and the relevant error output.
- **Never** paste real API keys or real configuration files into an issue.

### Feature requests

Open an issue describing the scenario and expected behaviour. Aivora is a local tool and will not add cloud sync, accounts or key hosting.

### Protocol adaptation

Implement per the [adapter guide](protocol/AEX-ADAPTER-GUIDE.md), then register using the template in [ECOSYSTEM.md](protocol/ECOSYSTEM.md). For spec questions or ambiguities, open an issue tagged `protocol`.

### Code contributions

```bash
git clone https://github.com/Farewell-coder/Aivora.git
cd Aivora
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Requires JDK 17 and Android SDK 35. Make sure unit tests pass and avoid unrelated refactoring in your change.

## Changelog

### 0.0.5

- **Fixed**: plain `http://` third-party endpoints could not fetch models. The request was blocked by Android's cleartext traffic policy (`CLEARTEXT communication not permitted by network security policy`) and never reached the server. A network security config now permits cleartext HTTP while keeping system certificate validation unchanged.
- **AEX protocol**: implemented AEX 1.0 (`.akcp`, AES-256-GCM with PBKDF2-HMAC-SHA256 at 600,000 iterations, header as AAD). Exports go through a FileProvider into the system share sheet and support single, multi-selected and full-set scopes.
- **Docs**: added the `protocol/` directory and rewrote the main README.

### 0.0.4

- Fixed the system back button exiting the app from the About and Protocol screens.
- Model fetch failures now show the real HTTP status and server message instead of a generic "invalid key".
- Leading and trailing whitespace in keys is stripped before saving and sending.
- Editing a configuration now clears stale models and stale results.
- Fixed a false "copied" toast on empty export credentials, a null pointer on per-model retry, and the UI freezing after a biometric failure.
- APK size reduced from 3,984,449 to 2,705,722 bytes (−32.1%) with no feature or crypto changes.

### 0.0.3

- Added Aivora-specific encrypted full backup and cross-device restore.
- Configuration card actions changed to icon buttons.

### 0.0.2

- Refined the create/edit flow and interface details.

### 0.0.1

- First release: encrypted local configuration management, key diagnostics, AKCP 1.0 import and export.

## License

Aivora is distributed under **AGPL-3.0-only**. See [LICENSE](LICENSE).

Versioning note: application, protocol and crypto-suite versions **evolve independently**. Currently app 0.0.5 · AKCP 1.0 · crypto suite 1; AEX 1.0 is a draft.