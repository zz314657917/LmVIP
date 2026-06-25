---
title: Current Focus
type: status
repo: LmVIP
last_verified: 2026-04-30
---

# 当前默认关注点
- 仓库已经不是 planner 阶段，当前事实源是现有实现、`README.md`、`knowledge/tasks/current-task.md` 与 `knowledge/tasks/timeline.md`
- 最近稳定改动集中在奖励 claim 状态机、`claims retry/reset`、LuckPerms legacy 旧组清理、PAPI 缓存与 `LmCore ExecutionService` 玩家可见反馈
- 本轮重点不是新增 VIP 玩法，而是保持 `1.12.2 + Java 8 + LmCore + LuckPerms 5.4.x` 这条运行基线稳定
- `ExecutionService` 只负责成功路径的玩家反馈，不接管充值流水、奖励幂等、VIP 业务状态或 LuckPerms 同步

# 接手时建议先看
- `src/main/kotlin/cc/mcstory/lmvip/service/RewardService.kt`
- `src/main/kotlin/cc/mcstory/lmvip/service/VipService.kt`
- `src/main/kotlin/cc/mcstory/lmvip/storage/JdbcVipRepository.kt`
- `src/main/kotlin/cc/mcstory/lmvip/integration/LmCoreExecutionFeedback.kt`
- `src/main/kotlin/cc/mcstory/lmvip/integration/LuckPermsGroupSync.kt`
- `src/main/resources/config.yml`
- `README.md`

# 当前更稳定的结论
- 构建产物仍是 `build/libs/LmVIP.jar`，当前公开对外查询入口仍是 Bukkit `ServicesManager` 暴露的 `LmVipApi`
- claim 记录现在使用 `pending`、`claimed`、`failed`；`/vipadmin claims retry` 只续跑失败或未执行命令，`claims reset` 只清理 `failed/pending`
- `ExecutionService` 成功路径反馈已经补过 test-cell smoke；重复订单、重复领取、GUI 展示、PAPI 和状态预览不应触发反馈
- 当前剩余高价值验证主要在正式测试服：真实运营奖励命令链路，以及玩家离线 300 秒缓存保留的长跑观察
