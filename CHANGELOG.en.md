# Changelog

What each release added, changed and fixed is recorded here, so the README does not turn into one long list.

[简体中文](CHANGELOG.md)

---

## v0.1.2 · Unified input field, floating labels

This release folds every input into a single component and switches to floating labels. **UI only — no changes to data structures, crypto parameters or the AEX protocol**, so it upgrades in place.

### Floating labels

Some fields previously had only a placeholder: once you typed something the hint vanished and you could no longer tell what the field was. They now use a floating label:

- With no input, the label sits inside the field
- On focus or when a value exists, the label shrinks and moves above the field, staying visible
- The placeholder becomes supplementary help text rather than the only hint

Covers every input: configuration name, provider template, API endpoint, API key, notes, backup credential, import credential and the search box.

### Endpoint hint follows the field

The grey endpoint-correction hint moved into the field's own supporting text, so it is bound to the field instead of floating loose above it.

### Other

- New shared input component, reused by future forms; radius and spacing come from the same tokens
- Version 0.1.2 (versionCode 12)

### Verification

- CI build passes, unit tests unchanged
- No schema changes; upgrading loses no data

---

## v0.1.1 · Straightening what was off

The previous release swapped in Lucide's official geometry and added line heights. The direction was right, but three things were left untidy; this release cleans them up.

### Bottom navigation: the four icons are finally evenly spaced

The four labels differ in length — four characters for "Service configurations", two for "Settings". The pill width follows the label, and the old layout distributed gaps by each item's own width, so the **four icon centres were visibly uneven** and "Settings" sat narrow and off to the edge, making the whole row look shifted.

Each item now gets an equal share, so centre spacing is identical.

The navigation label line height is gone as well: a single line of text plus line height puts the extra space *below* the glyphs, pushing the label low inside the pill — exactly the problem the previous release introduced.

### Empty states: no longer a lone perfect circle

Surrounded by rounded rectangles, a 56dp perfect circle in the middle visually "pops out". It is now a **rounded square** of the same size (18dp radius, between the 20dp card and the 14dp inner block), which restores the hierarchy.

Both empty-state icons are now 24dp; previously one was 26 and the other 24.

### Single-line text back to the visual centre

The "A" badge in the page header and the various single-line labels dropped their line height.

**Line height only makes sense for multi-line text.** On a single line it does not improve legibility; it just shifts the glyphs down inside their container. Multi-line description text keeps its line height, which was correct.

### A type scale, filled in along the way

The previous release added line heights case by case, which fixed "cramped text" but not "two sizes on one page". Type sizes are now collected into six steps (22/18/15/14/13/12), mapped onto Material 3 semantic slots, with line height uniformly = size × 1.45. New code uses the slots instead of literals.

### Version

`0.1.1` (versionCode 11)

---

## v0.1.0 · Clearer to read, easier to use

No functional changes. This release handles two long-standing annoyances: **text crammed together** and **icons that looked wrong**.

### Icons: switched to Lucide's official geometry

These were hand-drawn paths; some looked fine, others were visibly off:

| Where | Before | After |
|---|---|---|
| Copy | A malformed box | Lucide `copy` |
| Share | Actually an upload arrow | Lucide `share-2` (three linked nodes) |
| Settings | Sliders | Lucide `settings` |
| Lock | Rectangular outline | Lucide `lock` |

The whole icon set now comes from the official Lucide library (`com.composables:icons-lucide-android`, 1521 icons, MIT), adding semantics that previously needed text: key, server, file in/out, deeplink, warning, success and refresh.

**Adding a dependency has a cost, so here is why**: nobody can eyeball-check a hand-drawn path for correctness, and "nobody noticed it was copied wrong" is exactly how icon styles drift. With the official library, icon geometry is no longer ours to maintain.

### Typography: line height at last

Before the rework, `lineHeight` appeared **zero** times in the project. Font sizes were set in 54 places — all as inline `fontSize = 12.sp` — leaving line height to the font default. CJK multi-line copy needs more leading than a Latin default provides, so lines ended up touching.

