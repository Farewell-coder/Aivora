# Public interoperability vectors

Both files contain only synthetic credentials and an `example.invalid` endpoint.

- `python-export.aivora`: created by the independent Python implementation and successfully decrypted by Kotlin.
- `kotlin-export.aivora`: created by Kotlin and successfully decrypted by Python.
- Credential for both vectors: `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA`.

This all-A credential is intentionally public and must never protect real data. Production exports generate independent random 256-bit credentials.