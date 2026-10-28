# DeerTitle

面向 **Minecraft 26.1 及以上 Paper / Folia** 的称号插件。使用 Java 25、Gradle Kotlin DSL、Paper 原生 Dialog 和 MiniMessage。

DeerTitle 使用独立数据库结构，可通过管理员命令从 MiniPlayerTitle 导入；不会继续读写旧插件的数据表。

## 游戏内功能

使用 `/deertitle` 或 `/dt` 打开主菜单。

| 页面 | 功能 |
| --- | --- |
| 主菜单 | 当前称号、余额、自定义次数及入口 |
| 称号背包 | 查看、佩戴、卸下称号；显示到期时间 |
| 称号商店 | 购买称号，购买前显示价格、剩余数量及有效期 |
| 自定义称号 | 购买自定义次数、创建及修改自己的称号 |
| 称号编辑器 | 色板、自定义 RGB、纯色、渐变、彩虹、文字装饰、MiniMessage 高级模式、预览 |
| 商店管理 | 管理称号库，上架、下架、编辑商品 |
| 上架设置 | 价格、限量/不限量、开始与结束时间、永久/限期拥有 |

编辑器的“预览”提交当前表单并重新显示效果，保留已填写内容。原生 Dialog 不提供逐字输入变化事件。销售时段与玩家购买后拥有的天数分别设置。

普通玩家每次创建或修改自定义称号均消耗 **1 次**，保存成功后立即生效。管理员创建公共称号不消耗次数。所有颜色文字使用 MiniMessage；玩家输入仅允许颜色和装饰，不允许点击命令、悬浮事件、字体、NBT 等活动标签。

## 构建与安装

```bash
./gradlew clean build
```

需要 JDK 25。安装 `build/libs/DeerTitle-1.0.0-SNAPSHOT.jar`，不要安装 `-plain.jar`。SQLite、PostgreSQL、MySQL 驱动和连接池已包含在安装 JAR 中。

1. 将 JAR 放入 `plugins/`，启动一次生成 `plugins/DeerTitle/config.yml`。
2. 配置数据库、经济模式及文字长度限制，然后重启插件所在服务端。
3. 安装 PlaceholderAPI，并在聊天、Tab 或其他显示插件中使用下文占位符。
4. 管理员打开 `/dt admin` 创建公共称号并设置销售规则；`/dt editor` 可直接进入创建页面。

采用最早公开的 Paper 26.x API 编译，`api-version: '26.1'`，不使用 NMS、反射版本适配或 Bukkit Inventory GUI。当前验证范围见 [验证记录](docs/VALIDATION.md)。对于尚未发布的未来版本，以 Paper 公共 API 保持兼容为前提，不能预先证明未知版本的运行结果。Spigot、CraftBukkit 和 Minecraft 1.21.x 不在支持范围。

## 配置与跨服

字段统一使用 `kebab-case`，完整默认值见 [config.yml](src/main/resources/config.yml)。

```yaml
database:
  type: pgsql # sqlite / pgsql / mysql
  host: localhost
  port: 5432 # MySQL 通常为 3306
  name: deertitle
  username: deertitle
  password: ''
  ssl: true
  pool-size: 4
cross-server:
  enabled: true
  refresh-seconds: 3
economy:
  mode: coins # coins / vault，全插件二选一
  currency-name: ''
custom-title:
  price-per-credit: '100'
  max-credits-per-purchase: 100
  max-visible-length: 24
```

- SQLite 用于单服；启用跨服却选择 SQLite 时拒绝启动。多个子服应连接**同一个 PostgreSQL / MySQL 数据库**，并保持经济模式、价格和展示配置一致。
- 余额、背包、佩戴、次数、库存以数据库为准。所有写事务经过数据库互斥行，保护跨服库存与扣款不变量；事务内不调用 Vault 或网络服务。在线占位符缓存默认约 3 秒同步，购买和装备校验始终直接读数据库。
- `coins` 为内置整数称号币。`vault` 需要用户另装 Vault 及适合对应 Paper/Folia 的经济插件；缺失时拒绝启动，不自动切换货币。Vault 金额最多两位小数。
- Vault 余额是否跨服共享，取决于所选经济插件；共享 DeerTitle 数据库不能代替经济插件的数据同步。
- `database.ssl: true` 对远端数据库执行证书及主机名验证。MySQL 关闭 TLS 时如需 RSA 公钥检索，须显式设置 `database.allow-public-key-retrieval: true`；默认关闭。
- `display.time-zone` 控制 UI 销售时间输入与日期显示；`import.mini-player-title.source-time-zone` 单独解释旧数据日期。
- 修改数据库或经济模式后重启。

## PlaceholderAPI

