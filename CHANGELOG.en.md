# Changelog

What each release added, changed and fixed is recorded here, so the README does not turn into one long list.

[简体中文](CHANGELOG.md)

---

## v0.0.6 · Export history, multi-format import, automatic endpoint correction

### New: export history

The bottom navigation grows from three items to four; the fourth is **History**.

One record corresponds to **one export action**, not to the number of configurations — exporting five configurations at once produces one record containing all five.

- Total count at the top, list grouped by date
- Each record keeps a **full snapshot** of what was exported (including API keys, encrypted with the device master key), so "re-export" reproduces the original file rather than rebuilding from the current state
- The detail sheet shows that export's credential (monospaced, selectable), copies it, and re-exports or re-shares
- Long-press any record to enter selection mode; multi-select and delete in one confirmed action
- If the source configuration has since been deleted, the record shows a grey "original configuration deleted" note

Records created before 0.0.6 have no snapshot; the detail sheet says so and still shows the credential.

### New: import from third-party applications

Import no longer accepts only our own `.akcp`. Format is decided **by content structure, never by file extension**:

| Source | Signature | Credential required |
|---|---|---|
| Aivora AEX (`.akcp`) | file magic | yes |
| Legacy AKCP (`.aivora`) | file magic | yes |
| Chatbox | `settings.apiHost` / `settings.apiKey` | no |
| Continue | `models[].apiKey` / `apiBase` | no |
| LobeChat | `keyVaults.openai.apiKey` / `baseURL` | no |
| CC Switch | `env.ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL` | no |
| New API / One API | `key` / `base_url` / `models` | no |
| Cherry Studio | `data.json` inside a ZIP (double-stringified) | no |
| Generic .env | `OPENAI_API_KEY=` / `OPENAI_API_BASE=` | no |

Third-party parses land in an **import preview**: pick items, rename them, and see the source badge and whether a key is present. When nothing is recognised the dialog lists every supported format instead of a bare "unsupported format".

Imported configurations carry a source marker that survives backup and restore.

### New: automatic endpoint correction

Endpoints previously had to include `/v1` themselves. A bare address was concatenated into `http://x:54604/models` and 404'd; a full interface URL had its suffix duplicated.

Normalization runs in three tiers:

- **Template allowlist** (OpenAI, DeepSeek, Moonshot, Zhipu, DashScope, SiliconFlow, OpenRouter, Anthropic, Gemini, Ollama): exact host match, **not a single character is touched**
- **Third-party** (IP literal, non-standard port, private range, relay markers in the path, or simply not on the allowlist): a ten-step rewrite — trim, add the missing scheme (http for IPs and private ranges, https for domains), lowercase version segments, collapse duplicate slashes, strip the trailing slash, strip terminal segments (`/chat/completions`, `/completions`, `/responses`, `/messages`, `/embeddings`, `/models`), append `/v1`
- **Undecidable** (no relay marker, no version segment, interface-layer name at the end): left untouched with a note asking for manual confirmation — nothing is guessed

Example: `http://103.236.89.43:54604` → `http://103.236.89.43:54604/v1`

The correction appears as grey text under the field. **Saved configurations are never modified**; the value is written only when you save the editor.

When `/models` fails outright, diagnostics try up to three candidate paths. Success requires HTTP 200 **and** a parseable `data` array — relay services often return 200 on errors, so status alone would mislead.

### New: "Endpoint correction" switch in Settings

The first card in Settings, on by default. Turning it off disables normalization, candidate probing and the note, and applies only to future operations.

### Reworked: selection mode on the configurations screen

- The fixed bottom action bar **replaces** the bottom navigation; two stacked bars would be noise
- "New" and "Import" are hidden while selecting
- Each card keeps only its checkbox — edit, export, copy and delete are hidden to prevent mis-taps
- Added select-all / deselect-all
- Added bulk delete; the confirmation states the count and nothing else
- Removed the "Export all" button, now redundant with select-all

### Fixed: exported files could not be imported

This was a functional defect in 0.0.5 and earlier.

Export went through `Aex.export()` (`.akcp`, PBKDF2) while import went through `Akcp.requireFilename()` + `Akcp.import()` (which requires `.aivora`, Argon2id). The two criteria were incompatible, so **any file this app produced was rejected outright by its own import picker**.

Import now dispatches on `Aex.detect()`: `AEX` decrypts as AEX, `LEGACY` decrypts as AKCP, `UNKNOWN` goes to third-party parsing.

### Fixed: the protocol screen described the wrong format

The Protocol screen claimed the `.aivora` suffix and Argon2id key derivation, while exports actually produce `.akcp` with PBKDF2-HMAC-SHA256. The text now matches the implementation and documents multi-format import.

### Other corrections

- AEX header version check tightened from `startsWith("1.")` to an exact `1.0`, so a future incompatible 1.x cannot be mistaken for compatible
- User-Agent and the version string on the About screen now read the real `versionName` instead of a hard-coded literal
- Third-party parsing now uses `kotlinx.serialization`'s `JsonElement`, which sidesteps R8 reflection stripping and runs under unit tests

### Database migration

`version 2 → 3`; every added column is nullable, so no data is lost:

