# LmVIP

LmVIP 是一个基于 TabooLib 6 的周目 VIP 系统。

- 强依赖 `LmCore` 与 `LuckPerms`
- 数据库连接统一走 `LmCore` 的 `database("LmVIP")`
- 总累充决定永久 VIP 等级与 LuckPerms 组
- 周目、月、日累充用于奖励资格和展示
- 提供 `/vip` GUI、`/vipadmin` 管理命令和 PlaceholderAPI 变量

## 首次部署

1. 在 `LmCore` 中配置可用的数据库 profile，默认名称为 `LmVIP`。
2. 安装并启用 `LmCore`、`LuckPerms`，需要变量时再安装 `PlaceholderAPI`。
3. 按服务端版本选择产物放入 `plugins`：1.12.2 使用 `lmvip-legacy/build/libs/LmVIP-1.12.2.jar`，1.20.1 使用 `lmvip-modern/build/libs/LmVIP-1.20.1.jar`。
4. 启动后检查生成的 `config.yml`、`levels.yml`、`gui.yml`、`lang.yml`。

Paper/Spigot 1.12.2 通常仍跑在 Java 8。LuckPerms 请使用兼容 Java 8 的 Bukkit 版本，例如 5.4.x；较新的 5.5.x 版本可能要求更高 Java 版本。

插件启动和 `/vipadmin reload` 会检查 `config.yml`、`levels.yml`、`gui.yml`、`lang.yml`。缺失文件会自动生成。`config.yml`、`gui.yml`、`lang.yml` 已有文件缺少默认 key 时会先备份为 `.bak-时间戳`，再只补缺失项，不覆盖已有值。`levels.yml` 是运营业务配置，已有文件不会被默认 VIP 示例反向补齐；单个等级缺字段时使用读取层默认值并输出日志提示。

当前支持矩阵：

- CatServer/Paper 1.12.2，Java 8，产物 `LmVIP-1.12.2.jar`
- Paper/Arclight 1.20.1，Java 17+ 运行时，产物 `LmVIP-1.20.1.jar`
- TabooLib 6 打包产物，两个产物插件名都保持 `LmVIP`
- LmCore `database("LmVIP")`
- LuckPerms Bukkit 5.4.x
- PlaceholderAPI 2.10.6
- Docker MySQL，通过 LmCore profile 连接

1.20.1 产物使用 Java 17 字节码，面向 Paper 和 Arclight 1.20.1 验收。Paper 是主要现代目标；当前 Arclight 1.20.1 已完成基础启动/relay smoke，compatibility verdict 为 `DEGRADED`；业务命令链路和 Paper 1.20.1 仍需单独验收。两个产物都不声明 Folia 支持。

## 周目流程

当前周目由数据库 active season 决定，不按日期推断。

```text
/vipadmin season create season-1 第一周目
/vipadmin season create season-2 第二周目
/vipadmin season create season-8 第八周目
/vipadmin season activate season-8
/vipadmin season start season-9 第九周目
```

历史周目补录充值记录：

```text
/vipadmin points season set 玩家名 season-1 1000 补录第一周目累充
```

正常充值入口：

```text
/vipadmin points add 玩家名 recharge 100 web order-202604270001 充值到账
```

这条命令会同时增加总累充、当前周目累充、当月累充、当日累充。`source + orderId` 会防重复入账。

回滚充值流水：

```text
/vipadmin points rollback 123 订单退款
```

回滚会写入一条反向流水，不删除历史流水。回滚 `recharge` 或 `total` 相关流水后，插件会立即重算玩家 VIP 等级并同步 LuckPerms group。

## 玩家命令

```text
/vip
/vip info
/vip claim daily
/vip claim weekly
/vip claim monthly
/vip claim once <level>
```

## 管理命令

```text
/vipadmin season create <seasonId> <displayName>
/vipadmin season activate <seasonId>
/vipadmin season start <seasonId> <displayName>
/vipadmin season current
/vipadmin season list
/vipadmin season info <seasonId>

/vipadmin points add <player> recharge <amount> <source> <orderId> [reason]
/vipadmin points season add|take|set <player> <seasonId> <amount> [reason]
/vipadmin points season reset <player> <seasonId> [reason]
/vipadmin points add|take|set <player> <total|monthly|daily> <amount> [reason]
/vipadmin points reset <player> <total|monthly|daily> [reason]
/vipadmin points rollback <transactionId> [reason]

/vipadmin info <player> [seasonId]
/vipadmin sync <player>
/vipadmin recalc <player>
/vipadmin cache stats
/vipadmin cache clear [player]
/vipadmin cache warm <player>
/vipadmin claims retry <player> <daily|weekly|monthly|once> [level]
/vipadmin claims reset <player> <daily|weekly|monthly|once> [level]
/vipadmin reload
```

