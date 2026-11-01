# AEX 官方测试向量

本目录的 `.akcp` 文件由**独立的 Python 参考实现**生成，用于第三方验证自己的解密实现是否符合 AEX 1.0。

## 文件清单

| 文件 | 内容 | 大小 | SHA-256 |
|---|---|---:|---|
| `single.akcp` | 单个配置，字段齐全 | 679 字节 | `b8cb82c5ea37c27f8d6a7f3078200908dfcf85a5e3dafb92bc0ac0ac09e226a5` |
| `multiple.akcp` | 三个配置，含缺省字段的条目 | 787 字节 | `b6eec5289a2f18973f7c862399a9a99627841e5457d9687892bed53ea1b9662f` |

## 解密参数

两个文件使用**完全相同**的确定性参数，便于复现：

| 项 | 值 |
|---|---|
| 凭证 | `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA`（52 个 `A`） |
| Salt | `000102030405060708090A0B0C0D0E0F`（16 字节） |
| Nonce | `000102030405060708090A0B`（12 字节） |
| 迭代次数 | 600000 |
| 派生密钥 | `5b6b77bf572354c79d88aaea9359e2a2d0f3bc67eba089901440133b351f8d19` |

派生出的密钥相同，是因为凭证与 salt 相同；nonce 也相同，所以两个文件的密文不同仅来自明文差异。**这种 nonce 复用只出现在测试向量中，生产环境必须每次重新生成随机 salt 与 nonce。**

## 期望解密结果

### `single.akcp`

```json
{
  "format": "aivora.exchange/1",
  "protocolVersion": "1.0",
  "exportedAt": "2026-11-01T00:00:00Z",
  "exporter": { "name": "Aivora", "version": "0.0.5", "platform": "android" },
  "profiles": [
    {
      "id": "vector-single",
      "name": "Synthetic Vector",
      "provider": "custom",
      "baseUrl": "https://example.invalid/v1",
      "apiKey": "sk-EXAMPLE-NOT-REAL",
      "models": ["model-a", "model-b"],
      "defaultModel": "model-a",
      "description": "synthetic test vector"
    }
  ]
}
```

### `multiple.akcp`

三个配置，名称依次为 `Alpha`、`Beta`、`Gamma`。`Gamma` 只有 `id`、`name`、`provider`，用于验证缺省字段语义。`Beta` 的 `models` 为 `["m2","m3"]`，顺序必须保留。

## 负向用例

用下列方式处理向量，你的实现必须拒绝，且**不得输出任何部分明文**：

| 用例 | 期望结果 |
|---|---|
| 凭证改为 `BBBB…`（52 个 B） | 认证失败 |
| 密文最后一个字节取反 | 认证失败 |
| 头部 `iterations` 改为 600001 | 认证失败（header 是 AAD） |
| 头部 `protocolVersion` 改为 `2.0` | 不支持的版本（参数校验先于解密） |
| 文件截断 32 字节 | 头部/长度校验失败 |
| 首字节改成 `0x00` | 非法 Magic |

## 自行复现

`scripts/gen_vectors.py` 包含生成逻辑，仅依赖 Python 标准库与 `cryptography`。它以相同参数重新生成这两个文件，可用于核对你的实现是否逐字节一致：

```bash
python3 scripts/gen_vectors.py
```

## Aivora 自身的验证

Kotlin 实现的 `AexTest.decryptsPythonGeneratedVectors` 会读取本目录的两个文件并解密，断言字段内容正确；`pythonVectorRejectedWithWrongCredential` 断言错误凭证被拒绝。这两项测试在每次构建时运行，是真实的跨语言互操作证据，而非仅文档声明。

## 注意

这些凭证是公开的合成值，仅用于测试。任何情况下都不得用于保护真实配置。
