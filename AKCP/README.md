# AKCP — open AI configuration exchange

AKCP is the open protocol used by [Aivora](https://github.com/Farewell-coder/Aivora). Files use the mandatory **`.aivora`** suffix and a public binary container. Security relies on Argon2id and AES-256-GCM—not secret source code, obfuscation or a proprietary suffix.

- [第三方适配指南（中文）](ADAPTATION.zh-CN.md)
- [Full specification](SPECIFICATION.md)
- [Container layout](format/container.md)
- [Cryptography](cryptography/encryption.md)
- [Schema](schemas/config.schema.json)
- [Compatibility](COMPATIBILITY.md)
- [Security and error handling](SECURITY.md)
- [Independent Python implementation](implementations/python/aivora_akcp.py)
- [Test vectors](test-vectors/)

## Minimal integration

1. Accept only `.aivora` filenames.
2. Parse and bounds-check the 64-byte header.
3. Ask the user for that file's independent credential.
4. Derive with Argon2id and authenticate/decrypt using the header as AAD.
5. Reject wrong credentials, modification, truncation and unsupported versions without partial output.
6. Validate the JSON schema, map to your own model, show a masked preview and import only after confirmation.

All examples contain synthetic credentials. Do not publish real user configurations.