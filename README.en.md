# Aivora

[简体中文](README.md) | **English**

Aivora is a local-first Android AI service configuration manager built with Kotlin and Jetpack Compose. API keys are encrypted with a data key protected by Android Keystore. Configurations are exchanged using the open AKCP protocol and dedicated `.aivora` files.

## Features

- Only a configuration name is required; endpoint, API key and notes are optional.
- Blank, custom and mainstream provider templates with editable endpoints.
- Encrypted local storage, search and deletion confirmation.
- Expandable per-configuration model diagnostics: fetch `/models`, test every model serially, show status, HTTP code, latency and response/error summary, with cancellation and per-model retry.
- Uses OpenAI-compatible `/models` and `/chat/completions`; real tests may consume API quota.
- Authenticated `.aivora` import and per-configuration export, connection checks, independent Base32 credentials and user-selected destinations.
- Material 3 light/dark/system themes, Lucide icons, three destination navigation and system authentication.
- Aivora-specific encrypted full backups in Settings; cross-device restores re-encrypt secrets with the new device key after an overwrite confirmation.

No account, cloud synchronization, key proxy, local API server or Aivora backend is used.

## Build

Android 8.0+; JDK 17 and Android SDK 35.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Open protocol

The [AKCP directory](AKCP/) contains the specification, [Chinese adaptation guide](AKCP/ADAPTATION.zh-CN.md), schema, errors, independent Python implementation and bidirectional interoperability vectors. Compatible applications only accept `.aivora`, then validate the binary container and authentication. Security never depends on hidden source or the suffix.

## License

Aivora is distributed under **AGPL-3.0-only**. See [LICENSE](LICENSE).

Version: 0.0.4. AKCP: 1.0. Crypto suite: 1. These versions evolve independently.