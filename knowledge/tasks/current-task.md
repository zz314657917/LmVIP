# LmVIP 当前任务

## 背景

LmVIP 是基于 TabooLib 6 的周目 VIP 插件，强依赖 LmCore 与 LuckPerms，可选接入 PlaceholderAPI。当前版本已完成 review 修复、一次性 VIP 礼包、配置/语言文件自动补齐、`LmVipApi` 对外查询 API，并完成 Docker MySQL + test-cell 运行态提测。

## 当前目标

状态：提测完成，进入交付测试包阶段。

本轮不新增玩法功能，只收口 `LmCore-v2` 运行依赖、`LmVipApi` 跨插件查询、正式奖励配置失败路径、高频展示入口的缓存行为，以及测试环境清理。

## 本次已完成

- 提交知识更新：`228f4d5 记录 LmVIP LmCore-v2 接入验证`。
- 刷新 `LmCore-v2` 本地 Maven 基线：`mvn clean install -DskipTests` 会卡在 testCompile 缺少 `DefaultExpressionService`、`DefaultRuleService`、`DefaultActionService`；实际使用 `mvn clean install "-DskipTests" "-Dmaven.test.skip=true"` 成功安装 `cc.mcstory:lm-core:1.1.0-SNAPSHOT`。
- 刷新 LmVIP 构建基线：`F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace` 通过，产物为 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar`。
- 创建临时 `LmVipApiProbe` 插件并在 test-cell 通过 Bukkit `ServicesManager` 获取 `LmVipApi`，验证缓存查询和异步 snapshot 查询。
- 完成 Docker MySQL + cell-02 运行态 smoke：开周目、充值、重复订单、领奖、once、PAPI、API probe、rollback、奖励命令失败回滚、配置自动补齐。
- 完成依赖缺失验证：缺 `LmCore` 或缺 `LuckPerms` 时，Bukkit 均阻止加载 `LmVIP.jar` 并输出 `UnknownDependencyException`。
- 完成收尾清理：cell-02 已恢复原始插件状态并释放租约，端口 `25575`、`38090`、`38091` 已关闭，`lmvip_%` 测试表已清空，临时 API probe jar 和源码已删除。

## 已确认事实

- 构建产物：`F:/mcplugins/LmVIP/build/libs/LmVIP.jar`。
- 当前运行依赖：运行时插件名仍为 `LmCore` 与 `LuckPerms`；数据库仍走 LmCore profile `LmVIP`，不接入 LmCore PlayerState V2。
- API 构建内容：`LmVIP.jar` 已包含 `LmVipApi`、`VipApiSnapshot`、`BukkitLmVipApi`。
- LmCore 状态：test-cell 中 `database(LmVIP): available`，`registered-handles=0` 属于预期，因为 LmVIP 当前业务真源是 `lmvip_*` 表和 LuckPerms group。
- 核心 smoke：`/vipadmin season start season-final-191837 FinalSmoke` 成功；`/vipadmin points add zzzderk recharge 1000 codex order-final-191837 smoke` 写入交易 `id=1`；重复 `source + orderId` 返回重复订单，不重复入账。
- 积分与权限：充值后 `zzzderk total_points=1000, vip_level=3`，LuckPerms 父组为 `default + vip3`；rollback 后账户积分归零，父组回到仅 `default`。
- PAPI 与 API：充值后 `%lmvip_level%=3`、`%lmvip_total_points%=1000`；probe 返回 `cachedLevel=3`、`cachedTotal=1000`、`asyncLevel=3`、`asyncTotal=1000`。rollback 后 PAPI 与 probe 均回到 0。
- 奖励：once/daily/weekly/monthly 可领取且重复领取失败；失败奖励命令会提示“奖励发放失败，领取记录已回滚”，DB 证据为 once level 3 失败后 `lmvip_claims` 中无 level 3 记录。
- 配置补齐：删除 `lang.yml` 并删掉 `config.yml` 的 `reward.command-timeout-seconds` 后执行 `/vipadmin reload`，语言文件和缺失 key 均自动恢复，并生成 `config.yml.bak-*`。

## 待验证点

- 未做长时间高频计分板或聊天 PAPI 压测；当前只验证了主线程变量读取和变更后刷新结果。
- 正式服运营奖励命令仍需按实际道具、权限、礼包内容配置后再做一次发放验收。
- LmCore-v2 自身测试编译缺类问题不属于 LmVIP，本轮只通过 `-Dmaven.test.skip=true` 刷新本地 Maven 依赖。

## 当前结论

可以交付 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar` 进入正式测试服试跑。代码层面的 review findings、API 查询、配置补齐和核心运行态链路已通过；剩余风险集中在正式服运营奖励命令内容和长时间高频 PAPI 压力。

## 下一步

1. 在正式测试服部署 `LmCore-v2` 对应 `LmCore.jar`、LuckPerms Bukkit 5.4.x、可选 PlaceholderAPI，并确认 LmCore 存在 `database("LmVIP")` profile。
2. 部署 `F:/mcplugins/LmVIP/build/libs/LmVIP.jar` 后，按 README 提测清单跑一次正式奖励命令验收。
3. 若有计分板高频刷新场景，做 10-30 分钟 PAPI 压力观察，关注主线程卡顿和数据库访问日志。

## 验证记录

- `mvn clean install "-DskipTests" "-Dmaven.test.skip=true"` in `F:/mcplugins/LmCore-v2`：通过。
- `F:/mcplugins/LmBattlePass/gradlew.bat -p F:/mcplugins/LmVIP clean build --stacktrace`：通过。
- Docker dev-stack：MySQL `127.0.0.1:3307`、Redis `127.0.0.1:6380` 均健康。
- cell-02：已部署临时 `LmCore-v2 + LuckPerms 5.4.145 + LmVIP + LmVipApiProbe` 完成运行态验证。
- DB：测试中创建 `lmvip_accounts`、`lmvip_admin_audit`、`lmvip_claims`、`lmvip_seasons`、`lmvip_transactions`；收尾后 `SHOW TABLES LIKE 'lmvip_%'` 为空。
- 清理：cell-02 已释放，测试端口关闭，`LmVIP.jar` 和 `LmVipApiProbe.jar` 不再留在 cell-02 插件目录。