| 占位符 | 内容 |
| --- | --- |
| `%deertitle_title%` | 当前称号，使用配置的输出格式；默认 legacy RGB |
| `%deertitle_title_minimessage%` | MiniMessage 格式的当前称号 |
| `%deertitle_title_legacy%` | `§` 格式和 RGB 当前称号 |
| `%deertitle_title_plain%` | 无样式文字 |
| `%deertitle_title_id%` | 当前称号 ID，未佩戴时为空 |
| `%deertitle_coins%` | 内置称号币余额，不查询 Vault 余额 |
| `%deertitle_custom_credits%` | 剩余自定义次数 |

使用 `display.prefix`、`display.suffix` 和 `display.empty-title` 设置展示内容。插件不接管聊天事件或玩家头顶队伍。占位符只读在线玩家缓存，不执行数据库查询，也不在每次调用时解析渐变；离线或长期无法刷新的数据不继续展示。

## 管理命令

| 命令 | 用途 |
| --- | --- |
| `/dt status` | 查看数据库、经济模式与跨服配置 |
| `/dt admin` | 商店管理 |
| `/dt editor` | 新建公共称号 |
| `/dt coins <玩家或UUID> <add\|take> <数量>` | 增减内置称号币 |
| `/dt credits <玩家或UUID> <add\|take> <数量>` | 增减自定义次数 |
| `/dt grant <玩家或UUID> <称号ID>` | 永久授予称号 |
| `/dt import miniplayertitle preview` | 只读分析旧数据，产生导入预览 |
| `/dt import miniplayertitle confirm <确认码>` | 导入已预览的快照 |

离线玩家使用 UUID；在线玩家可使用完整名字。普通权限为 `deertitle.use`、`deertitle.custom`，默认开启；管理权限为 `deertitle.admin`，默认仅 OP。

## 从 MiniPlayerTitle 导入

1. 备份旧数据库。安排迁移时停止旧插件继续修改数据；源数据库只读快照保证读取一致，但不会自动追踪预览之后发生的交易。
2. 设置 `import.mini-player-title.source-directory`（默认 `plugins/MiniPlayerTitle`），脚本自动读取旧 `config.yml` 的数据库配置、`ExternalEco`、`Prefix`、`Suffix`。支持旧 SQLite / PostgreSQL / MySQL。
3. 确认旧服务器时区、稳定的 `source-id`、小数处理规则和是否导入商店。
4. 执行 `preview` 检查条数、警告和错误。存在错误时不会导入。预览只读源数据，不修改新库。
5. 在 5 分钟内使用同一管理员的确认码执行 `confirm`。整批导入事务提交；不会留下半套已导入数据。
6. 检查玩家称号与余额，将旧 `%mplt_player_title%` 等占位符换成 DeerTitle 的占位符，再停用旧插件。

导入自动转换 `&`、`§`、RGB 和旧渐变文本为 MiniMessage，保留合法颜色效果；旧文本中的未知标签作为字面文字处理。旧前后缀也一并导入到称号显示内容，之后无需再次设置相同 DeerTitle 前后缀。

数据库使用独立 `dt_*` 表与来源映射。相同 `source-id` 重复导入不会重复发币、发称号、补回已售库存或重置玩家后来的佩戴选择。不要更换 `source-id` 来重试同一份资料。旧 `amount` 按剩余库存解释，旧到期日期按来源时区当天零点解释。

旧插件的外部 Vault 余额不会复制为称号币。旧内部余额带小数时默认拒绝；只有明确选择 `floor` 或 `round-half-up` 才转换，并给出警告。Vault 模式仍使用原经济提供者的余额。

旧数据库没有可靠的“自定义称号创建者”和“剩余自定义次数”字段，因此导入称号保留使用权，作为历史公共称号管理，不根据描述猜测编辑权限。尚未兑换的实体称号卡不在数据库中，不属于这次数据库导入。超过 100,000 行或 8,000,000 个文本字符的来源会停止预览，需分批迁移方案。

## Vault 付款

按经济插件返回的交易结果处理：**成功就发放，失败就取消购买并释放库存**。重复点击不会重复扣款。

经济接口抛异常或返回空结果时，同样结束本次购买并记录日志，不自动再次扣款或退款。崩溃留下的预留超过 5 分钟后由后台释放；已经记录扣款成功、尚未发放的内容会自动补发。维护在启动时及之后每 30 秒运行，无需管理员执行付款处理命令。

这是游戏场景的简化取舍：如果经济插件实际扣钱后才抛异常，或进程恰好在扣款后崩溃，仍可能出现扣款与发放不一致；插件不为这些极少数情况引入停服核单流程。

## 开发与验证

```bash
./gradlew test
./gradlew shadowJar
```

测试涵盖真实 SQLite 事务、并发限量库存、余额回滚、付款状态与重复回调、旧库导入幂等、字符转换、编辑表单和在线缓存生命周期。PostgreSQL/MySQL 集成测试通过独立测试数据库运行，配置方法见 [验证记录](docs/VALIDATION.md)。本地测试世界、数据库凭证、下载文件和报告原件位于 Git 忽略的 `.local-test/`；发布资料只包含脱敏结论。
