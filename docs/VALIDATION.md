# 验证记录

最新 RGB / HSV 颜色编辑功能的验证见 [颜色滑块验证记录](COLOR-SLIDERS-VALIDATION.md)。下文保留付款简化提交 `8b542f4` 的结果，对应下方列出的独立 JAR 哈希。

日期：2026-10-28。本次针对 Vault 付款简化及自动维护进行回归，全部使用隔离的本地数据库、测试账户和新建世界。

## 本次构建

JDK 25、Gradle 9.4.1，运行 `test` 与 `shadowJar`。实际服务端使用同一份安装 JAR：

```text
build/libs/DeerTitle-1.0.0-SNAPSHOT.jar
SHA-256 124d309a5906a6f52bad47c3882dc036be1ea3060609a7baf6373e3802dadf06
```

## 单元与数据库测试

默认测试 **70 项通过、0 失败、20 项外部数据库测试按条件跳过**，其中 SQLite 存储契约 20 项、付款协调器 13 项。随后分别对 PostgreSQL 17.11 和 MySQL 8.4.13 运行全部 20 项存储契约，两者均 **20/20 通过，无跳过**。

覆盖成功发放、拒绝或异常后取消、库存释放、重复回调只扣一次、五分钟到期边界、最近状态转换更新时间、多实例维护竞争、每批处理上限，以及单条发放失败后的回滚、重试和其他订单继续处理。付款协调器还使用真实 SQLite 触发器阻止首次发放：撤除故障后，后台只补发一次，不重新扣款。

原有的库存、余额、次数、所有权、导入、文字、表单和缓存测试一并通过。数据库结构继续读取 v1 的订单记录，仅新增维护查询索引。

外部数据库测试会重建测试表，必须使用专用测试库。只有明确设置以下变量才会运行，URL 限定为回环地址且数据库名为 `deertitle`：

```bash
export DEERTITLE_TEST_ALLOW_RESET=isolated-local
export DEERTITLE_TEST_DATABASE_TYPE=postgresql # 或 mysql
export DEERTITLE_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:15432/deertitle'
export DEERTITLE_TEST_DATABASE_USERNAME='<测试用户>'
# 通过当前环境配置 DEERTITLE_TEST_DATABASE_PASSWORD，不把真实密码写进仓库。
./gradlew test --tests '*ExternalTitleStoreTest' --rerun-tasks
```

## Paper / Folia 实际运行

在 Paper 26.1.1 build 4 和 Folia 26.1.2 build 8 上，以离线协议客户端读取原生 Dialog 并发送实际回调。使用真实 Vault API 与隔离的测试经济提供者，分别验证：

- 返回成功后到账；返回拒绝、空结果或抛异常后结束本次购买。
- 异常后可再次购买，限量商品失败后释放库存，重复回调不会再次扣款。
- 命令帮助、补全和状态输出不再要求人工处理付款。
- 启动及真实 30 秒维护周期均取消陈旧预留、补发已记录成功的购买，保留未满五分钟的扣款记录。
- 自动维护不调用经济提供者。

两组均通过。每组玩家最终为 3 笔完成、4 笔取消、0 笔待处理；自定义次数为 3，限量称号只售出并授予 1 次。维护单独补发 2 笔、释放 4 笔陈旧预留。

测试有意模拟一次“已经扣钱才抛异常”：经济提供者记录 4 次实际扣款，DeerTitle 完成 3 笔购买。此差异是简化策略接受的异常边界，不能把“取消购买”解释成已经退款。该情况下不自动重试扣款，后续新购买可正常进行。

## 既有兼容性证据

初始提交 `deb7822` 已验证七个 Paper/Folia 版本、九个服务端与数据库组合、七类 Dialog 页面、MiniPlayerTitle 导入、PlaceholderAPI 与 PostgreSQL 跨服同步。完整记录保留在该提交的 [验证文档](https://github.com/LunaDeerMC/DeerTitle/blob/deb7822ce18d6213bbdd45005fdb880066731af1/docs/VALIDATION.md) 和 [机器可读结果](https://github.com/LunaDeerMC/DeerTitle/blob/deb7822ce18d6213bbdd45005fdb880066731af1/docs/validation-results.json)。

那些结果对应初始 JAR；本次新 JAR 的实际运行范围为上面列出的两组付款与维护回归。历史文档中的人工付款处理方式已由当前实现取代。

## 证据与边界

本次原始报告保存在 `.local-test/reports/vault-simplification/`；测试程序、世界、数据库及账户信息排除在 Git 外。全部临时 Minecraft JVM 和两个测试数据库容器已停止。脱敏结果见 [validation-results.json](validation-results.json)。

协议客户端验证真实 API 和交互行为，不代替游戏画面的人工排版检查。测试经济提供者不代表所有第三方经济插件；未来未发布版本的兼容性和生产长期负载未由这些有界测试证明。
