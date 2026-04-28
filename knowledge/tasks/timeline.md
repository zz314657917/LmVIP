# LmVIP 时间轴

## 2026-04-28 15:12 +08:00 - Review 收口与提测归档

- 当前阶段：LmVIP 已从实现收口进入提测中，测试包已构建，当前快照写入 `knowledge/tasks/current-task.md`。
- 本段重点：4 个 review findings 已修复并完成核心运行态验证；once 一次性 VIP 礼包、语言文件、配置注释和缺失 key 自动补齐已纳入当前版本；提测依赖边界已明确为 LmCore、LuckPerms 和 LmCore 的 `LmVIP` 数据库 profile。
- 已完成：`rollback` 后立即重算 VIP 并同步 LuckPerms；PAPI 主线程只读缓存并异步刷新；流水写入区分重复订单、无变更和 SQL 异常；奖励命令失败或超时会回滚本次领取记录；`/vipadmin reload` 会先补齐缺失配置文件和必要 key。
- 关键决策：VIP 等级由跨周目总累充永久决定，LuckPerms 权限不再受月累充激活影响；日、周、月奖励按周目与周期刷新；once 礼包按玩家和 VIP 等级永久一次，不随周目刷新；当前周目只由数据库 active season 决定。
- 验证记录：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace` 通过；Docker MySQL + test-cell 已验证建表、充值、重复订单、领奖、once、rollback、PAPI 和 LuckPerms 同步；缺 `LmCore` 或 `LuckPerms` 时 Bukkit 阻止加载 `LmVIP.jar` 并输出 `UnknownDependencyException`；test-cell 和 `lmvip_%` 测试表已清理。
- 遗留问题：未做长时间高频 PAPI 压测；正式测试服仍需确认 LuckPerms 5.4.x、LmCore `database("LmVIP")` 和运营奖励命令配置；本地 `F:/minecraft/server/paper-1.12.2` 当前缺 LuckPerms，不适合作为直接部署目标。
- 下一步：在目标测试服准备 LmCore、LuckPerms 和数据库 profile；部署 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar`；按 README 提测清单复测开周目、充值、重复订单、领奖、once、rollback 和 PAPI。
