# 示例：单个配置

本文只展示**解密后的 Payload 明文结构**，用于理解字段含义。实际的 `.akcp` 文件是二进制加密容器，不能用文本编辑器打开。

完整容器结构见 [AEX-SPECIFICATION](../AEX-SPECIFICATION.md)。

```json
{
  "format": "aivora.exchange/1",
  "protocolVersion": "1.1",
  "exportedAt": "2026-11-01T09:00:00Z",
  "exporter": {
    "name": "Aivora",
    "version": "0.0.8",
    "platform": "android"
  },
  "profiles": [
    {
      "id": "8f3a1c72-2b4d-4e11-9a55-7c0d3e6f1b20",
      "name": "DeepSeek 官方",
      "provider": "deepseek",
      "baseUrl": "https://api.deepseek.com/v1",
      "apiKey": "sk-EXAMPLE-REPLACE-ME",
      "description": "日常对话用",
      "extra": {
        "com.example.notes.pinned": true
      }
    }
  ]
}
```

## 要点

- `profiles` 是数组，单个配置也要包成数组，不是对象。
- 1.1 起不再携带模型列表：接收方拿到 `baseUrl` 与 `apiKey` 后自行请求 `GET {baseUrl}/models`。
- `apiKey` 在这里是明文，因为它位于**已加密**的 Payload 内部；文件本身是密文。
- `extra` 的键必须带命名空间前缀（如 `com.example.notes.pinned`），避免第三方字段冲突。
- 示例中的 `apiKey` 是占位值，任何公开文档都不得出现真实密钥。