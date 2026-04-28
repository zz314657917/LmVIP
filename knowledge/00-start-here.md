# LmVIP 知识入口

## 当前入口

- 当前快照：[tasks/current-task.md](tasks/current-task.md)
- 阶段时间轴：[tasks/timeline.md](tasks/timeline.md)
- 用户与运维文档：../README.md

## 项目定位

LmVIP 是 TabooLib 6 + Kotlin 的周目 VIP 插件，面向 Paper/Spigot/CatServer 1.12.2。插件强依赖 LmCore 与 LuckPerms，可选接入 PlaceholderAPI。

核心规则：

- 跨周目总累充决定永久 VIP 等级。
- LuckPerms group 只跟永久 VIP 等级同步。
- 当前周目由数据库 active season 决定。
- 日、周、月奖励按周目和周期刷新。
- once 一次性 VIP 礼包按玩家和 VIP 等级永久一次。

## 重要模块

- `src/main/kotlin/cc/mcstory/lmvip/LmVipPlugin.kt`：插件生命周期、服务初始化、Bukkit Services API 注册。
- `src/main/kotlin/cc/mcstory/lmvip/api/`：对外 `LmVipApi`，供其他插件查询 VIP 等级和累计充值。
- `src/main/kotlin/cc/mcstory/lmvip/service/`：VIP 快照、积分、rollback、奖励领取核心逻辑。
- `src/main/kotlin/cc/mcstory/lmvip/storage/JdbcVipRepository.kt`：LmCore 数据库接入、表结构、流水和领取记录。
- `src/main/kotlin/cc/mcstory/lmvip/integration/`：LuckPerms 与 PlaceholderAPI 边界层。
- `src/main/resources/`：带注释的默认配置、语言文件、GUI 与等级奖励配置。

## 构建与验证

```powershell
F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace
```

提测包：

```text
F:/mcplugins/LmVIP/build/libs/LmVIP.jar
```

## 接手注意

- 不要让外部插件直接调用内部 `LmVipServices`；跨插件查询走 `LmVipApi`。
- 主线程只使用 `getCachedVipLevel`、`getCachedTotalPoints` 这类缓存读 API；需要查库时走异步 API。
- 配置和语言文件要求带注释；缺失文件或必要 key 要在启动和 `/vipadmin reload` 时自动补齐。
- 当前已验证 Docker MySQL + test-cell 核心链路，但正式测试服仍需验证 LmCore `database("LmVIP")`、LuckPerms 5.4.x 和运营奖励命令。
