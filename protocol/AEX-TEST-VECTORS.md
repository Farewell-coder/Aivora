# AEX Test Vectors

> 状态：1.0 已冻结并实现。官方向量文件已提供在 [test-vectors/](test-vectors/README.md)，由独立的 Python 实现生成并已被 Kotlin 实现成功解密。

## 1. 原则

- 所有测试数据必须使用**合成凭证与虚构 Endpoint**（如 `https://example.invalid/v1`），禁止使用任何真实用户配置。
- 向量必须可被独立实现复现：同一凭证、同一 Salt、同一 Nonce 应得到逐字节相同的输出。
- 确定性向量只用于测试构造。**生产环境每次导出必须重新生成随机 Salt 与 Nonce。**

## 2. 确定性构造参数

为了让不同语言实现能产出同一份文件，测试向量固定以下随机值：

| 项 | 测试固定值 |
|---|---|
| 凭证 | `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA`（52 个 `A`） |
| Salt | `000102030405060708090A0B0C0D0E0F`（16 字节） |
| Nonce | `000102030405060708090A0B`（12 字节） |
| 迭代次数 | `600000` |
| 明文 | Payload 按 RFC 8785 规范化后的 UTF-8 字节 |

这组凭证是公开的合成值，**绝不能用于保护真实数据**。

## 3. 必需向量清单

| 编号 | 场景 | 期望结果 |
|---|---|---|
| V01 | 单配置，全部字段齐全 | 解密成功，`profiles` 长度 1 |
| V02 | 单配置，仅 `id` + `name` | 解密成功，缺省字段语义为"未设置" |
| V03 | 三配置批量 | 解密成功，顺序与导出顺序一致 |
| V04 | 含 `extra` 命名空间扩展 | 解密成功，额外字段可读 |
| V05 | 错误凭证（改 1 个字符） | `AUTHENTICATION_FAILED`，无任何明文输出 |
| V06 | 密文倒数第 1 字节取反 | `AUTHENTICATION_FAILED` |
| V07 | 头部 `iterations` 被改（如 600000 → 600001） | `AUTHENTICATION_FAILED`（AAD 变化） |
| V08 | 头部 `protocolVersion` 改为 `2.0` | `UNSUPPORTED_VERSION`（参数校验先于解密） |
| V09 | 头部被截断 / 头部长度字段与实际不符 | `INVALID_HEADER` |
| V10 | 文件被截断 16 字节（去掉认证标签） | `AUTHENTICATION_FAILED` 或长度校验失败 |
| V11 | Magic 错误 | `INVALID_MAGIC` |
| V12 | 文件大小 17 MiB | `FILE_TOO_LARGE` |
| V13 | 尾部追加 1 字节 | 拒绝（禁止尾部多余字节） |
| V14 | 旧版 `.aivora` 文件 | 识别为 legacy，不当作 AEX 处理 |

## 4. 跨语言互操作验证

协议冻结后，需要至少两套独立实现互相验证：

- Aivora（Kotlin）导出的文件，参考实现（Python）必须能解密。
- 参考实现导出的文件，Aivora 必须能解密。
- 双方对同一批向量产出的密文应逐字节一致（在固定 Salt/Nonce 的前提下）。

验证脚本见 [`scripts/gen_vectors.py`](../scripts/gen_vectors.py)，向量文件见 [test-vectors/](test-vectors/README.md)。Aivora 的 `AexTest.decryptsPythonGeneratedVectors` 与 `pythonVectorRejectedWithWrongCredential` 已覆盖该验证。

## 5. 验收清单

应用侧需要覆盖：

1. 单配置导出。
2. 多配置导出。
3. 全量导出。
4. 正确凭证解密。
5. 错误凭证拒绝。
6. 文件完整性验证。
7. 头部篡改检测。
8. 密文篡改检测。
9. 非法协议版本拒绝。
10. 缺失字段处理。
11. 未知扩展字段保留。
12. 超大文件拒绝。
13. 重复导出（每次新 Salt/Nonce）。
14. 分享面板取消不产生残留状态。
15. 不同 Android 版本的 URI 授权。
16. 旧版文件导入。
17. 旧版文件导出。
18. 新旧格式识别。
19. 临时文件清理。
20. 导出前后数据完整性对比。
