# 验证记录

日期：2026-10-28。全部测试使用新建的本地数据库和测试世界，没有访问生产数据库、玩家存档或真实经济账户。

## 构建与单元 / 组件测试

- JDK 25、Gradle 9.4.1；运行 `test` 与 `shadowJar`。
- SQLite 使用实际 JDBC 驱动与真实 SQL 事务，付款协调器使用真实数据库与可控扣款结果，不伪造 JavaPlugin 或使用 Unsafe。
- 覆盖库存竞争、余额与次数回滚、称号所有权与期限、陈旧编辑版本、同一订单重复回调、付款未知状态、人工核对竞争、导入事务及幂等、旧文字转换、表单验证、玩家退出与缓存刷新竞争。
- PostgreSQL 17 和 MySQL 8.4.13 在独立容器、回环地址运行同一份存储契约。默认 `./gradlew test` 会跳过外部数据库测试，不把跳过项当成通过。

本地默认测试结果：**64 项通过、0 失败、15 项外部数据库测试按条件跳过**。其中包含 15 项 SQLite 存储契约、12 项付款协调器测试及导入、文字、UI、缓存测试。随后使用项目 Gradle 对外部数据库单独运行：**PostgreSQL 17.11 为 15/15 通过，MySQL 8.4.13 为 15/15 通过，均无跳过**。

外部数据库测试会重建其测试表，必须使用专用测试库。只有明确设置以下变量才会运行，URL 限定为回环地址且数据库名为 `deertitle`：

```bash
export DEERTITLE_TEST_ALLOW_RESET=isolated-local
export DEERTITLE_TEST_DATABASE_TYPE=postgresql # 或 mysql
export DEERTITLE_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:15432/deertitle'
export DEERTITLE_TEST_DATABASE_USERNAME='<测试用户>'
# 通过当前环境配置 DEERTITLE_TEST_DATABASE_PASSWORD，不把真实密码写进仓库。
./gradlew test --tests '*ExternalTitleStoreTest' --rerun-tasks
```

## 真实服务端与数据库

下载官方构建并校验哈希。最早 Paper 26.1.1 build 4 的 56 个相关 Dialog / 调度类与编译基线 build 8 字节一致，之后还运行真实服务端验证。

测试矩阵包含：

| 服务端 | 构建 | 数据库 |
| --- | --- | --- |
| Paper 26.1.1 | 4 | SQLite |
| Paper 26.1.2 | 74 | SQLite |
| Paper 26.2 | 135 | SQLite |
| Paper 26.3 | 227 | SQLite、PostgreSQL、MySQL |
| Folia 26.1.2 | 8 | SQLite |
| Folia 26.2 | 7 | SQLite |
| Folia 26.3 | 5 | SQLite |

启动检查包括插件启用、状态命令、待核对订单查询、合成 MiniPlayerTitle 数据预览与确认、重复导入、源数据库哈希不变和正常关闭。

上述 **9 个服务端/数据库组合均通过**，使用同一份安装 JAR：

```text
SHA-256 959fd3aefcdc3de2787e840b94d09fb661104bc0206529ff899f8fd54cd159ab
```

## 原生 Dialog 与经济集成

使用本地离线协议客户端接入真实服务端，读取 `show_dialog` 并发送实际 Dialog 回调，验证页面和数据库状态。它能验证真实 Paper/Folia API、表单标识、购买行为和回调校验，但不能代替玩家对最终画面排版的人工评价。

测试还使用实际 PlaceholderAPI，以及仅用于隔离测试的 Vault API 经济提供者。该提供者记录扣款次数并可模拟余额不足、扣款后异常；它不属于发布 JAR，也不代表已测试市场上的所有经济插件。

Paper 26.1.1 和 Folia 26.1.2 的真实协议交互均已通过全部七类页面：空背包/商店、余额不足通知和返回、购买次数、渐变预览与色板、创建/修改各扣一次、佩戴/卸下与 PAPI 输出、管理员创建、限量限时销售、重复回调只扣一次、下架及不限量配置。

隔离 Vault 测试验证：同一购买回调重放不重复扣款；明确拒绝不扣款；模拟扣款后抛错时不发放、不重试，并保留 `CHARGING`。测试记录为已完成、已取消、待核对各一笔，与提供者记录的两次实际扣款一致。

另使用 Paper 26.1.1 与 Folia 26.1.2 同时连接同一个 PostgreSQL，验证同一 UUID 的余额、自定义次数、佩戴称号跨服刷新，以及从另一服卸下后同步返回。所有变化在配置的刷新窗口内被另一服的实际 PlaceholderAPI 输出观察到。

Vault 模式下禁用经济提供者的负向启动测试也通过：DeerTitle 明确拒绝启用，没有创建数据库或自动降级为称号币，服务端仍正常启动和停止。

另清空 MySQL 首次认证缓存，直接调用实际 `DatabaseFactory`：未启用公钥检索时拒绝无 TLS 的首次公钥交换，显式配置允许后连接及 `SELECT 1` 通过，避免被已有认证缓存掩盖配置要求。

审查和实际测试中已修复：

- 玩家退出与异步缓存完成之间的竞态，避免离线数据重新进入缓存。
- 旧占位符路径每次解析渐变造成的重复分配；现在只返回预渲染字符串。
- 异步失败后 Dialog 留在等待状态；现在显示可返回的失败页，并拒绝过期回调覆盖新页面。
- 原生 Dialog 不接受带短横线的输入标识；配置字段仍保持用户要求的 kebab-case，内部表单标识使用合法下划线。
- 管理员打开上架表单后发生购买时，剩余库存编辑拒绝陈旧已售数量。
- 实际扣款与管理员人工核对之间的竞争；未知状态必须明确确认操作来源已停止。

## 证据与边界

本地完整原件保存在 `.local-test/reports/`、`.local-test/client/` 和 `.local-test/servers/`，已排除在 Git 外。服务端测试仅使用回环监听、限额 JVM 和新世界；测试用数据库、假经济插件和账户信息不随正式插件发布。

全部临时 Minecraft JVM 和两个测试数据库容器已停止，保留验证资料。脱敏机器可读结果见 [validation-results.json](validation-results.json)。

这些是有界的自动化与服务端集成验证，不是生产长期压测。未来未发布的 Minecraft/Paper API、第三方经济插件的跨服一致性，以及最终游戏客户端布局仍须在实际部署环境复核。
