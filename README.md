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
4. 启动后检查生成的 `config.yml`、`levels.yml`、`gui.yml`。

Paper/Spigot 1.12.2 通常仍跑在 Java 8。LuckPerms 请使用兼容 Java 8 的 Bukkit 版本，例如 5.4.x；较新的 5.5.x 版本可能要求更高 Java 版本。

插件启动和 `/vipadmin reload` 会检查 `config.yml`、`levels.yml`、`gui.yml`、`lang.yml`。缺失文件会自动生成，已有文件缺少默认 key 时会先备份为 `.bak-时间戳`，再只补缺失项，不覆盖已有值。

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

## 构建验证

```powershell
F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace
```
