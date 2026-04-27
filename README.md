# DeerTitle

DeerTitle 是一个面向 Paper/Folia 服务端的称号插件，提供称号商店、称号卡、GUI 菜单、PlaceholderAPI 集成，以及 Vault 经济或内置货币支持。

当前项目使用 Gradle 构建，面向 Minecraft `1.20.1` API，插件声明为 `folia-supported: true`。

## 功能概览

- 玩家称号持有、佩戴、卸下与到期清理
- 基于 GUI 的主菜单、我的称号、商店、管理菜单
- 称号商店，支持价格、库存、时长和销售截止日期
- 称号卡道具，可发放给玩家并右键使用
- PlaceholderAPI 占位符扩展
- Vault 经济优先，未接入时回退到内置货币系统
- SQLite 默认开箱即用，同时支持 MariaDB/MySQL 配置
- 语言文件与配置文件自动生成

## 运行要求

- 服务端：Paper 1.20.1+ 或兼容 API 的 Folia 服务端
- 运行 Java：`17+`
- 构建 Java：建议使用 `JDK 21`，项目已配置 Gradle toolchain

可选依赖：

- `PlaceholderAPI`：启用称号占位符
- `Vault`：接入现有经济插件；未安装时使用插件内置货币

## 构建

在项目根目录执行：

```bash
./gradlew shadowJar
```

构建完成后，产物位于：

```text
build/libs/DeerTitle-<version>.jar
```

项目还提供了一个便捷任务：

```bash
./gradlew 'Clean&Build'
```

## 安装

1. 将构建出的 jar 放入服务端 `plugins/` 目录。
2. 如需占位符支持，额外安装 PlaceholderAPI。
3. 如需对接外部经济，额外安装 Vault 以及一个 Vault 兼容经济插件。
4. 启动服务器，插件会自动生成：
   - `plugins/DeerTitle/config.yml`
   - `plugins/DeerTitle/languages/<language>.yml`
5. 按需修改配置后，执行 `/dt reload` 或重启服务器。

## 默认配置说明

`config.yml` 主要包含以下部分：

- `general`
  - `languageCode`：默认 `zh_cn`
  - `debug`：调试开关
- `database`
  - `type`：`sqlite` 或 `mariadb`/`mysql`
  - `sqliteFile`：SQLite 文件路径，默认 `database/deertitle.db`
  - `host`、`port`、`database`、`username`、`password`：MariaDB 连接信息
- `display`
  - `titlePrefix`、`titleSuffix`：称号本体前后缀，默认显示为方括号样式
  - `titleSeparator`：称号与玩家名之间的分隔符
  - `currentTitlePlaceholder`、`previewPlaceholder`：显示相关占位符配置
  - `fallbackChatPrefix`、`fallbackTabPrefix`：已预留在配置中，便于后续扩展显示策略
- `economy`
  - `preferVault`：优先使用 Vault
  - `builtInCurrencyName`、`builtInCurrencySymbol`：内置货币名称与符号
  - `defaultBalance`：新玩家默认余额
- `shop`
  - `allowFreeTitles`、`maxPageSize`：商店行为相关配置字段
- `card`
  - `material`：称号卡材质，默认 `NAME_TAG`
  - `consumeOnUse`：使用后是否消耗
  - `requireSneakToUse`：是否要求潜行使用
- `ui`
  - `fillEmptySlots`：是否填充空槽位
  - `playerPageSize`、`adminPageSize`：菜单分页相关配置字段
- `tasks`
  - `expireCheckIntervalTicks`：称号过期扫描周期
  - `tabRefreshIntervalTicks`：Tab 刷新周期

## 数据库

默认使用 SQLite，不需要额外依赖。首次启动会在插件目录下生成数据库文件，并自动执行迁移。

若切换到 MariaDB/MySQL，可将 `database.type` 设置为 `mariadb` 或 `mysql`，并填写以下字段：

- `host`
- `port`
- `database`
- `username`
- `password`
- `parameters`

## 玩家命令

主命令：`/deertitle`

别名：`/dt`、`/title`

- `/dt`
  - 玩家执行时打开主菜单
  - 控制台执行时显示帮助
- `/dt help`
- `/dt list`
- `/dt wear <id>`
- `/dt equip <id>`
- `/dt remove`
- `/dt current`
- `/dt balance`
- `/dt shop`
- `/dt buy <offerId>`

## 管理命令

权限节点：`deertitle.admin`

- `/dt reload`
- `/dt admin create <title> || <description>`
- `/dt admin setdesc <titleId> <description>`
- `/dt admin setenabled <titleId> <true|false>`
- `/dt admin grant <player> <titleId> [days|-1]`
- `/dt admin revoke <player> <titleId>`
- `/dt admin shop set <titleId> <price> <days|-1> <amount|-1> <saleEnd|-1>`
- `/dt admin shop clear <titleId>`
- `/dt admin coin <set|add> <player> <amount>`
- `/dt admin card <player> <titleId> [days|-1]`

说明：

- `days = -1` 表示永久称号
- `amount = -1` 表示无限库存
- `saleEnd = -1` 表示永不过期；否则使用 `yyyy-MM-dd` 格式
- `create` 子命令使用 `||` 分隔称号文本与描述

## PlaceholderAPI 占位符

注册标识符：`deertitle`

- `%deertitle_current%`
- `%deertitle_current_legacy%`
- `%deertitle_current_plain%`
- `%deertitle_has_title%`

如果服务器已安装 PlaceholderAPI，插件会自动注册扩展；否则会回退到内部显示逻辑来刷新聊天前缀和 Tab 名称。

## 开发说明

- 构建脚本位于 `build.gradle.kts`
- `shadowJar` 任务会生成最终发布 jar
- SQLite 基线建表语句已内置在迁移代码中，由 `MigrationRunner` 和 `SchemaStatements` 统一维护
- 项目源代码目录：`src/main/java/cn/lunadeer/deertitle`

## 目录简述

- `command`：命令处理
- `configuration`：配置与语言加载
- `database`：连接管理、迁移、仓储
- `display`：聊天和列表名显示逻辑
- `economy`：Vault/内置经济封装
- `listener`：菜单与称号卡事件
- `placeholder`：PlaceholderAPI 扩展
- `service`：核心业务逻辑
- `ui`：Inventory GUI
- `utils`：配置工具与调度器兼容层

## 后续建议

如果你打算对外发布这个项目，建议下一步补上：

1. 开源协议
2. 版本更新记录
3. 示例配置文件截图或完整样例