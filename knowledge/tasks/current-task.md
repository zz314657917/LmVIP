# LmVIP 当前任务

## 背景

LmVIP 是基于 TabooLib 6 的周目 VIP 插件，强依赖 LmCore 与 LuckPerms，可选接入 PlaceholderAPI。当前开发线围绕 review 收口、一次性 VIP 礼包、配置/语言文件自动补齐和 1.12.2 运行态验证展开。

## 当前目标

状态：提测中。

本轮目标不再新增玩法功能，只对现有 review 修复做提测交接：确认构建产物、已验证链路、测试环境前置依赖和剩余待测项。

## 本次已完成

- 修复 rollback 后不立即同步 LuckPerms 的问题。
- 修复 PAPI 主线程可能查库的问题，主线程只读缓存，缺失或过期时异步刷新。
- 修复充值流水写入异常被吞掉的问题，重复订单、无变更和 SQL 异常可区分。
- 修复奖励命令失败后仍保留领取记录的问题，失败或超时会回滚本次 claim。
- 增加 once 一次性 VIP 礼包，按玩家和 VIP 等级永久一次。
- 增加 `LmVipApi` 对外 API，支持其他插件查询 VIP 等级和跨周目累计充值积分。
- 增加 `lang.yml`，配置文件带注释，缺失文件和必要 key 会在启动或 `/vipadmin reload` 后自动补齐。
- README 已补已验证环境、rollback、PAPI 缓存、once 礼包和提测清单。

## 已确认事实

- 构建产物：`F:/mcplugins/LmVIP/build/libs/LmVIP.jar`。
- 功能代码基线：`09e971d 新增 LmVIP 对外查询 API`。
- 基础能力依赖基线：`2579a54 改用本地 Maven 依赖 LmCore` 已把 Gradle 编译依赖切到 `cc.mcstory:lm-core:1.1.0-SNAPSHOT`，构建前由 `F:/mcplugins/LmCore-v2 mvn clean install -DskipTests` 更新本地 Maven 坐标，不再写死 `F:/mcplugins/LmCore/target/...`。
- 已在 Docker MySQL + test-cell 环境验证 LmCore profile `LmVIP` 可建表并执行核心链路。
- 已在 test-cell 临时部署 `LmCore-v2 + LuckPerms 5.4.145 + LmVIP` 验证基础服务接入：`/lmcore status` 可见 `LmVIP` profile available，`registered-handles=0`；`/lmcore testdb LmVIP` 成功；`/vipadmin season start`、充值和 `/vipadmin info` 成功。
- 缺少 `LmCore` 或缺少 `LuckPerms` 时，Bukkit 会阻止加载 `LmVIP.jar` 并输出 `UnknownDependencyException`。
- 本地 `F:/minecraft/server/paper-1.12.2` 当前缺少 LuckPerms，不适合直接作为 LmVIP 提测部署目标。

## 待验证点

- 长时间高频计分板或聊天 PAPI 刷新压力。
- 正式测试服中通过 Bukkit `ServicesManager` 获取 `LmVipApi` 的跨插件联调。
- 你的正式测试服中 LmCore 的 `LmVIP` 数据库 profile 是否已经配置好。
- 你的正式测试服中 LuckPerms Bukkit 版本是否仍兼容 Java 8，建议 5.4.x。
- 运营配置中的奖励命令是否都能真实发放物品或权限。

## 当前结论

可以进入提测。当前阻塞不在代码构建或核心链路，而在测试服依赖准备：必须先放好 LmCore、LuckPerms，并在 LmCore 中配置 `database("LmVIP")`。

## 下一步

1. 在目标测试服安装 `LmCore.jar`、LuckPerms Bukkit 5.4.x、可选 PlaceholderAPI。
2. 在 LmCore 配置中增加或确认 `LmVIP` 数据库 profile。
3. 复制 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar` 到目标测试服 `plugins`。
4. 启动测试服，按 README 的提测清单执行 `/vipadmin season start`、充值、重复订单、领奖、once、rollback 和 PAPI 验证。
5. 用一个测试插件或脚本通过 Bukkit `ServicesManager` 调用 `LmVipApi#getCachedVipLevel`、`getCachedTotalPoints`、`getSnapshotAsync`。
6. 若命中奖励命令失败，先确认 claim 记录是否已回滚，再修正 `levels.yml` 后重新领取。

## 验证记录

- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过。
- `git diff --check`：通过。
- Docker MySQL：`lmvip_%` 测试表已创建并在收尾时清理为空。
- test-cell `cell-02`：已完成运行态验证，收尾时已释放，端口 `25575`、`38090`、`38091` 已关闭。
- test-cell `cell-01` LmCore-v2 基础接入 smoke：`LmCore-v2 mvn clean install -DskipTests` 通过；`LmVIP clean build --stacktrace` 通过；临时部署后 DB 证据为 `lmvip_seasons=1`、`lmvip_transactions=1`、`zzzderk total_points=100, vip_level=1`；收尾已停止 cell、释放本轮 owner、删除 `LmVIP.jar/LuckPerms.jar` 临时 jar、恢复 `ops.json=[]`、删除 `lmvip_%` 表。
- 依赖缺失验证：缺 `LmCore` 和缺 `LuckPerms` 均能阻止 LmVIP 加载并输出明确依赖错误。
- API 构建验证：`LmVipApi`、`VipApiSnapshot`、`BukkitLmVipApi` 已打入 `build/libs/LmVIP.jar`。
- 仓库知识入口：`knowledge/00-start-here.md`。