## PlaceholderAPI

```text
%lmvip_level%
%lmvip_level_name%
%lmvip_total_points%
%lmvip_season_id%
%lmvip_season_name%
%lmvip_season_points%
%lmvip_monthly_points%
%lmvip_daily_points%
%lmvip_next_level_need%
%lmvip_daily_reward_available%
%lmvip_weekly_reward_available%
%lmvip_monthly_reward_available%
%lmvip_daily_claimed%
%lmvip_weekly_claimed%
%lmvip_monthly_claimed%
%lmvip_once_claimed_<level>%
%lmvip_once_reward_available_<level>%
```

## 对外 API

其他插件需要做业务判断时，优先通过 Bukkit `ServicesManager` 获取 `LmVipApi`，不要直接调用内部 `LmVipServices`。

```kotlin
import cc.mcstory.lmvip.api.LmVipApi

val api = Bukkit.getServicesManager().load(LmVipApi::class.java) ?: return
val cachedLevel = api.getCachedVipLevel(player.uniqueId)
val cachedTotal = api.getCachedTotalPoints(player.uniqueId)

api.getSnapshotAsync(player.uniqueId, player.name).thenAccept { snapshot ->
    val level = snapshot.vipLevel
    val totalRecharge = snapshot.totalPoints
}
```

- `getCachedVipLevel`、`getCachedTotalPoints` 只读缓存，不查库，主线程安全；缓存不存在或过期时返回 `null`。
- `getSnapshotAsync` 会优先返回有效缓存，缓存缺失时异步查询数据库。
- `refreshSnapshotAsync` 会强制异步刷新数据库快照。
- `totalPoints` 就是跨周目累计充值积分。
- `CompletableFuture` 可能在异步线程完成；回调里如果要操作 Bukkit API，请切回主线程。

## 配置文件

- `config.yml`：LmCore 数据库 profile、时区、周起始日、缓存和消息前缀。
- `levels.yml`：VIP 等级门槛、LuckPerms group、福利说明、日/周/月/一次性奖励门槛和奖励命令。
- `gui.yml`：GUI 标题、行数、槽位、材质和按钮名。

## LmCore ExecutionService 玩家反馈

`config.yml` 的 `execution-feedback` 会在真实成功路径后调用 LmCore-v2 `ExecutionService`，请求固定使用 `source=lmvip`，按动作拆分 `reason`：

- `recharge-success`：充值流水成功写入、VIP 快照刷新和 LuckPerms 同步成功后触发。
- `level-changed`：充值成功后根据新旧总累充计算出 VIP 等级变化时触发。
- `reward-claim-success`：玩家 `/vip claim` 或 GUI 领奖成功、领取记录写为 `claimed` 后触发。
- `benefits-refresh-success`：管理员 `/vipadmin sync` 成功刷新权益后触发，默认关闭。

反馈步骤只允许 LmCore `ExecutionService` 支持的玩家可见动作，例如 `[message]`、`[actionbar]`、`[title]`、`[sound]`、`[broadcast]`、`[actionAll]`、`[titleAll]`、`[close]`、`[delay]`。这里不会接控制台命令、OP、权限组、经验或跨服传送；GUI 展示、PAPI、状态预览、失败领取、重复订单都不会调用 `execute(...)`。

一次性 VIP 礼包按“每个玩家、每个 VIP 等级永久一次”记录，不随周目重置。配置示例：

```yaml
levels:
  "1":
    once-reward:
      commands:
        - "say %player% 领取了 VIP1 一次性礼包"
```

日/周/月奖励领取记录带当前周目和周期 key。换周目后，日/周/月奖励会在新周目重新计算领取记录；once 礼包不会随周目刷新。

奖励领取记录使用 `pending`、`running`、`claimed`、`failed`、`manual_review` 状态。奖励命令发放失败或超时后，本次记录会标记为 `failed`，不会删除记录，也不会允许玩家重复领取。LmVIP 会记录每条奖励命令的发放状态，管理员执行 `/vipadmin claims retry <player> <daily|weekly|monthly|once> [level]` 时会先通过数据库原子状态转换获取执行权，再跳过已经成功的命令，只续跑失败或未执行的同序号命令；不要在部分发放后重排已成功命令。

`/vipadmin claims reset <player> <daily|weekly|monthly|once> [level]` 不再物理删除领取记录。若已有任意奖励命令成功，reset 会被拒绝，必须人工核对后补发剩余奖励；若没有成功命令，记录会转为 `manual_review` 并保留命令发放证据。

