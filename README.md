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
3. 将 `build/libs/LmVIP.jar` 放入服务端 `plugins`。
4. 启动后检查生成的 `config.yml`、`levels.yml`、`gui.yml`、`lang.yml`。

Paper/Spigot 1.12.2 通常仍跑在 Java 8。LuckPerms 请使用兼容 Java 8 的 Bukkit 版本，例如 5.4.x；较新的 5.5.x 版本可能要求更高 Java 版本。

插件启动和 `/vipadmin reload` 会检查 `config.yml`、`levels.yml`、`gui.yml`、`lang.yml`。缺失文件会自动生成，已有文件缺少默认 key 时会先备份为 `.bak-时间戳`，再只补缺失项，不覆盖已有值。

已验证环境：

- CatServer/Paper 1.12.2，Java 8
- TabooLib 6 打包产物
- LmCore `database("LmVIP")`
- LuckPerms Bukkit 5.4.x
- PlaceholderAPI 2.10.6
- Docker MySQL，通过 LmCore profile 连接

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

## 配置文件

- `config.yml`：LmCore 数据库 profile、时区、周起始日、缓存和消息前缀。
- `levels.yml`：VIP 等级门槛、LuckPerms group、福利说明、日/周/月/一次性奖励门槛和奖励命令。
- `gui.yml`：GUI 标题、行数、槽位、材质和按钮名。

一次性 VIP 礼包按“每个玩家、每个 VIP 等级永久一次”记录，不随周目重置。配置示例：

```yaml
levels:
  "1":
    once-reward:
      commands:
        - "say %player% 领取了 VIP1 一次性礼包"
```

日/周/月奖励领取记录带当前周目和周期 key。换周目后，日/周/月奖励会在新周目重新计算领取记录；once 礼包不会随周目刷新。

奖励命令发放失败或超时后，本次领取记录会自动回滚。修正配置后，玩家可以重新领取。

## PlaceholderAPI 缓存

PAPI 请求在主线程只读取缓存，不直接查库。缓存过期或缺失时会异步刷新；充值、积分调整、rollback、领奖、reload 会主动刷新或清空缓存。

这意味着计分板高频刷新不会把数据库查询压到主线程。首次请求或刚 reload 后短暂返回空值属于缓存尚未建立，下一次刷新会返回最新值。

## 构建验证

```powershell
F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace
```

## 提测清单

1. 启动服务端，确认 LmVIP、LmCore、LuckPerms 正常启用，`lmvip_%` 表自动创建。
2. 执行 `/vipadmin season start season-test 测试周目`。
3. 执行 `/vipadmin points add 玩家名 recharge 1000 codex order-test-001 smoke`，确认 VIP 等级提升并同步 LuckPerms group。
4. 重复同一个 `source + orderId`，确认不会重复入账。
5. 执行 `/vip claim daily`、`/vip claim weekly`、`/vip claim monthly`，确认可领取且不能重复领取。
6. 打开 `/vip`，点击等级物品领取一次性 VIP 礼包，确认 `%lmvip_once_claimed_<level>%` 立即为 `true`。
7. 执行 `/vipadmin points rollback <transactionId> rollback-smoke`，确认积分归零、VIP group 降级或移除、`%lmvip_level%` 立即更新。
8. 删除或缺失 `lang.yml`、必要配置 key 后重启或 `/vipadmin reload`，确认自动生成或补齐并产生 `.bak-时间戳`。
9. 缺少 `LmCore` 或 `LuckPerms` 时，服务端应阻止 LmVIP 正常启用并输出明确依赖错误。
