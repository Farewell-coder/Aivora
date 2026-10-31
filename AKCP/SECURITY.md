# Security

Public source and protocol documentation do not make user files public. A valid independent credential remains necessary. Conversely, the `.aivora` suffix and R8 obfuscation are not security boundaries.

Errors: `INVALID_EXTENSION`, `INVALID_CONTAINER`, `UNSUPPORTED_VERSION`, `AUTHENTICATION_FAILED`, `INVALID_PAYLOAD`, `FILE_TOO_LARGE`. User-facing messages may distinguish wrong credentials from malformed files but must not expose plaintext, credentials or unnecessary cryptographic details. Authentication failure must yield no partial configurations. Use synthetic keys for tests. A recipient that decrypts a file has plaintext API keys and is responsible for safe storage. No absolute security guarantee is made for a fully compromised device.

Report vulnerabilities through GitHub Security Advisories rather than publishing live credentials.