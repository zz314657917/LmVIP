---
title: Build And Verify
type: build
repo: LmVIP
last_verified: 2026-04-30
---

# 构建工具
- Gradle Kotlin DSL + TabooLib 6
- 产物目标仍为 Java 8，运行时强依赖 `LmCore` 与 `LuckPerms`，`PlaceholderAPI` 可选

# 常用构建命令
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --stacktrace`
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`

# 产物目录
- `F:/mcplugins/LmVIP/build/libs/LmVIP.jar`

# 已验证基线
- `2026-04-30`：`test --stacktrace --rerun-tasks` 通过，`51 tests / 0 failures / 0 errors / 0 skipped`
- `2026-04-30`：`clean build --stacktrace` 通过，产物仍为 `build/libs/LmVIP.jar`
- `2026-04-30`：`cell-01` 成功路径 smoke 已验证 `ExecutionService` 反馈边界。充值成功与 daily 领奖成功会触发反馈；重复订单与重复领取不会产生第二次反馈
- `2026-04-29 ~ 2026-04-30`：Docker MySQL + test-cell 已覆盖 claim `failed -> retry -> claimed`、`claims reset`、`levels.yml` 不反向补默认等级、LuckPerms legacy 旧组清理

# 兼容与提测前置
- 目标环境按当前 README 维持在 CatServer/Paper 1.12.2、Java 8、`LmCore`、LuckPerms Bukkit `5.4.x`
- 使用 Placeholder 变量时再补 `PlaceholderAPI 2.10.6`
- 数据库连接统一走 `LmCore` 的 `database("LmVIP")`

# 手动验证优先级
- 先做 `/vipadmin season start`、`/vipadmin points add`、重复 `source + orderId` 幂等检查
- 再做 `/vip claim daily|weekly|monthly|once`、故障后 `/vipadmin claims retry|reset`、LuckPerms 组同步与 rollback
- 需要玩家可见反馈时，再补 `execution-feedback` 成功路径 smoke 和 `/vipadmin cache stats|clear|warm`

# 仍未覆盖
- `cache.retain-after-quit-seconds` 默认 300 秒的离线缓存保留，当前没有单独长跑验证
- 正式服运营奖励命令仍需要结合真实外部奖励插件再做一次端到端 smoke