奖励命令支持额外占位符：

```text
%claim_id%
%period%
%dispatch_id%
```

生产环境建议把真实礼包收口到单一奖励插件入口，并使用 `%claim_id%` 或 `%dispatch_id%` 做幂等。LmVIP 可以避免自身 retry 重放已成功命令，但无法感知外部插件在 `dispatchCommand` 返回成功后的异步失败。

重复充值订单会校验玩家、维度和金额；同 `source + orderId` 内容一致会返回重复订单，内容不一致会返回失败，不会静默吞掉差异。`/vipadmin points add|take|set` 和充值金额只接受正整数，避免负数反转操作语义。

## PlaceholderAPI 缓存

PAPI 请求在主线程只读取缓存，不直接查库。缓存过期或缺失时会异步刷新；充值、积分调整、rollback、领奖、reload 会主动刷新或清空缓存。

这意味着计分板高频刷新不会把数据库查询压到主线程。首次请求或刚 reload 后短暂返回空值属于缓存尚未建立，下一次刷新会返回最新值。

缓存策略固定为旧值优先：有未过期缓存直接返回，有过期缓存先返回旧值并合并为 1 个异步刷新任务，无缓存时先返回空字符串并合并为 1 个异步刷新任务。外部 `LmVipApi#getSnapshotAsync` / `refreshSnapshotAsync` 对同一玩家也会合并 in-flight 查询。

运维命令：

```text
/vipadmin cache stats
/vipadmin cache clear [player]
/vipadmin cache warm <player>
```

`cache.retain-after-quit-seconds` 控制玩家退出后缓存保留时间，默认 300 秒。高频计分板或 TAB 场景建议先观察 `/vipadmin cache stats` 中的刷新中数量、API 加载中数量、命中、旧值命中、刷新成功/失败和合并次数。

## 构建验证

```powershell
F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP :lmvip-legacy:test :lmvip-modern:test --stacktrace
F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace
```

产物路径：

```text
F:/mcplugins/LmVIP/lmvip-legacy/build/libs/LmVIP-1.12.2.jar
F:/mcplugins/LmVIP/lmvip-modern/build/libs/LmVIP-1.20.1.jar
```

启动时插件会输出 compatibility 诊断，包含产物目标、Java runtime、服务端类型、Bukkit 版本、LmCore/LuckPerms/PlaceholderAPI 状态和 verdict。

## 提测清单

1. 启动服务端，确认 LmVIP、LmCore、LuckPerms 正常启用，`lmvip_%` 表自动创建。
2. 执行 `/vipadmin season start season-test 测试周目`。
3. 执行 `/vipadmin points add 玩家名 recharge 1000 codex order-test-001 smoke`，确认 VIP 等级提升并同步 LuckPerms group。
4. 重复同一个 `source + orderId`，确认不会重复入账。
5. 执行 `/vip claim daily`、`/vip claim weekly`、`/vip claim monthly`，确认可领取且不能重复领取。
6. 打开 `/vip`，点击等级物品领取一次性 VIP 礼包，确认 `%lmvip_once_claimed_<level>%` 立即为 `true`。
7. 执行 `/vipadmin points rollback <transactionId> rollback-smoke`，确认积分归零、VIP group 降级或移除、`%lmvip_level%` 立即更新。
8. 执行 `/vipadmin cache stats`、`/vipadmin cache clear 玩家名`、`/vipadmin cache warm 玩家名`，确认缓存统计、清理和预热正常。
9. 高频执行 `/papi parse 玩家名 %lmvip_level%`、`%lmvip_total_points%`、`%lmvip_daily_claimed%`，确认刷新任务不会随解析次数线性增长。
10. 配置一条多命令奖励并让后续命令失败，确认领取记录标记为 `failed`，玩家不能重复领取；修复同序号失败命令后执行 `/vipadmin claims retry`，确认已成功命令不重放且最终转为 `claimed`。
11. 删除 `levels.yml` 中默认 VIP3 后 `/vipadmin reload`，确认不会被默认示例补回。
12. 配置 `sync.legacy-groups` 后执行 `/vipadmin sync 玩家名`，确认旧 VIP 组会被移除。
13. 删除或缺失 `lang.yml`、必要配置 key 后重启或 `/vipadmin reload`，确认自动生成或补齐并产生 `.bak-时间戳`。
14. 缺少 `LmCore` 或 `LuckPerms` 时，服务端应阻止 LmVIP 正常启用并输出明确依赖错误。
15. 1.20.1 Paper/Arclight smoke 需额外确认 compatibility verdict；Arclight 若显示 degraded/blocked，按日志根因处理，不影响 Paper 结果。
