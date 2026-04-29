# LmVIP 当前任务

## 背景

LmVIP 是基于 TabooLib 6 的周目 VIP 插件，强依赖 LmCore 与 LuckPerms，可选接入 PlaceholderAPI。当前版本已完成周目充值、永久 VIP、日/周/月/once 奖励、GUI、PAPI、`LmVipApi`、配置/lang 自动补齐、PAPI 高频缓存加固、以及生产前 P2 风险修复。

## 当前状态

状态：生产前 P2 风险修复已完成本地实现、自动化测试、Docker MySQL + test-cell 运行态验证和环境清理，准备提交。

本轮只修 4 个自检 P2：奖励部分发放后重复领取风险、`levels.yml` 默认补齐污染、`refreshSnapshotAsync` 强制刷新语义、LuckPerms 旧组清理。没有新增 VIP 玩法，也没有修改 `LmVipApi` 公开方法签名。

## 本次已完成

- 奖励领取记录改为 `pending` / `claimed` / `failed` 状态；失败或超时不再删除 claim，玩家不能重复领取。
- 新增 `/vipadmin claims retry <player> <daily|weekly|monthly|once> [level]` 和 `/vipadmin claims reset <player> <daily|weekly|monthly|once> [level]`。
- 奖励命令新增 `%claim_id%`、`%period%`、`%dispatch_id%`，用于外部奖励插件幂等。
- `levels.yml` 已有文件不再自动补回默认 VIP 示例；缺字段只使用读取层默认值并写日志提示。
- `getSnapshotAsync` 和 `refreshSnapshotAsync` 分离 in-flight；普通查询可复用 refresh，强制刷新不复用普通查询。
- 新增 `sync.legacy-groups: []`；LuckPerms 同步会清理当前 VIP 组和 legacy 旧组。

## 验证记录

- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --stacktrace`：通过。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过。
- `git diff --check`：通过，仅有 Git LF/CRLF 提示。
- Docker MySQL：`lmvip_claims` 自动迁移出 `status`、`dispatch_id`、`failure_reason`、`updated_at`。
- test-cell：充值 100 后 `total/season/monthly/daily=100`，`vip_level=1`，LuckPerms 组同步到 `vip1`。
- test-cell：daily 奖励第二条命令故意失败后，claim 保留为 `failed`；重复 `/vip claim daily` 未再次执行第一条奖励命令。
- test-cell：修复命令后 `/vipadmin claims retry zzzderk daily` 将 claim 从 `failed` 改为 `claimed`。
- test-cell：weekly 奖励失败后 `/vipadmin claims reset zzzderk weekly` 清理 failed 记录。
- test-cell：删除已有 `levels.yml` 中 VIP3 后 `/vipadmin reload` 未把默认 VIP3 写回。
- test-cell：配置 `sync.legacy-groups: [vip_old]` 后，玩家同步前父组为 `default/vip1/vip_old`，执行 `/vipadmin sync zzzderk` 后只剩 `default/vip1`。
- test-cell：`cell-01` 已停止并释放，端口关闭，临时插件文件/配置已恢复，`lmvip_%` 测试表已删除。

## 已确认事实

- 构建产物路径仍为 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar`。
- 运行时仍依赖 `LmCore` 插件名、`LuckPerms` 和 LmCore database profile `LmVIP`；本插件不接入 LmCore PlayerState V2。
- `lmvip_claims` 旧领取记录默认按 `claimed` 兼容。
- PAPI 高频缓存策略仍保持：主线程只读内存，过期返回旧值并合并刷新，无缓存返回空并合并刷新。

## 剩余风险

- 未单独等待默认 300 秒验证玩家退出后的延迟缓存清理；该点仍建议正式测试服长跑观察。
- 正式服运营奖励命令建议使用单一奖励入口，并消费 `%claim_id%` 或 `%dispatch_id%` 做幂等；LmVIP 不尝试回滚外部插件已发出的物品或货币。
