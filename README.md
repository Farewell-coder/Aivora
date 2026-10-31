# Aivora

Aivora is a local-first Android AI service configuration manager built with Kotlin and Jetpack Compose. It stores API keys with Android Keystore-backed encryption and exchanges configurations through the open AKCP protocol using dedicated `.aivora` files.

## Features

- Multiple providers/accounts, encrypted keys, search, default and enable controls.
- Masked key display, separate copy actions and deletion confirmation.
- Provider templates and editable OpenAI-compatible endpoints.
- Single and batch key diagnostics: fetch `/models`, then test each model serially with a minimal `/chat/completions` request; show status, HTTP code, latency and response/error summary, with per-model retry and cancellation.
- Diagnostic results are stored locally. Actual inference requests may consume API quota.
- Authenticated `.aivora` import/export with independent Base32 credentials.
- Import preview and explicit conflict handling.
- Optional Shizuku status detection; core features work without it.
- Dark mode, protocol information and third-party integration links.

No account, cloud synchronization, key proxy, local API server or Aivora backend is used.

## Build

Android 8.0+; JDK 17 and Android SDK 35.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Open protocol

The public specification, [Chinese third-party adaptation guide](AKCP/ADAPTATION.zh-CN.md), schema, errors, synthetic test vectors and an independent Python implementation live in [AKCP/](AKCP/). Compatible applications only accept `.aivora`, then validate the binary container and authentication. Security never depends on hiding source or the suffix.

## License

Aivora is distributed under **AGPL-3.0-only**. See [LICENSE](LICENSE).

Version: 0.0.1. AKCP: 1.0. Crypto suite: 1. These versions evolve independently.