All 54 font sizes now carry a matching line height (size × 1.45), a practical range for CJK readability: 12sp with 18sp, 15sp with 22sp, 20sp with 29sp.

### Removed an unexplained gap

There was always a band of blank space under the list page title with no obvious source. The root cause was the status bar height being counted twice:

- `Scaffold`'s `contentWindowInsets` already made room
- A second `Spacer(windowInsetsTopHeight(statusBars))` was added below it

The latter is gone. The first screen now shows one more row.

### Lists no longer jump

The configuration list and export history gained `animateItem()`, so items make room smoothly on create, delete and filter instead of the whole column snapping at once.

### Other

- Header height changed from a hard 54dp to a minimum, so the title is no longer clipped at larger system font sizes
- Version `0.1.0` (versionCode 10)

---

## v0.0.9 · Interaction rework

UI and interaction only — **no changes to data structures, crypto parameters or the AEX protocol**: configurations, export history and backup files are untouched, so it upgrades in place.

### Six interaction changes

**Press feedback**
Buttons, cards and the bottom navigation scale slightly on press and spring back; page changes and long-press selection give a light tactile cue.

**Selection that stays in sync**
In selection mode the select-all box becomes tri-state: none, partial (a middle bar), all. Selected cards get a primary-colour outline, and "Export selected" / "Delete selected" grey out when nothing is selected.

**Cards expand smoothly**
Collapsible cards on the settings page open smoothly, the chevron rotates 180°, and the whole row is tappable rather than only the small chevron on the right.

**Editing moved to a bottom sheet**
"New / edit configuration" changed from a centre-screen dialog to a sheet sliding up from the bottom: drag it down to dismiss, and releasing halfway snaps it back. The keyboard no longer squashes the fields.

**Smoother scrolling**
The configuration and export-history pages use lazy lists, rendering only what is on screen, so long lists stay responsive.

**Page transitions**
Switching pages via the bottom navigation expands the new page outward from the bottom centre over 320ms.

### Visual consistency

- Both palettes gained container levels, so dark mode no longer blurs cards into one mass
- Radius, spacing and type sizes collapsed into one set of tokens; cards have muted and outlined tiers
- Empty states unified as icon + description + primary action
- The title bar is fixed and no longer scrolls away with the list

### Known limits

- Drag-to-reorder is not supported (it would need a sort field on the configuration table; out of scope here)
- The key diagnostics page is still a plain scrolling list, not lazily rendered

### Verification

- Unit tests and the build run in CI
- No changes to the `ServiceConfig` / `ExportRecord` schema; upgrading loses no data

### Supported apps

**Dollhouse 0.2.2** has completed AEX 1.1 integration:

- Deeplink: `dollhouse://aivora-import` (pick a configuration in Aivora → Deeplink → choose Dollhouse)
- File: `.akcp` import, single / multiple / full set
- AEX 1.1 carries no model list, so Dollhouse requests `/models` itself after import

See `protocol/ECOSYSTEM.md` for status and registration.

---

## v0.0.8 · Deeplink export and AEX 1.1

This release adds a second way to hand a configuration to another app, and bumps the protocol to 1.1. **The protocol change is breaking**: 1.0 `.akcp` files cannot be imported on 0.0.8, and vice versa.

### New: Deeplink export

The export dialog gains a **Deeplink** button, shown only for single-configuration exports.

- Tapping it checks whether any registered receiver is installed; if not, it shows "no app supports deeplink transfer"
- Otherwise a plain name list appears; picking one opens that app and Aivora moves to the background
- Not encrypted, no credential, no export record, never copied to the clipboard

Dollhouse is supported first. Integration guide: `protocol/AEX-DEEPLINK.md`.

### Changed: AEX 1.1 no longer exports the model list

Neither `.akcp` nor deeplinks carry `models` / `defaultModel` any more. Receivers fetch `GET {baseUrl}/models` themselves.

