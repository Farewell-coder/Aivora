# 示例：多个配置

```json
{
  "format": "aivora.exchange/1",
  "protocolVersion": "1.0",
  "exportedAt": "2026-11-01T09:12:40Z",
  "exporter": {
    "name": "Aivora",
    "version": "0.1.0",
    "platform": "android"
  },
  "profiles": [
    {
      "id": "8f3a1c72-2b4d-4e11-9a55-7c0d3e6f1b20",
      "name": "DeepSeek 官方",
      "provider": "deepseek",
      "baseUrl": "https://api.deepseek.com/v1",
      "apiKey": "sk-EXAMPLE-A",
      "models": ["deepseek-chat", "deepseek-reasoner"],
      "defaultModel": "deepseek-chat"
    },
    {
      "id": "2c7e90ab-4f18-4c33-8d22-1a6b5e9f0c74",
      "name": "自建网关",
      "provider": "custom",
      "baseUrl": "https://gateway.example.invalid/v1",
      "apiKey": "sk-EXAMPLE-B",
      "models": ["gpt-4o-mini", "claude-haiku"],
      "defaultModel": "gpt-4o-mini",
      "description": "内网代理"
    },
    {
      "id": "5b1d3f88-9a2c-4d70-b6e5-3f8c2a4b7d91",
      "name": "仅保存未验证",
      "provider": "custom"
    }
  ]
}
```

## 要点

- 第三个配置只填了名称，`baseUrl`、`apiKey`、`models` 全部缺失。这表示"用户尚未设置"，不是空字符串。
- 若某字段存在但为空串（`"apiKey": ""`），语义是"用户明确清空过该字段"，与缺失不同。
- 数组顺序由导出方决定，通常与用户在列表中的排列一致。
- 接收方遇到重复 `id` 时应按自己的冲突策略处理（跳过 / 另存 / 替换），不得静默覆盖。