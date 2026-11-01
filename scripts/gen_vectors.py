import hashlib, json, base64, os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = bytes([0x41,0x45,0x58,0x31,0x0d,0x0a,0x1a,0x0a])
CRED = "A"*52
SALT = bytes(range(16))
NONCE = bytes(range(12))
ITER = 600000

def build(payload_obj, salt=SALT, nonce=NONCE):
    plain = json.dumps(payload_obj, separators=(',',':'), ensure_ascii=False).encode()
    header = {
        "format":"aivora.exchange/1", "protocolVersion":"1.0",
        "kdf":"PBKDF2-HMAC-SHA256", "iterations":ITER,
        "salt": base64.b64encode(salt).decode(),
        "cipher":"AES-256-GCM",
        "nonce": base64.b64encode(nonce).decode(),
        "tagBits":128, "ciphertextLength": len(plain)+16,
        "payloadEncoding":"json"
    }
    hb = json.dumps(header, separators=(',',':'), ensure_ascii=False).encode()
    aad = MAGIC + len(hb).to_bytes(4,'big') + hb
    key = hashlib.pbkdf2_hmac('sha256', CRED.encode(), salt, ITER, 32)
    ct = AESGCM(key).encrypt(nonce, plain, aad)
    return aad + ct, key, plain

def single():
    return {
      "format":"aivora.exchange/1", "protocolVersion":"1.0",
      "exportedAt":"2026-11-01T00:00:00Z",
      "exporter":{"name":"Aivora","version":"0.0.5","platform":"android"},
      "profiles":[{
        "id":"vector-single","name":"Synthetic Vector",
        "provider":"custom","baseUrl":"https://example.invalid/v1",
        "apiKey":"sk-EXAMPLE-NOT-REAL",
        "models":["model-a","model-b"],"defaultModel":"model-a",
        "description":"synthetic test vector"
      }]
    }

def multi():
    return {
      "format":"aivora.exchange/1", "protocolVersion":"1.0",
      "exportedAt":"2026-11-01T00:00:00Z",
      "exporter":{"name":"Aivora","version":"0.0.5","platform":"android"},
      "profiles":[
        {"id":"vector-a","name":"Alpha","provider":"custom","baseUrl":"https://example.invalid/v1",
         "apiKey":"sk-EXAMPLE-A","models":["m1"],"defaultModel":"m1"},
        {"id":"vector-b","name":"Beta","provider":"custom","baseUrl":"https://example.invalid/v1",
         "apiKey":"sk-EXAMPLE-B","models":["m2","m3"]},
        {"id":"vector-c","name":"Gamma","provider":"custom"}
      ]
    }

out = '/root/appbuild/Aivora/protocol/test-vectors'
os.makedirs(out, exist_ok=True)
results = {}
for name, obj, salt, nonce in [
    ('single.akcp', single(), SALT, NONCE),
    ('multiple.akcp', multi(), SALT, NONCE),
]:
    data, key, plain = build(obj, salt, nonce)
    open(os.path.join(out, name), 'wb').write(data)
    results[name] = {
        'size': len(data),
        'sha256': hashlib.sha256(data).hexdigest(),
        'key_hex': key.hex(),
        'plain_sha256': hashlib.sha256(plain).hexdigest(),
    }
print(json.dumps(results, indent=2))