Reason: model catalogues change constantly, so the snapshot taken at export time goes stale quickly; and naming differs across apps, so copying it over creates dirty data.

### Breaking-change notice

Version matching is exact. After upgrading to 0.0.8, previously exported 1.0 files are rejected with "this file was created by a newer version, please upgrade the app" and must be re-exported.

### Docs

New `protocol/AEX-DEEPLINK.md` (full deeplink integration guide); adapter guide, specification, schema, security doc, ecosystem page and test vectors all updated to 1.1.

---

## v0.0.7 · Transparent system bars, new icon

This release is purely visual plus system-bar handling. No functional or data-structure changes — configs, export history and backups are untouched.

### Reworked: status and navigation bars go fully transparent (edge-to-edge)

The status bar used to be its own colour strip; the page background stopped dead underneath it. Now the background runs to the very top and the system bar is just icons floating over it.

- One entry point only: `enableEdgeToEdge()` in `MainActivity.onCreate` — no per-screen configuration
- All four tabs, the lock screen, About and the Protocol page behave the same
- Content is inset via `WindowInsets`, so headings are never covered by the clock or battery icons
- The bottom navigation keeps its existing `navigationBarsPadding()`, so the gesture area gets no extra dead space

### Fixed: the lock screen stayed light in dark mode

The lock screen was wrapped in a bare `MaterialTheme`, which follows the **system** light/dark state rather than the appearance chosen inside the app. Set the app to dark while the system stayed light, and the lock screen rendered light.

It now uses the same `AivoraTheme` as the rest of the app, so it follows the app setting.

### Fixed: status bar icon colour did not follow the theme

On dark backgrounds the icons never switched to light, making them hard to read. The cause: the colour was set once when the Activity was created and never re-applied after a theme switch.

`AivoraTheme` now sets `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` inside a `SideEffect`, driven by the current light/dark value, so a switch takes effect immediately.

### New: application icon

- Replaced every `ic_launcher.png` / `ic_launcher_round.png` / `ic_launcher_foreground.png` under `mipmap-*`
- The adaptive-icon background changed from the old `#7466DC` to `#FFFFFF`: the new artwork carries its own white base and fills the canvas, so the old purple showed as a fringe under circular masks
- The foreground sits in a 108dp canvas at roughly 86% scale, so circular, squircle and rounded-square masks all crop it safely

### Launch window

Added `Theme.Aivora` with transparent system bars and a `windowBackground` matching the theme background, so a cold start no longer flashes the default white status bar. In dark mode the launch background is dark (`values-night`).

### Verification

- All 59 unit tests pass (`0 failures / 0 errors`)
- In-place install verified on device (`versionCode=7`, data preserved)

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

### Fixed: bulk export gave no credential

An old defect that dates back to 0.0.5 and shipped with the initial 0.0.6 release.

"Export selected" called `shareProfiles()`, a straight line from "generate" to "raise the system share sheet" — no dialog at all. The credential was created in memory and never surfaced, so the user could neither read nor copy it. The recipient got a file with no key, which is a failed hand-off.

Single export went through `ExportDialog`, which does show the credential. Two code paths, two different behaviours.

Both now share one dialog (`ExportSheet`):

- Single and bulk behave identically: the credential is shown first and can be copied, then the user picks Export or Share
- Bulk additionally lists the included configuration names; the title reads "Export N configurations" and the file is `Aivora-N-configs.akcp`
- Credential derivation (600,000 PBKDF2 rounds) runs off the main thread; the buttons show a spinner and stay disabled while it runs
- Removed `shareProfiles()` and `ExportDialog` (`ExportSheet` supersedes the latter)

Record timing was corrected along the way: `recordExport` used to run **before** `startActivity`, so backing out of the share sheet still left a record behind — an orphan entry for a file nobody received. It is now written after the local write succeeds, or just before the share sheet is raised; closing the dialog writes nothing.

### New: long-press to enter selection mode

Long-pressing any configuration card now enters selection mode and selects that card, matching the gesture already used on the export history screen. Previously selection could only be entered from the top bar.

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