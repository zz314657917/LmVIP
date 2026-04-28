# LmVIP 当前任务

## 背景

LmVIP 是基于 TabooLib 6 的周目 VIP 插件，强依赖 LmCore 与 LuckPerms，可选接入 PlaceholderAPI。当前版本已完成 review 修复、一次性 VIP 礼包、配置/语言文件自动补齐、`LmVipApi` 对外查询 API，并在 Docker MySQL + test-cell 中做过核心运行态提测。

## 当前目标

状态：PAPI 高频缓存加固已完成，进入提交与后续正式服复测阶段。

本轮不新增 VIP 玩法，只加固单服缓存策略：PAPI 主线程永远只读内存，缓存过期返回旧值并合并异步刷新，无缓存返回空字符串并合并异步刷新；`LmVipApi#getSnapshotAsync` / `refreshSnapshotAsync` 同玩家 in-flight 查询合并。

## 本次已完成

- 新增 `RefreshingValueCache` 与 `SingleFlight`，覆盖 PAPI 旧值优先、刷新去重、API in-flight 合并。
- `/vipadmin cache stats|clear|warm` 已落地，`lang.yml` 与 README 已补说明。
- 新增 `cache.retain-after-quit-seconds: 300`，默认配置带注释；缺失 key 会通过既有默认合并逻辑自动补齐。
- `PlayerQuitEvent` 增加延迟缓存清理，默认玩家离线 300 秒后清理。
- 修复运行态发现的 LuckPerms 反射问题：只选择零参数 `getNodes` / `getDistinctNodes` / `getOwnNodes`，避免误调用 `getNodes(NodeType)` 导致 `wrong number of arguments`。
- test-cell 验证结束后已停止 cell、恢复 jar/config/ops，释放租约并删除 `lmvip_%` 测试表。

## 已确认事实

- 构建产物：`F:/mcplugins/LmVIP/build/libs/LmVIP.jar`。
- 当前运行依赖：运行时插件名仍为 `LmCore` 与 `LuckPerms`；数据库仍走 LmCore profile `LmVIP`，不接入 LmCore PlayerState V2。
- PAPI 缓存命令可用：`/vipadmin cache stats` 显示 PAPI 缓存数、刷新中、API 加载中、命中、未命中、旧值命中、刷新成功/失败、合并数和最近错误。
- test-cell 中 500 次 `/papi parse zzzderk %lmvip_level%|%lmvip_total_points%|%lmvip_daily_claimed%` 后，缓存统计为命中 498、旧值命中 5、刷新成功 3、失败 0、合并 4，刷新任务没有随解析次数线性增长。
- `/vipadmin cache clear zzzderk` 后首次 PAPI 返回空，`/vipadmin cache warm zzzderk` 可预热回 VIP 3。
- 充值后 PAPI 返回 `3 / 1000 / false`；rollback 后 PAPI 返回 `0 / 0`，LuckPerms 父组从 `default + vip3` 回到仅 `default`。
- 启动时缺失 `cache.retain-after-quit-seconds` 与旧 `lang.yml` cache 消息 key 会自动补齐并生成 `.bak-*`。

## 待验证点

- 未单独等待 300 秒验证玩家退出后的延迟缓存清理；本轮只验证了监听器编译、配置读取和缓存 clear/warm。
- 正式服运营奖励命令仍需按真实礼包内容再验收。
- LmCore-v2 自身测试编译缺类问题不属于 LmVIP，本轮只消费已安装的本地 Maven 依赖。

## 当前结论

可以交付 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar` 进入正式测试服试跑。本轮已经把高频 PAPI 的主线程查库风险和重复异步刷新风险收口，并修复了运行态暴露的 LuckPerms 反射同步问题。

## 下一步

1. 提交本轮代码、README 和知识库更新。
2. 在正式测试服部署当前 `LmVIP.jar`，按 README 提测清单复测真实奖励命令。
3. 如果正式服 TAB/计分板刷新频率很高，持续观察 `/vipadmin cache stats` 中 `刷新中`、`API加载中`、`刷新失败` 和 `最近错误`。

## 验证记录

- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --tests "cc.mcstory.lmvip.cache.*" --stacktrace`：通过。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP test --stacktrace`：通过。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过。
- Docker dev-stack：MySQL `127.0.0.1:3307`、Redis `127.0.0.1:6380` 可用。
- cell-01：临时部署 `LmCore-v2 + LuckPerms 5.4.145 + LmVIP`，完成 cache stats、clear、warm、500 次 PAPI parse、充值、rollback、LuckPerms group 降级验证。
- 清理：cell-01 已释放，测试端口关闭，`LmVIP.jar` 和 `LuckPerms.jar` 已从 cell-01 插件目录移除，原 `LmCore.jar` / `lm-core-1.1.0-SNAPSHOT.jar` 已恢复，`ops.json` 已恢复为空，`lmvip_%` 测试表已删除。
