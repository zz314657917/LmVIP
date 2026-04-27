package cc.mcstory.lmvip.command

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.config.VipConfigManager
import cc.mcstory.lmvip.model.PointDimension
import cc.mcstory.lmvip.util.BukkitTasks
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.*

@CommandHeader(name = "vipadmin", aliases = ["lmvipadmin"], permission = "lmvip.admin")
object VipAdminCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ -> help(sender) }
    }

    @CommandBody
    val season = subCommand {
        dynamic(comment = "action") {
            dynamic(comment = "seasonId", optional = true) {
                dynamic(comment = "displayName", optional = true) {
                    execute<ProxyCommandSender> { sender, context, _ ->
                        handleSeason(sender, context.args().toList())
                    }
                }
                execute<ProxyCommandSender> { sender, context, _ -> handleSeason(sender, context.args().toList()) }
            }
            execute<ProxyCommandSender> { sender, context, _ -> handleSeason(sender, context.args().toList()) }
        }
    }

    @CommandBody
    val points = subCommand {
        dynamic(comment = "action") {
            dynamic(comment = "playerOrRollbackId", optional = true) {
                dynamic(comment = "arg1", optional = true) {
                    dynamic(comment = "arg2", optional = true) {
                        dynamic(comment = "arg3", optional = true) {
                            dynamic(comment = "arg4", optional = true) {
                                dynamic(comment = "arg5", optional = true) {
                                    execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                                }
                                execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                            }
                            execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                        }
                        execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                    }
                    execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
                }
                execute<ProxyCommandSender> { sender, context, _ -> handlePoints(sender, context.args().toList()) }
            }
        }
    }

    @CommandBody
    val info = subCommand {
        dynamic(comment = "player") {
            dynamic(comment = "seasonId", optional = true) {
                execute<ProxyCommandSender> { sender, context, _ -> handleInfo(sender, context.args().toList()) }
            }
            execute<ProxyCommandSender> { sender, context, _ -> handleInfo(sender, context.args().toList()) }
        }
    }

    @CommandBody
    val sync = subCommand {
        dynamic(comment = "player") {
            execute<ProxyCommandSender> { sender, context, _ ->
                val player = offline(context.args().getOrNull(1) ?: return@execute)
                val service = LmVipServices.vipService ?: return@execute sender.sendMessage("LmVIP 未就绪")
                BukkitTasks.async({ service.refreshAndSync(player) }) { result ->
                    val snapshot = result.getOrElse { return@async sender.sendMessage("同步失败: ${it.message}") }
                    sender.sendMessage("已同步 ${player.name}: VIP ${snapshot.vipLevel}")
                }
            }
        }
    }

    @CommandBody
    val recalc = subCommand {
        dynamic(comment = "player") {
            execute<ProxyCommandSender> { sender, context, _ -> handleInfo(sender, listOf("info", context.args()[1])) }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val plugin = taboolib.platform.BukkitPlugin.getInstance()
            val current = LmVipServices.vipService
            val loaded = VipConfigManager.load(plugin)
            LmVipServices.updateConfig(loaded)
            current?.updateConfig(loaded)
            sender.sendMessage("配置已重载")
        }
    }

    private fun handleSeason(sender: ProxyCommandSender, args: List<String>) {
        val repository = LmVipServices.repository ?: return sender.sendMessage("LmVIP 未就绪")
        val action = args.getOrNull(1)?.lowercase() ?: return helpSeason(sender)
        val operator = sender.name
        BukkitTasks.async({
            when (action) {
                "create" -> {
                    val id = requireArg(args, 2, "seasonId")
                    val name = args.getOrNull(3) ?: id
                    "创建周目 $id: ${repository.createSeason(id, name, operator)}"
                }
                "activate" -> {
                    val id = requireArg(args, 2, "seasonId")
                    "激活周目 $id: ${repository.activateSeason(id, operator)}"
                }
                "start" -> {
                    val id = requireArg(args, 2, "seasonId")
                    val name = args.getOrNull(3) ?: id
                    "开启周目 $id: ${repository.startSeason(id, name, operator)}"
                }
                "current" -> repository.activeSeason()?.let { "当前周目: ${it.seasonId} ${it.displayName}" } ?: "当前没有启用周目"
                "list" -> repository.listSeasons().joinToString("\n") { "${if (it.active) "*" else "-"} ${it.seasonId} ${it.displayName}" }
                "info" -> {
                    val id = requireArg(args, 2, "seasonId")
                    repository.listSeasons().firstOrNull { it.seasonId == id }?.let { "${it.seasonId} ${it.displayName} active=${it.active}" } ?: "周目不存在"
                }
                else -> "未知 season 操作"
            }
        }) { result -> sender.sendMessage(result.getOrElse { "操作失败: ${it.message}" }) }
    }

    private fun handlePoints(sender: ProxyCommandSender, args: List<String>) {
        val service = LmVipServices.vipService ?: return sender.sendMessage("LmVIP 未就绪")
        val action = args.getOrNull(1)?.lowercase() ?: return helpPoints(sender)
        val operator = sender.name
        if (action == "rollback") {
            val id = args.getOrNull(2)?.toLongOrNull() ?: return sender.sendMessage("用法: /vipadmin points rollback <transactionId> [reason]")
            val reason = args.getOrNull(3) ?: "rollback"
            BukkitTasks.async({ service.rollback(id, operator, reason) }) { result ->
                sender.sendMessage(transactionMessage("回滚流水", result, "失败或重复"))
            }
            return
        }
        if (action == "season") {
            val op = args.getOrNull(2)?.lowercase() ?: return helpPoints(sender)
            val player = offline(args.getOrNull(3) ?: return helpPoints(sender))
            val seasonId = args.getOrNull(4) ?: return helpPoints(sender)
            val amount = args.getOrNull(5)?.toLongOrNull()
            val reason = if (op == "reset") {
                args.getOrNull(5) ?: "season-reset"
            } else {
                args.getOrNull(6) ?: "season-$op"
            }
            BukkitTasks.async({
                when (op) {
                    "add" -> service.adjustSeason(player, seasonId, null, requireNotNull(amount), operator, reason)
                    "take" -> service.adjustSeason(player, seasonId, null, -requireNotNull(amount), operator, reason)
                    "set" -> service.adjustSeason(player, seasonId, requireNotNull(amount), null, operator, reason)
                    "reset" -> service.adjustSeason(player, seasonId, 0L, null, operator, reason)
                    else -> null
                }
            }) { result -> sender.sendMessage(transactionMessage("周目积分流水", result)) }
            return
        }
        val player = offline(args.getOrNull(2) ?: return helpPoints(sender))
        if (action == "add" && args.getOrNull(3).equals("recharge", true)) {
            val amount = args.getOrNull(4)?.toLongOrNull() ?: return helpPoints(sender)
            val source = args.getOrNull(5) ?: return helpPoints(sender)
            val order = args.getOrNull(6) ?: return helpPoints(sender)
            val reason = args.getOrNull(7) ?: "recharge"
            BukkitTasks.async({ service.addRecharge(player, amount, source, order, operator, reason) }) { result ->
                sender.sendMessage(transactionMessage("充值流水", result, "重复订单或失败"))
            }
            return
        }
        val dimension = PointDimension.parse(args.getOrNull(3) ?: "") ?: return helpPoints(sender)
        val amount = args.getOrNull(4)?.toLongOrNull()
        val reason = if (action == "reset") {
            args.getOrNull(4) ?: "reset-${dimension.dbKey}"
        } else {
            args.getOrNull(5) ?: "$action-${dimension.dbKey}"
        }
        BukkitTasks.async({
            when (action) {
                "add" -> service.adjustDimension(player, dimension, null, requireNotNull(amount), operator, reason)
                "take" -> service.adjustDimension(player, dimension, null, -requireNotNull(amount), operator, reason)
                "set" -> service.adjustDimension(player, dimension, requireNotNull(amount), null, operator, reason)
                "reset" -> service.adjustDimension(player, dimension, 0L, null, operator, reason)
                else -> null
            }
        }) { result -> sender.sendMessage(transactionMessage("积分流水", result)) }
    }

    private fun handleInfo(sender: ProxyCommandSender, args: List<String>) {
        val service = LmVipServices.vipService ?: return sender.sendMessage("LmVIP 未就绪")
        val player = offline(args.getOrNull(1) ?: return sender.sendMessage("用法: /vipadmin info <player> [seasonId]"))
        val seasonId = args.getOrNull(2)
        BukkitTasks.async({ service.snapshot(player, seasonId, force = true) }) { result ->
            val snapshot = result.getOrElse { return@async sender.sendMessage("查询失败: ${it.message}") }
            sender.sendMessage("§6${player.name} VIP信息")
            sender.sendMessage("§7周目: §f${snapshot.seasonId ?: "-"} ${snapshot.seasonName ?: ""}")
            sender.sendMessage("§7VIP: §f${snapshot.vipLevelName} §7总累充: §f${snapshot.totalPoints}")
            sender.sendMessage("§7周目累充: §f${snapshot.seasonPoints} §7月: §f${snapshot.monthlyPoints} §7日: §f${snapshot.dailyPoints}")
        }
    }

    private fun offline(name: String): OfflinePlayer = Bukkit.getOfflinePlayer(name)

    private fun requireArg(args: List<String>, index: Int, label: String): String {
        return args.getOrNull(index) ?: throw IllegalArgumentException("missing $label")
    }

    private fun transactionMessage(prefix: String, result: Result<Long?>, emptyText: String = "无变更"): String {
        return result.fold(
            onSuccess = { "$prefix: ${it ?: emptyText}" },
            onFailure = { "${prefix}失败: ${it.message ?: it.javaClass.simpleName}" }
        )
    }

    private fun help(sender: ProxyCommandSender) {
        sender.sendMessage("§6/vipadmin season <create|activate|start|current|list|info>")
        sender.sendMessage("§6/vipadmin points add <player> recharge <amount> <source> <orderId>")
        sender.sendMessage("§6/vipadmin points season <add|take|set|reset> <player> <seasonId> [amount]")
        sender.sendMessage("§6/vipadmin info <player> [seasonId]")
    }

    private fun helpSeason(sender: ProxyCommandSender) = sender.sendMessage("用法: /vipadmin season <create|activate|start|current|list|info>")
    private fun helpPoints(sender: ProxyCommandSender) = sender.sendMessage("用法: /vipadmin points add <player> recharge <amount> <source> <orderId>")
}
