# LmVIP 当前任务

## 背景

LmVIP 是基于 TabooLib 6 的周目 VIP 插件，强依赖 LmCore 与 LuckPerms，可选接入 PlaceholderAPI。当前版本已完成周目充值、永久 VIP、日/周/月/once 奖励、GUI、PAPI、`LmVipApi`、配置/lang 自动补齐、PAPI 高频缓存加固、以及生产前 P2 风险修复。

## 当前状态

状态：生产前 P2/P3 风险修复、LmCore-v2 `ExecutionService` 玩家可见反馈接入、review finding 收口、知识归档、提交前验证和运行态反馈 smoke 已完成；当前已进入 1.20.1 双产物兼容阶段，Gradle 双模块构建、jar 元数据验证和 Arclight 1.20.1 基础启动/relay smoke 已通过。Paper 1.20.1 smoke 与 Arclight 业务命令链路仍待覆盖。

2026-04-29 追加：已完成 LmCore-v2 `ExecutionService` 玩家可见反馈的代码级接入与构建验证。反馈只在充值入账、VIP 等级变化、奖励领取成功、手动权益刷新成功后触发；失败、重复、GUI 展示、PAPI 和状态预览路径不调用 `execute(...)`。本轮未跑 test-cell 真实反馈 smoke。

2026-04-30 追加：`cell-01` 已补运行态反馈 smoke。开周目后充值 100 触发充值成功提示和 actionbar；重复同 order 被识别为重复订单且无第二次反馈；`/vip claim daily` 成功触发领奖成功 actionbar；重复领取只返回“已领取”，未观察到二次奖励反馈。测试后已清理 `exec-115938` / `exec-order-115938` 相关 DB 记录并释放 test-cell。

2026-04-30 13:08 追加：已收口本轮 review findings。奖励命令异步切回主线程时增加 `future.cancel(false)` 和 claim `pending` 状态门闸，timeout 会先把 claim 标记为 failed，迟到的主线程任务或发放过程中状态被改掉时不再继续执行未完成命令；`BukkitTasks.async` 捕获 async/callback 调度失败并保证 callback 有结果；`VipService.refreshAndSync` 增加主线程保护，避免 LuckPerms 同步被误放到主线程。

2026-06-25 追加：已按双产物计划拆出 `lmvip-legacy` 与 `lmvip-modern`。legacy 产物为 `LmVIP-1.12.2.jar`，Java 8 字节码，插件元数据不声明 `api-version`；modern 产物为 `LmVIP-1.20.1.jar`，Java 17 字节码，插件元数据声明 `api-version: 1.20`。两者共享现有业务源码、配置语义、数据库表和 `LmVipApi`。

本轮只修 4 个自检 P2：奖励部分发放后重复领取风险、`levels.yml` 默认补齐污染、`refreshSnapshotAsync` 强制刷新语义、LuckPerms 旧组清理。没有新增 VIP 玩法，也没有修改 `LmVipApi` 公开方法签名。

## 本次已完成

- 奖励领取记录改为 `pending` / `claimed` / `failed` 状态；失败或超时不再删除 claim，玩家不能重复领取。
- 新增 `/vipadmin claims retry <player> <daily|weekly|monthly|once> [level]` 和 `/vipadmin claims reset <player> <daily|weekly|monthly|once> [level]`。
- 奖励命令新增 `%claim_id%`、`%period%`、`%dispatch_id%`，用于外部奖励插件幂等。
- `levels.yml` 已有文件不再自动补回默认 VIP 示例；缺字段只使用读取层默认值并写日志提示。
- `getSnapshotAsync` 和 `refreshSnapshotAsync` 分离 in-flight；普通查询可复用 refresh，强制刷新不复用普通查询。
- 新增 `sync.legacy-groups: []`；LuckPerms 同步会清理当前 VIP 组和 legacy 旧组。
- 新增 `execution-feedback` 配置和 `LmCoreExecutionFeedback` 适配层；通过 Bukkit ServicesManager 反射发现 LmCore `ExecutionService`，请求固定 `source=lmvip` 并携带 `reason/traceId`。
- 自检修复：恢复 `config.yml` 中被注释吞掉的 YAML key，确保 `reward.command-timeout-seconds` 和 `execution-feedback` 默认配置真实生效。
- Review finding 修复：奖励发放 timeout/迟到任务增加 claim 状态门闸，`BukkitTasks.async` 调度拒绝时不再静默丢 callback，`refreshAndSync` 明确只能异步调用。
- 双产物兼容：新增 `RuntimeCompatibilityStatus` / `PlatformCompatibility` 启动诊断，启动日志会输出 artifact、runtimeJava、server、deps 和 verdict；新增 P/G/E workflow 文档记录本轮兼容任务。

## 验证记录

- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --stacktrace`：通过。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过。
- 2026-04-30 提交前复核：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --stacktrace`：通过。
- 2026-04-30 提交前复核：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过，产物仍为 `build/libs/LmVIP.jar`。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --tests cc.mcstory.lmvip.integration.LmCoreExecutionFeedbackTest --stacktrace`：通过。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test`：通过。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP build`：通过。
- `git diff --check`：通过，仅有 Git LF/CRLF 提示。
- Docker MySQL：`lmvip_claims` 自动迁移出 `status`、`dispatch_id`、`failure_reason`、`updated_at`。
- test-cell：充值 100 后 `total/season/monthly/daily=100`，`vip_level=1`，LuckPerms 组同步到 `vip1`。
- test-cell：daily 奖励第二条命令故意失败后，claim 保留为 `failed`；重复 `/vip claim daily` 未再次执行第一条奖励命令。
- test-cell：修复命令后 `/vipadmin claims retry zzzderk daily` 将 claim 从 `failed` 改为 `claimed`。
- test-cell：weekly 奖励失败后 `/vipadmin claims reset zzzderk weekly` 清理 failed 记录。
- test-cell：删除已有 `levels.yml` 中 VIP3 后 `/vipadmin reload` 未把默认 VIP3 写回。
- test-cell：配置 `sync.legacy-groups: [vip_old]` 后，玩家同步前父组为 `default/vip1/vip_old`，执行 `/vipadmin sync zzzderk` 后只剩 `default/vip1`。
- test-cell：`cell-01` 已停止并释放，端口关闭，临时插件文件/配置已恢复，`lmvip_%` 测试表已删除。
- 2026-04-30 ExecutionService runtime：`cell-01` 中 `/vipadmin season start exec-115938 Exec115938`、`/vipadmin points add zzzderk recharge 100 codex exec-order-115938 execution-smoke`、`/vip claim daily` 成功路径均有反馈；重复充值订单和重复 daily claim 未产生第二次反馈。测试后已 stop + release，端口 `25570/38080/38081` 无监听，相关 DB 测试标记已清理。
- 2026-04-30 review finding 收口：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --tests cc.mcstory.lmvip.service.RewardServiceClaimRetryTest --tests cc.mcstory.lmvip.util.BukkitTasksTest --tests cc.mcstory.lmvip.service.VipServiceThreadGuardTest --stacktrace`：通过。
- 2026-04-30 review finding 收口：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --stacktrace --rerun-tasks`：通过，51 tests / 0 failures / 0 errors / 0 skipped。
- 2026-04-30 review finding 收口：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过，产物仍为 `build/libs/LmVIP.jar`。
- 2026-06-25 双产物兼容：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP :lmvip-legacy:test :lmvip-modern:test --stacktrace`：通过。
- 2026-06-25 双产物兼容：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过。
- 2026-06-25 双产物兼容：jar 检查通过，`LmVIP-1.12.2.jar` 的 `LmVipPlugin.class` major=52，`LmVIP-1.20.1.jar` 的 `LmVipPlugin.class` major=61；modern `plugin.yml` 含 `api-version: 1.20`，legacy 不含。
- 2026-06-25 1.20.1 runtime：BlackBoxPro `cell-06` Arclight/Forge 1.20.1 基础 smoke 通过，日志显示 `LmCore database(default): available`、`LmVIP enabled with LmCore database profile 'LmVIP'`，compatibility 为 `artifact=1.20.1/java17, runtimeJava=17.0.18, server=ARCLIGHT/1.20.1-R0.1-SNAPSHOT, deps=LmCore:ENABLED LuckPerms:ENABLED PAPI:ENABLED, verdict=DEGRADED`。

## 已确认事实

- 构建产物路径为 `F:/mcplugins/LmVIP/lmvip-legacy/build/libs/LmVIP-1.12.2.jar` 与 `F:/mcplugins/LmVIP/lmvip-modern/build/libs/LmVIP-1.20.1.jar`。
- 运行时仍依赖 `LmCore` 插件名、`LuckPerms` 和 LmCore database profile `LmVIP`；本插件不接入 LmCore PlayerState V2。
- `lmvip_claims` 旧领取记录默认按 `claimed` 兼容。
- PAPI 高频缓存策略仍保持：主线程只读内存，过期返回旧值并合并刷新，无缓存返回空并合并刷新。

## 剩余风险

- 未单独等待默认 300 秒验证玩家退出后的延迟缓存清理；该点仍建议正式测试服长跑观察。
- 正式服运营奖励命令建议使用单一奖励入口，并消费 `%claim_id%` 或 `%dispatch_id%` 做幂等；LmVIP 不尝试回滚外部插件已发出的物品或货币。
- Paper 1.20.1 真实服务器 smoke 尚未执行。
- Arclight 1.20.1 已完成基础启动/relay smoke，但业务命令链路因 test-cell OP/权限设置未覆盖；不能表述为完整业务 PASS。
