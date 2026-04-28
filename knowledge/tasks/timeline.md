# LmVIP 时间轴

## 2026-04-28 15:45 +08:00 - 对外 API 增加

- 当前阶段：提测中追加跨插件查询能力，仍保持业务实现稳定。
- 本段重点：新增正式 Bukkit Services API `LmVipApi`，给其他插件查询 VIP 等级和跨周目累计充值积分。
- 已完成：新增 `cc.mcstory.lmvip.api` 包；`LmVipPlugin` 启动时注册 `LmVipApi`，关闭时注销；`VipService` 增加 `cachedSnapshot`，缓存读取不触发数据库；README 增加 API 使用示例。
- 关键决策：外部插件不要直接调用内部 `LmVipServices`；主线程只用 `getCachedVipLevel`、`getCachedTotalPoints`，需要查库时走 `getSnapshotAsync` 或 `refreshSnapshotAsync`。
- 验证记录：`clean build --stacktrace` 通过；`build/libs/LmVIP.jar` 已包含 `cc/mcstory/lmvip/api/LmVipApi.class`、`VipApiSnapshot.class` 和 `BukkitLmVipApi.class`；新增 `VipApiSnapshotsTest` 覆盖 VIP 等级与累计充值字段映射。
- 遗留问题：尚未在真实外部插件中通过 Bukkit `ServicesManager` 做跨插件联调。
- 下一步：目标测试服准备依赖后，用测试插件调用 `LmVipApi#getCachedVipLevel`、`getCachedTotalPoints`、`getSnapshotAsync` 验证。

## 2026-04-28 15:12 +08:00 - Review 收口与提测归档

- 当前阶段：LmVIP 已从实现收口进入提测中，测试包已构建，当前快照写入 `knowledge/tasks/current-task.md`。
- 本段重点：4 个 review findings 已修复并完成核心运行态验证；once 一次性 VIP 礼包、语言文件、配置注释和缺失 key 自动补齐已纳入当前版本；提测依赖边界已明确为 LmCore、LuckPerms 和 LmCore 的 `LmVIP` 数据库 profile。
- 已完成：`rollback` 后立即重算 VIP 并同步 LuckPerms；PAPI 主线程只读缓存并异步刷新；流水写入区分重复订单、无变更和 SQL 异常；奖励命令失败或超时会回滚本次领取记录；`/vipadmin reload` 会先补齐缺失配置文件和必要 key。
- 关键决策：VIP 等级由跨周目总累充永久决定，LuckPerms 权限不再受月累充激活影响；日、周、月奖励按周目与周期刷新；once 礼包按玩家和 VIP 等级永久一次，不随周目刷新；当前周目只由数据库 active season 决定。
- 验证记录：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace` 通过；Docker MySQL + test-cell 已验证建表、充值、重复订单、领奖、once、rollback、PAPI 和 LuckPerms 同步；缺 `LmCore` 或 `LuckPerms` 时 Bukkit 阻止加载 `LmVIP.jar` 并输出 `UnknownDependencyException`；test-cell 和 `lmvip_%` 测试表已清理。
- 遗留问题：未做长时间高频 PAPI 压测；正式测试服仍需确认 LuckPerms 5.4.x、LmCore `database("LmVIP")` 和运营奖励命令配置；本地 `F:/minecraft/server/paper-1.12.2` 当前缺 LuckPerms，不适合作为直接部署目标。
- 下一步：在目标测试服准备 LmCore、LuckPerms 和数据库 profile；部署 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar`；按 README 提测清单复测开周目、充值、重复订单、领奖、once、rollback 和 PAPI。

## 2026-04-28 11:22 +08:00 - 提测快照建立

