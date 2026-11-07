# 示例：加密包结构

`.akcp` 是二进制容器，不能用文本编辑器直接查看。本页说明一个完整加密包的字节布局与构造过程。

## 1. 字节布局示意

```text
偏移    长度    内容
0       8       41 45 58 31 0D 0A 1A 0A      ← Magic "AEX1"
8       4       00 00 00 F7                  ← 头部长度 H = 247（大端）
12      H       {"format":"aivora.exchange/1",...} ← 紧凑 JSON 头部（明文，仅协议参数）
12+H    N       ...                          ← AES-256-GCM 密文（末尾 16 字节为标签）
```

`H = 247` 是按下方第 2 节那 10 个字段、紧凑 JSON（无空格无换行）序列化得到的实际长度，
与官方测试向量 `single.akcp` 的头部长度一致。字段值一变，`H` 随之变化。

头部是**明文**的，但它不包含任何用户数据；它同时作为 AAD 参与认证，所以被改动一个字节就会导致解密失败。

## 2. 头部示例

```json
{
  "format": "aivora.exchange/1",
  "protocolVersion": "1.1",
  "kdf": "PBKDF2-HMAC-SHA256",
  "iterations": 600000,
  "salt": "AAECAwQFBgcICQoLDA0ODw==",
  "cipher": "AES-256-GCM",
  "nonce": "AAECAwQFBgcICQoL",
  "tagBits": 128,
  "ciphertextLength": 512,
  "payloadEncoding": "json"
}
```

- `salt` 是 16 字节（示例为 `00…0F`）的 Base64。
- `nonce` 是 12 字节的 Base64。
- 实际导出时这两个值必须来自 CSPRNG，每次不同。

## 3. 构造步骤

```text
1. payloadBytes = 紧凑 JSON（无多余空白）的 UTF-8 字节；键序为插入序（非 RFC 8785 排序）
2. salt   = random(16)
   nonce  = random(12)
3. header = JSON 序列化（字段顺序固定）
4. aad    = magic ‖ uint32be(len(header)) ‖ header 原始字节
5. key    = PBKDF2-HMAC-SHA256(credential, salt, 600000, 32)
6. ct‖tag = AES-256-GCM-Encrypt(key, nonce, payloadBytes, aad)
7. file   = magic ‖ uint32be(len(header)) ‖ header ‖ ct ‖ tag
```

## 4. 解密校验顺序

```text
1. 文件大小 ≤ 16 MiB？
2. Magic 匹配？
3. 8–12 字节的 H 在 64–8192 之间？
4. 12 + H + ciphertextLength == 文件总长度？
5. 头部 JSON 可解析且字段齐全？
6. kdf / cipher / tagBits / iterations 在白名单内？
7. salt 解码 16 字节、nonce 解码 12 字节？
8. 派生密钥，GCM 解密并验证标签
9. Payload 通过 JSON Schema
```

任何一步失败都不得输出部分明文。

## 5. 常见误实现

| 错误做法 | 后果 |
|---|---|
| 解析头部后重新序列化再算 AAD | 空白/键序变化 → 认证必然失败 |
| 用 `endpoint` 反推 `provider` | 违反"禁止猜测映射" |
| 忽略 `iterations` 直接用默认值 | 解密失败或资源耗尽 |
| 用其他文件的凭证尝试解密 | 提示"凭证错误或文件已损坏" |
| 把 Payload 的 `apiKey` 写日志 | 密钥泄漏 |