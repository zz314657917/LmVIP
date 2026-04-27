package cc.mcstory.lmvip.command

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.gui.VipGui
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.util.BukkitTasks
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.*
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(name = "vip", aliases = ["lmvip"], permission = "lmvip.use")
object VipCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.cast<Player>()
            VipGui.open(player)
        }
    }

    @CommandBody
    val info = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.cast<Player>()
            val service = LmVipServices.vipService ?: return@execute sender.sendMessage(LmVipServices.message("common.not-ready"))
            BukkitTasks.async({ service.snapshot(player, force = true) }) { result ->
                val snapshot = result.getOrElse {
                    return@async sender.sendMessage(LmVipServices.message("command.vip.info-read-failed", "error" to it.message))
                }
                sender.sendMessage(LmVipServices.message("command.vip.info.header", "level_name" to snapshot.vipLevelName))
                sender.sendMessage(LmVipServices.rawMessage(
                    "command.vip.info.points",
                    "total" to snapshot.totalPoints,
                    "season" to snapshot.seasonPoints,
                    "monthly" to snapshot.monthlyPoints,
                    "daily" to snapshot.dailyPoints
                ))
                sender.sendMessage(LmVipServices.rawMessage("command.vip.info.season", "season" to (snapshot.seasonName ?: LmVipServices.rawMessage("common.not-set"))))
            }
        }
    }

    @CommandBody
    val claim = subCommand {
        dynamic(comment = "daily|weekly|monthly|once") {
            dynamic(comment = "level", optional = true) {
                execute<ProxyCommandSender> { sender, context, _ -> handleClaim(sender, context.args().toList()) }
            }
            execute<ProxyCommandSender> { sender, context, _ -> handleClaim(sender, context.args().toList()) }
        }
    }

    private fun handleClaim(sender: ProxyCommandSender, args: List<String>) {
        val player = sender.cast<Player>()
        val action = args.getOrNull(1)?.lowercase() ?: return sender.sendMessage(LmVipServices.message("command.vip.claim-usage"))
        val service = LmVipServices.vipService ?: return sender.sendMessage(LmVipServices.message("common.not-ready"))
        if (action == ClaimType.ONCE.dbKey) {
            val level = args.getOrNull(2)?.toIntOrNull() ?: return sender.sendMessage(LmVipServices.message("command.vip.claim-usage"))
            BukkitTasks.async({
                val snapshot = service.snapshot(player, force = true)
                service.rewards.claimOnce(player, snapshot, level)
            }) { result ->
                val operation = result.getOrElse {
                    return@async sender.sendMessage(LmVipServices.message("command.vip.claim-failed", "error" to it.message))
                }
                sender.sendMessage((LmVipServices.config?.messagePrefix ?: "") + operation.message)
            }
            return
        }
        val type = ClaimType.parse(action)?.takeIf { it != ClaimType.ONCE }
            ?: return sender.sendMessage(LmVipServices.message("command.vip.claim-usage"))
        BukkitTasks.async({
            val snapshot = service.snapshot(player, force = true)
            service.rewards.claim(player, snapshot, type)
        }) { result ->
            val operation = result.getOrElse {
                return@async sender.sendMessage(LmVipServices.message("command.vip.claim-failed", "error" to it.message))
            }
            sender.sendMessage((LmVipServices.config?.messagePrefix ?: "") + operation.message)
        }
    }
}
