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
            val service = LmVipServices.vipService ?: return@execute sender.sendMessage("LmVIP 未就绪")
            BukkitTasks.async({ service.snapshot(player, force = true) }) { result ->
                val snapshot = result.getOrElse { return@async sender.sendMessage("读取失败: ${it.message}") }
                sender.sendMessage("§6[LmVIP] §fVIP等级: §e${snapshot.vipLevelName}")
                sender.sendMessage("§7总累充: §f${snapshot.totalPoints} §7周目: §f${snapshot.seasonPoints} §7本月: §f${snapshot.monthlyPoints} §7今日: §f${snapshot.dailyPoints}")
                sender.sendMessage("§7当前周目: §f${snapshot.seasonName ?: "未设置"}")
            }
        }
    }

    @CommandBody
    val claim = subCommand {
        dynamic(comment = "daily|weekly|monthly") {
            execute<ProxyCommandSender> { sender, context, _ ->
                val player = sender.cast<Player>()
                val type = ClaimType.parse(context.args().getOrNull(1) ?: "") ?: return@execute sender.sendMessage("用法: /vip claim <daily|weekly|monthly>")
                val service = LmVipServices.vipService ?: return@execute sender.sendMessage("LmVIP 未就绪")
                BukkitTasks.async({
                    val snapshot = service.snapshot(player, force = true)
                    service.rewards.claim(player, snapshot, type)
                }) { result ->
                    val operation = result.getOrElse { return@async sender.sendMessage("领取失败: ${it.message}") }
                    sender.sendMessage((LmVipServices.config?.messagePrefix ?: "") + operation.message)
                }
            }
        }
    }
}
