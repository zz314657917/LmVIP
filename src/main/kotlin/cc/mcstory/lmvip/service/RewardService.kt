package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.model.ClaimStatus
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.model.OperationResult
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.storage.JdbcVipRepository
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.platform.BukkitPlugin

class RewardService(
    private var config: VipRuntimeConfig,
    private val repository: JdbcVipRepository,
) {

    fun updateConfig(config: VipRuntimeConfig) {
        this.config = config
    }

    fun status(snapshot: VipSnapshot, type: ClaimType): ClaimStatus {
        val seasonId = snapshot.seasonId ?: return ClaimStatus(false, false, "当前没有启用周目")
        val level = config.levels.firstOrNull { it.level == snapshot.vipLevel }
            ?: return ClaimStatus(false, false, "当前没有 VIP 等级")
        val periodKey = periodKey(type)
        val claimed = repository.hasClaim(snapshot.playerId, seasonId, type.dbKey, level.level, periodKey)
        val available = when (type) {
            ClaimType.DAILY -> snapshot.dailyPoints >= level.daily.threshold
            ClaimType.WEEKLY -> snapshot.monthlyPoints >= level.weekly.threshold
            ClaimType.MONTHLY -> snapshot.monthlyPoints >= level.monthly.threshold
        }
        val required = when (type) {
            ClaimType.DAILY -> level.daily.threshold
            ClaimType.WEEKLY -> level.weekly.threshold
            ClaimType.MONTHLY -> level.monthly.threshold
        }
        val reason = when {
            claimed -> "已领取"
            available -> "可领取"
            type == ClaimType.DAILY -> "今日累充不足: ${snapshot.dailyPoints}/$required"
            else -> "本月累充不足: ${snapshot.monthlyPoints}/$required"
        }
        return ClaimStatus(available, claimed, reason)
    }

    fun claim(player: Player, snapshot: VipSnapshot, type: ClaimType): OperationResult {
        val seasonId = snapshot.seasonId ?: return OperationResult(false, "当前没有启用周目")
        val level = config.levels.firstOrNull { it.level == snapshot.vipLevel }
            ?: return OperationResult(false, "当前没有 VIP 等级")
        val status = status(snapshot, type)
        if (!status.available || status.claimed) {
            return OperationResult(false, status.reason)
        }
        val periodKey = periodKey(type)
        if (!repository.insertClaim(player.uniqueId, player.name, seasonId, type.dbKey, level.level, periodKey)) {
            return OperationResult(false, "奖励已经领取")
        }
        val commands = when (type) {
            ClaimType.DAILY -> level.daily.commands
            ClaimType.WEEKLY -> level.weekly.commands
            ClaimType.MONTHLY -> level.monthly.commands
        }
        dispatchCommands(player, level.level, seasonId, commands)
        return OperationResult(true, "领取成功")
    }

    private fun dispatchCommands(player: Player, level: Int, seasonId: String, commands: List<String>) {
        if (commands.isEmpty()) return
        val task = Runnable {
            for (command in commands) {
                val parsed = command
                    .replace("%player%", player.name)
                    .replace("%uuid%", player.uniqueId.toString())
                    .replace("%level%", level.toString())
                    .replace("%season%", seasonId)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)
            }
        }
        if (Bukkit.isPrimaryThread()) {
            task.run()
        } else {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), task)
        }
    }

    fun periodKey(type: ClaimType): String {
        return when (type) {
            ClaimType.DAILY -> config.periods.dayKey()
            ClaimType.WEEKLY -> config.periods.weekKey()
            ClaimType.MONTHLY -> config.periods.monthKey()
        }
    }
}
