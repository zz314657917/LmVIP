---
title: Current Focus
type: status
repo: LmVIP
last_verified: 2026-06-26
---

# 当前默认关注点
- 仓库已经不是 planner 阶段，当前事实源是现有实现、`README.md`、`knowledge/tasks/current-task.md` 与 `knowledge/tasks/timeline.md`
- 最近稳定改动集中在奖励 claim 状态机、`claims retry/reset`、LuckPerms legacy 旧组清理、PAPI 缓存、`LmCore ExecutionService` 玩家可见反馈和 1.20.1 双产物
- 当前重点是收口静态审计 P0/P1：claim CAS、reset 保留证据、奖励命令主线程边界、重复订单内容校验、管理金额校验和 LuckPerms 同步失败可见化
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
- 构建产物为 `lmvip-legacy/build/libs/LmVIP-1.12.2.jar` 和 `lmvip-modern/build/libs/LmVIP-1.20.1.jar`，当前公开对外查询入口仍是 Bukkit `ServicesManager` 暴露的 `LmVipApi`
- claim 记录现在使用 `pending`、`running`、`claimed`、`failed`、`manual_review`；`/vipadmin claims retry` 通过数据库 CAS 获取执行权，只续跑失败或未执行命令；`claims reset` 不再删除成功发放证据
- `ExecutionService` 成功路径反馈已经补过 test-cell smoke；重复订单、重复领取、GUI 展示、PAPI 和状态预览不应触发反馈
- 当前剩余高价值验证主要在正式测试服：Paper 1.20.1 runtime smoke、Arclight 1.20.1 业务命令链路、真实运营奖励命令链路，以及玩家离线 300 秒缓存保留的长跑观察
- 余额投影和 LuckPerms outbox 仍是后续增强，不要把本轮最小修复表述为完整多子服金融级一致性方案