- 当前阶段：代码修复和运行态验证已完成，进入可提测交接。
- 本段重点：新增 `knowledge/tasks/current-task.md`，把当前目标、已完成事项、验证记录、测试包路径和测试服前置条件收口成快照。
- 已完成：记录构建产物 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar`；标明 Docker MySQL 与 test-cell 验证结论；明确本地 `F:/minecraft/server/paper-1.12.2` 缺 LuckPerms，不适合作为直接部署目标。
- 关键决策：`current-task.md` 只保留当前快照，阶段历史后续写入 `timeline.md`。
- 验证记录：提交 `c2e88f1 记录 LmVIP 提测状态` 后重新执行 `clean build --stacktrace` 通过。
- 遗留问题：正式测试服仍需补齐 LmCore、LuckPerms 和 LmCore 的 `LmVIP` 数据库 profile。
- 下一步：进入目标测试服提测或继续补时间轴归档。

## 2026-04-28 05:16 +08:00 - 提测文档与 reload 配置补齐

- 当前阶段：review 修复已完成，补齐验收证据和运营说明。
- 本段重点：README 增加已验证环境、rollback 行为、PAPI 缓存说明和提测清单；`/vipadmin reload` 改为先补齐缺失配置文件与必要 key，再加载配置。
- 已完成：补充 `TransactionWriteResult.NoChange` 单测；确认 `lang.yml`、配置注释、缺失文件和缺失 key 自动补齐要求落地。
- 关键决策：配置迁移在 reload 时也要生效，避免只在开服时自动补文件。
- 验证记录：提交 `bbf7626 补齐 LmVIP 提测文档和 reload 配置补全`；`clean build --stacktrace` 通过；`git diff --check` 通过。
- 遗留问题：无新增功能风险，剩余为运行态复测与正式测试服依赖准备。
- 下一步：补跑依赖缺失、Docker MySQL 与 test-cell 清理验证。

## 2026-04-28 04:06 +08:00 - PAPI 缓存刷新收口

- 当前阶段：PAPI 主线程查库风险进入专门修复。
- 本段重点：Placeholder 主线程只读缓存，缓存缺失或过期只触发异步刷新；充值、积分调整、领奖、rollback、reload 和 disable 都会刷新或清理缓存。
- 已完成：`LmVipPlaceholderExpansion` 增加显式 `refresh`、`invalidate`、`clear`；服务层关键变更点接入缓存刷新。
- 关键决策：不在 PAPI 请求链路里直接查库，避免计分板或聊天高频刷新造成主线程 IO。
- 验证记录：提交 `8c93ddd 修复 PAPI 缓存刷新时机`；后续 test-cell 中通过 `%lmvip_level%`、`%lmvip_once_claimed_1%` 等变量验证 rollback 与 once 状态刷新。
- 遗留问题：未做长时间高频 PAPI 压测。
- 下一步：继续收口文档与提测证据。

## 2026-04-27 21:40 +08:00 - 一次性 VIP 礼包

- 当前阶段：补齐原始需求中“首次达到 VIP 等级可领一次性礼包”的缺口。
- 本段重点：`ClaimType` 增加 `ONCE`；once 礼包使用 `lmvip_claims` 记录，`claim_type=once`、`period_key=once`，按玩家和 VIP 等级永久一次。
- 已完成：新增 `/vip claim once <level>`；GUI 等级物品显示 once 状态并可点击领取；PAPI 增加 once 领取与可领取变量；`levels.yml` 增加 `once-reward`。
- 关键决策：once 礼包跨周目永久一次，不随日、周、月或周目刷新；领取只要求永久 VIP 等级达标。
- 验证记录：提交 `fe8b917 Add one-time VIP rewards`；新增 `ClaimTypeTest` 与 `OnceRewardPolicyTest`。
- 遗留问题：正式奖励命令仍需按服务器运营内容配置。
- 下一步：继续 review findings 的运行态验证。

## 2026-04-27 21:29 +08:00 - Review 修复与配置语言收口

- 当前阶段：针对 4 个 review findings 做集中修复。
- 本段重点：rollback 后同步 LuckPerms；流水写入区分重复订单、无变更和 SQL 异常；奖励命令失败或超时回滚 claim；配置文件和 `lang.yml` 自动生成并补缺失 key。
- 已完成：引入 `TransactionWriteResult`、`RollbackTransactionResult` 等明确结果语义；新增 `ConfigDefaultMerger`、语言运行配置和相关单测；奖励发放失败路径删除本次领取记录。
- 关键决策：真实 SQL 异常必须抛出并记录日志，不能和重复订单混在一起；奖励仍先占 claim，但发放失败必须补偿删除。
- 验证记录：提交 `c859b44 Fix VIP review issues and config defaults`；新增配置合并、语言占位符、rollback/result、奖励命令上下文等单测。
- 遗留问题：PAPI 缓存刷新时机后续单独补强。
- 下一步：补 once 礼包和 PAPI 缓存细节。

## 2026-04-27 13:09 +08:00 - LuckPerms 同步兼容修复

- 当前阶段：初版实现后的 LuckPerms 适配修正。
- 本段重点：调整 LuckPerms group 同步的反射调用，兼容 Bukkit 侧 LuckPerms 5.4.x 的运行时 API。
- 已完成：`LuckPermsGroupSync` 修正用户加载、节点处理和保存路径。
- 关键决策：为了兼容 1.12.2 + Java 8 环境，LuckPerms 以反射边界接入，业务层不散落 LuckPerms API 调用。
- 验证记录：提交 `4f48d74 Fix LuckPerms sync reflection`。
- 遗留问题：后续 rollback 降级同步仍需在业务层触发重算。
- 下一步：进入 review 修复。

## 2026-04-27 12:40 +08:00 - 初版 LmVIP 实现

- 当前阶段：从空仓库建立 TabooLib 6 + Kotlin 的 LmVIP 插件骨架和首版功能。
- 本段重点：实现周目、充值流水、VIP 等级、LuckPerms group 同步、玩家 GUI、PAPI、日/周/月奖励、LmCore 数据库接入。
- 已完成：新增 Gradle/TabooLib 工程、命令 `/vip` 与 `/vipadmin`、`JdbcVipRepository`、`VipService`、`RewardService`、`PeriodService`、默认 `config.yml`、`levels.yml`、`gui.yml`。
- 关键决策：数据库统一走 LmCore profile `LmVIP`；当前周目由数据库 active season 决定；总累充决定永久 VIP 等级；日、周、月奖励按周期和周目记录领取。
- 验证记录：提交 `2e92067 Initial LmVIP implementation`；包含 VIP 计算和周期服务基础单测。
- 遗留问题：初版尚未覆盖 once 礼包、语言文件、配置注释补齐、review findings 和完整运行态验证。
- 下一步：补 LuckPerms 适配与 review 收口。
