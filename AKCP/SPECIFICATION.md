# AKCP 1.0 Specification

AKCP (AI Key Configuration Protocol) is an open, offline configuration exchange protocol implemented by Aivora. Software, protocol and cryptography versions are independent.

## Mandatory filename rule

A compatible application MUST only offer import for a non-empty filename ending in lowercase `.aivora`. Renaming the payload to `.json`, `.akcp` or another suffix MUST make it ineligible at the file-selection boundary. The suffix is an ecosystem identifier, not a security control. Exporters MUST use `.aivora`.

## Container

All integer fields are unsigned big-endian. Header size is 64 bytes; maximum complete file size is 16 MiB.

| Offset | Size | Meaning |
|---|---:|---|
|0|8|Magic `41 4b 43 50 0d 0a 1a 0a`|
|8|2|Protocol version = 1|
|10|2|Container version = 1|
|12|2|Cryptography version = 1|
|14|1|KDF ID: 1 = Argon2id|
|15|1|Cipher ID: 1 = AES-256-GCM|
|16|1|Flags, currently 0|
|17|1|Header length = 64|
|18|4|Argon2id memory in KiB = 65536|
|22|4|Argon2id iterations = 3|
|26|4|Argon2id parallelism = 1|
|30|4|Ciphertext length including 16-byte tag|
|34|16|Random salt|
|50|12|Random nonce|
|62|2|Reserved = 0|
|64|N|AES-GCM ciphertext and authentication tag|

Every export MUST use a fresh 256-bit CSPRNG credential, fresh 128-bit salt and fresh 96-bit nonce. Credentials use RFC 4648 Base32 without padding, formatted in seven 8-character groups (52 symbols, final quantum zero-padded in the conventional Base32 bit conversion). Hyphens and ASCII case are ignored only during input normalization; no partial/default credential is allowed.

Derive 32 bytes with Argon2id using the normalized 52-symbol ASCII credential, the salt and the header parameters. Encrypt the UTF-8 JSON payload using AES-256-GCM. AAD is exactly the 64-byte header. Validate all lengths, supported versions and KDF bounds before expensive derivation. Any authentication failure MUST fail the entire import without returning partial plaintext.

## Payload

Root object: `schema` = `akcp.config-set/1`, `configs` = array. Core fields per item: `config_name`, `provider`, `api_key`, `endpoint`; optional fields: `models` (string array), `default_model`, `notes`, `enabled`, `extensions` (object).

Unknown object members must be preserved when possible. Extensions MUST use a namespace such as `extensions.example.*`. An implementation that cannot retain values MUST disclose that before import. It must not silently reinterpret or delete unknown data.

## Optional embedded containers

Level 3 readers may locate a valid full container inside surrounding data, then apply exactly the same bounds, version and authentication checks. Magic alone never proves validity. Standard-position readers remain Level 1 compatible.

## Compatibility levels

- Level 1: suffix and binary recognition, Argon2id/AES-GCM import, core fields, preview.
- Level 2: full fields, export, unknown-field preservation, conflict handling and error codes.
- Level 3: extensions, embedded-container scanning and future crypto suites.

No server, Aivora SDK, online password validation or Aivora application is needed. After authorized decryption, the receiving application can access plaintext API keys and must protect them appropriately.