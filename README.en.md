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
- [Third-party integration](#third-party-integration)
- [Contributing](#contributing)
- [Changelog](CHANGELOG.en.md)
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
| Multi-format import | Reads Chatbox, Continue, LobeChat, CC Switch, New API, Cherry Studio and plain `.env` files; format is detected from content, with a preview you tick items in before importing |
| Export history | Every export is recorded with its full snapshot and credential; view, copy, re-export, re-share, or long-press to bulk delete |
| Endpoint correction | Adds a missing `/v1` and strips duplicated API suffixes; official template URLs are left untouched, and candidate paths are retried when diagnostics fail |
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

Use the releases page as the source of truth: [github.com/Farewell-coder/Aivora/releases/latest](https://github.com/Farewell-coder/Aivora/releases/latest)

- Artifact name: `Aivora-v<version>.apk` (the version equals `versionName`)
- Per-release detail lives in the [changelog](CHANGELOG.en.md); this page does not repeat the version number, so it cannot fall out of date.

### Installation

1. Download the `.apk` from the releases page.
2. Allow installation from the current browser or file manager when prompted.
3. Open Aivora — no registration or sign-in required.

Requirements: **Android 8.0 (API 26)+**, arm64 devices.

### About signing

Release builds are signed with a fixed certificate, so they upgrade in place. A locally built debug package uses a different signature and cannot be installed over a release build; uninstall it first (this clears app data, so export a backup beforehand).

## AEX exchange protocol

> **Status: AEX 1.1 is finalised and implemented since 0.0.8.**
>
> What is fixed is the **protocol structure and crypto parameters — not the app**. Aivora keeps shipping updates: new features, bug fixes and UI changes never affect future integrations.
> Configuration cards and multi-select generate `.akcp` files, and both show the credential in a dialog first, then let you Export or Share. Single configurations additionally offer a **Deeplink** button that hands the config to an already-integrated app as a plaintext link.
>
> **1.1 is a breaking change**: `profiles[]` no longer carries `models` / `defaultModel`, `protocolVersion` is `1.1` and matched exactly, so 1.0 containers are rejected by a 1.1 implementation. See the [protocol changelog](protocol/AEX-CHANGELOG.md).

### Purpose

Make "move a configuration from app A to app B" follow a published standard. Third-party software does not need a private Aivora format, an Aivora SDK, an account or an Aivora server — only a compliant implementation of the public spec.

AEX is the **complete, standalone and only** exchange protocol of Aivora. There is no second parallel format, and none is planned.

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
Configuration → Export (single card, or multi-select) → dialog shows credential → generate .akcp (independent credential) → Export saves locally, or Share opens the system share sheet → target app
```

- **Two entry points**: Export saves to a user-chosen location, Share opens the system share sheet; both produce byte-identical `.akcp` files
- **Single and batch share one dialog**: batch additionally lists the included configuration names; the credential must always be shown, and skipping the dialog to jump straight to the share sheet is not allowed
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

## Third-party integration

Want your app to read configurations exported by Aivora? Everything you need is under [protocol/](protocol/README.md). No Aivora SDK, no account, no server, and no need to read Aivora's source.

| Entry point | What it covers |
|---|---|
| [Protocol overview](protocol/README.md) | Start here: what the protocol is, how to use it, how to read the docs |
| [Specification](protocol/AEX-SPECIFICATION.md) | Container layout, field definitions, versioning, error codes |
| [Adapter guide](protocol/AEX-ADAPTER-GUIDE.md) | Step-by-step integration (file channel), Android receiver advice and common pitfalls |
| [Deeplink integration](protocol/AEX-DEEPLINK.md) | Deeplink channel: link format, fields, Kotlin sample, testing commands and security requirements |
| [Security model](protocol/AEX-SECURITY.md) | Crypto parameters, key derivation, boundaries, and what not to do |
| [Payload schema](protocol/AEX-SCHEMA.json) | JSON Schema for the payload |
| [Test and acceptance](protocol/AEX-TEST-VECTORS.md) | Acceptance checklist and negative cases |
| [Official test vectors](protocol/test-vectors/) | Ready to run against your implementation |
| [Examples](protocol/examples/) | Single, multiple and encrypted-package walkthroughs |

**Adapters and how to register** are tracked in [protocol/ECOSYSTEM.md](protocol/ECOSYSTEM.md). **Dollhouse** has completed AEX 1.1 integration (file + deeplink), self-declared; other apps are welcome to submit via the template.

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

What each release added, changed and fixed is recorded in the changelog, rather than piled up here.

See [Changelog](CHANGELOG.en.md).

## License

Aivora is distributed under **AGPL-3.0-only**. See [LICENSE](LICENSE).

Versioning note: application and protocol versions **evolve independently**. The protocol is AEX 1.1 (finalised); for the app version see [releases](https://github.com/Farewell-coder/Aivora/releases/latest).

- **Protocol version rules**: a major bump is always breaking; for a minor bump, Aivora matches the version **exactly**, i.e. `protocolVersion` must equal the single version the implementation supports. 1.1 removed the model-list fields, so 1.0 and 1.1 containers are not interchangeable.
- **The app keeps moving**: features, UI, performance and bug fixes ship continuously without requiring a protocol change.