- `service_configs.source`: import origin; empty on old rows, which simply hides the badge
- `export_records.configNames`: the names exported at the time
- `export_records.encryptedSnapshot`: the encrypted snapshot

The full-backup schema becomes `aivora.full-backup/2` and still reads `/1`.

### Verification

- **All 59 unit tests pass** (`0 failures / 0 errors`), including 18 new endpoint-normalization cases and 10 third-party parsing cases
- Installed over the previous version on a real device (`versionCode=6`, no data loss)
- Signing certificate SHA-256 `9737ec4b99ec130c5358a9a3aac9ce3e62a2f47c78f0114fd1442d5c0343875b`, identical to earlier releases, so it upgrades in place

### Not yet verified

- Real export files from third-party apps (Chatbox, Cherry Studio, …) — test fixtures were built from the documented field structures, but no actual export file has been obtained yet
- A real third-party app importing an `.akcp`

---

## v0.0.5 · Cleartext endpoints work, configurations travel between apps

### Fixed: models could not be fetched from `http://` endpoints

Third-party endpoints served over plain HTTP could not fetch models. The error was:

```
CLEARTEXT communication to <host> not permitted by network security policy
```

The cause was neither the server nor the key: since Android 9, cleartext traffic is blocked by default, so the request was stopped before a connection was ever made and **never left the device**. Testing the same endpoint with `curl` on a desktop returned success, which is why this was long misread as "the endpoint is fine, the app is broken".

A network security config now permits cleartext. **System certificate validation is unchanged** — user certificates are still not trusted and TLS verification stays on. HTTPS behaviour is unaffected.

### New: AEX 1.0 configuration exchange protocol

Configurations are no longer data only Aivora can read. Exporting produces a standard `.akcp` file that other software can read from the public specification.

- Container: 8-byte magic + 4-byte header length + UTF-8 JSON header + AES-256-GCM ciphertext
- Crypto: AES-256-GCM (128-bit tag), PBKDF2-HMAC-SHA256 at 600,000 iterations, 16-byte salt, 12-byte nonce
- Integrity: the entire header is the AAD, so tampering with the protocol version or crypto parameters breaks decryption
- Scope: single, multi-selected and full-set exports, all using the same `profiles` array
- Limits: 16 MiB per file, header length 64–8192 bytes

### Export and share are two separate entry points

The export dialog on each configuration card offers both:

| Entry | Behaviour |
|---|---|
| **Export** | Opens the system "save to…" dialog and writes wherever you choose |
| **Share** | Builds a `content://` URI via FileProvider and opens the native Android share sheet |

Both produce **byte-identical files** through the same crypto path. Temporary files live in the app's private cache, expire after 10 minutes, and are capped at 20.

### Credentials are separate from the file

An `.akcp` file carries nothing that can decrypt it — the header holds only protocol parameters, the payload holds only configuration data, and the filename holds no credential.

The dialog shows the 52-character credential unique to that file. You pass it separately: say it out loud, send it in another message, or sync it through a password manager. Without it the recipient cannot decrypt, so intercepting the file alone reveals nothing. The protocol defines no credential channel and offers no recovery.

### Protocol documentation

A new `protocol/` directory lets third parties integrate without reading the source:

- Full specification, security model, JSON Schema
- Adapter guide with Android receiver advice and error codes
- Official test vectors generated by an independent Python implementation, with SHA-256, fixed parameters and expected plaintext
- Single, multiple and encrypted-package examples

### Verification

- **All 30 unit tests pass** (`0 failures / 0 errors`), covering round-trip export, batch ordering, wrong credentials, ciphertext tampering, header tampering, unsupported versions, truncated files, format detection and credential normalization
- **Cross-language interoperability is measured**: vectors produced by Python are decrypted and asserted by the Kotlin implementation, on every build
- Installed over the previous version on a real device (`versionCode=5`, `firstInstallTime` unchanged, no data loss); cold start 572 ms
- Signing certificate SHA-256 `9737ec4b99ec130c5358a9a3aac9ce3e62a2f47c78f0114fd1442d5c0343875b`, identical to earlier releases, so it upgrades in place

### Not yet verified

- A real third-party app importing an `.akcp` (nothing is adapted yet, so there is nothing to test against)
- Cleanup timing after cancelling the share sheet
- A third language implementation beyond Kotlin and Python

---

## v0.0.4

- Fixed the system back button exiting the app from the About and Protocol screens.
- Model fetch failures now show the real HTTP status and server message instead of a generic "invalid key".
- Leading and trailing whitespace in keys is stripped before saving and sending.
- Editing a configuration now clears stale models and stale results.
- Fixed a false "copied" toast on empty export credentials, a null pointer on per-model retry, and the UI freezing after a biometric failure.
- APK size reduced from 3,984,449 to 2,705,722 bytes (−32.1%) with no feature or crypto changes.

---

## v0.0.3

- Added Aivora-specific encrypted full backup and cross-device restore.
- Configuration card actions changed to icon buttons.

---

## v0.0.2

- Refined the create/edit flow and interface details.

---

## v0.0.1

- First release: encrypted local configuration management, key diagnostics, configuration import and export.