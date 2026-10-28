# Full / Lite 分发验证

日期：2026-10-28。目标版本：1.0.1。

## 打包方式与体积

参考 [Dominion 的构建实现](https://github.com/LunaDeerMC/Dominion/blob/32ccca356ba210806ce2c6a9f49b7af391b39867/build.gradle.kts)：Full 附带运行依赖；Lite 通过 Paper/Folia 原生 `plugin.yml` 的 `libraries` 解析、下载并缓存依赖。两种包共用应用代码、配置与数据库结构，功能相同。

旧版 1.0.0 JAR 的压缩条目中，SQLite 跨平台原生库约占 13.4 MiB；MySQL 驱动约 2.26 MiB，Protobuf 约 1.61 MiB，PostgreSQL 驱动约 0.95 MiB，插件自身约 0.16 MiB。拆分改变分发体积，不代表省去运行依赖或减少运行时内存。

本次本地候选由 JDK 25、Gradle 9.4.1 执行 `./gradlew --no-daemon -PreleaseVersion=1.0.1 clean build` 生成：

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `DeerTitle-1.0.1-full.jar` | 20,536,015 | `54442cd3122030c38c712fa36db467a1a8b450c69db9b11ca8107ccb856c4f7a` |
| `DeerTitle-1.0.1-lite.jar` | 186,022 | `a3063933374459035806f4e6ba323e19b1e3d7a178cf98d620daec8a5221bf43` |

检查实际 JAR：Full 的 `libraries: []`，包含三种 JDBC 驱动、服务发现描述及重定位后的 Hikari；Lite 声明四项依赖坐标，不包含这些依赖类。两个包的插件版本均为 `1.0.1`，各有且仅有一份 `plugin.yml`；Full 打包未污染 Lite 的资源或应用字节码。

## 生命周期与自动化测试

默认测试 **85 项通过，0 失败，20 项外部数据库契约测试按环境条件跳过**。新增 4 项隔离加载器测试，真实注册 JDBC 驱动并启动 MySQL 驱动清理线程，覆盖：

- Lite 注销私有依赖驱动、停止其 MySQL 清理线程并关闭私有库加载器。
- Full 注销自身驱动，但不接管服务器对插件加载器的关闭。
- 依赖由服务器祖先加载器提供时，保留共享驱动、清理线程和加载器。
- Lite 清理自身其他驱动时，服务器共享的 MySQL 驱动仍然可用。

Paper 原生库加载采用父级优先；服务器已经提供的 JDBC 驱动可能优先于声明的 Maven 版本。清理依据实际类加载器所有权，不注销或关闭服务器共享依赖。

Release 工作流通过 `actionlint`；构建后发布两个 JAR 和包含两份校验值的 `SHA256SUMS`。

## 实际服务端验证

均使用本地隔离测试世界和数据库，安装上文冻结的候选 JAR。

| 服务端 | 版本 | 数据库 | 结果 |
| --- | --- | --- | --- |
| Paper 26.1.1 build 4 | Full | SQLite | 通过 |
| Paper 26.1.1 build 4 | Lite | SQLite | 首次下载与缓存重启均通过 |
| Folia 26.1.2 build 8 | Full | SQLite | 通过 |
| Folia 26.1.2 build 8 | Lite | SQLite | 通过 |
| Paper 26.1.1 build 4 | Lite | PostgreSQL | 通过 |
| Paper 26.1.1 build 4 | Lite | MySQL | 通过 |

六组分别执行真实 MiniPlayerTitle 导入、重复导入，以及称号币和次数的四次增减事务，停机后直接核对数据库：2 个称号、1 名玩家、2 条拥有关系、1 条上架记录，余额 520、次数 2；重复导入不增加数据，源数据库未改变。另一次 Lite 热缓存重启核对持久化结果与重复导入。

冷缓存测试保留 Minecraft 服务器自身所需依赖，起初没有 Hikari、PostgreSQL 和 MySQL 9.3 的插件依赖。Paper 原生加载器自动解析并下载，新建 40 个缓存文件（包括 POM、JAR、校验和及仓库元数据）。热启动缓存新增 0、改写 0；Maven 转发请求数从 31 保持为 31，即本次重启没有请求依赖仓库。

测试容器内 Java 直连 Paper 默认镜像与标准 Maven Central 均受到网络限制。冷下载通过仅监听本机的测试转发服务访问标准 Maven Central；转发沿用环境代理并验证上游 HTTPS 证书，不缓存依赖。通过 Paper 自带的 `PAPER_DEFAULT_CENTRAL_REPOSITORY` 为测试进程指定该入口，依然由原生 LibraryLoader 下载、校验和保存，未手工填充插件依赖缓存。该转发不属于插件，不需要部署；实际使用 Lite 需保证服务器能访问其 Maven 仓库。

运行时探针确认 Lite 的 Hikari 6.3.0、PostgreSQL 42.7.7 使用私有库加载器；SQLite 3.49.1.0、MySQL 9.2.0 由服务器提供。停用后私有 PostgreSQL 驱动已注销，服务器共享 JDBC 注册对象和 MySQL 清理线程保留，共享 SQLite 仍能执行内存库 `SELECT 1`。六组及缓存重启均正常关闭。

原始报告保存在 Git 忽略的 `.local-test/reports/full-lite/`，加载器探针和网络转发记录位于 `.local-test/full-lite-probe/`。这次验证针对分发、依赖下载、存储事务和生命周期；颜色编辑等既有功能的验证范围见单独记录。
