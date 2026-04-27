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
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class RewardCommandContext(
    val playerName: String,
    val playerId: UUID,
    val level: Int,
    val seasonId: String,
) {
    fun render(command: String): String {
        return command
            .replace("%player%", playerName)
            .replace("%uuid%", playerId.toString())
            .replace("%level%", level.toString())
            .replace("%season%", seasonId)
    }
}

class RewardService(
    private var config: VipRuntimeConfig,
    private val repository: JdbcVipRepository,
) {

    fun updateConfig(config: VipRuntimeConfig) {
        this.config = config
    }

    fun status(snapshot: VipSnapshot, type: ClaimType): ClaimStatus {
        val seasonId = snapshot.seasonId ?: return ClaimStatus(false, false, raw("reward.no-active-season"))
        val level = config.levels.firstOrNull { it.level == snapshot.vipLevel }
            ?: return ClaimStatus(false, false, raw("reward.no-vip-level"))
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
            claimed -> raw("reward.status.claimed")
            available -> raw("reward.status.available")
            type == ClaimType.DAILY -> raw("reward.status.daily-not-enough", "current" to snapshot.dailyPoints, "required" to required)
            else -> raw("reward.status.monthly-not-enough", "current" to snapshot.monthlyPoints, "required" to required)
        }
        return ClaimStatus(available, claimed, reason)
    }

    fun claim(player: Player, snapshot: VipSnapshot, type: ClaimType): OperationResult {
        val seasonId = snapshot.seasonId ?: return OperationResult(false, raw("reward.no-active-season"))
        val level = config.levels.firstOrNull { it.level == snapshot.vipLevel }
            ?: return OperationResult(false, raw("reward.no-vip-level"))
        val status = status(snapshot, type)
        if (!status.available || status.claimed) {
            return OperationResult(false, status.reason)
        }
        val periodKey = periodKey(type)
        if (!repository.insertClaim(player.uniqueId, player.name, seasonId, type.dbKey, level.level, periodKey)) {
            return OperationResult(false, raw("reward.already-claimed"))
        }
        val commands = when (type) {
            ClaimType.DAILY -> level.daily.commands
            ClaimType.WEEKLY -> level.weekly.commands
            ClaimType.MONTHLY -> level.monthly.commands
        }
        val context = RewardCommandContext(player.name, player.uniqueId, level.level, seasonId)
        if (!dispatchCommands(context, commands)) {
            repository.deleteClaim(player.uniqueId, seasonId, type.dbKey, level.level, periodKey)
            return OperationResult(false, raw("reward.dispatch-failed-rollback"))
        }
        return OperationResult(true, raw("reward.claim-success"))
    }

    private fun dispatchCommands(context: RewardCommandContext, commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        val task = {
            runCatching { runRewardCommands(context, commands) }.getOrElse {
                Bukkit.getLogger().warning("[LmVIP] Reward command dispatch failed for ${context.playerName}: ${it.message}")
                false
            }
        }
        return if (Bukkit.isPrimaryThread()) {
            task()
        } else {
            val future = CompletableFuture<Boolean>()
            runCatching {
                Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), Runnable {
                    future.complete(task())
                })
                future.get(config.rewardCommandTimeoutSeconds, TimeUnit.SECONDS)
            }.getOrElse {
                Bukkit.getLogger().warning("[LmVIP] Reward command dispatch failed or timed out for ${context.playerName}: ${it.message}")
                false
            }
        }
    }

    private fun runRewardCommands(context: RewardCommandContext, commands: List<String>): Boolean {
        var success = true
        for (command in commands) {
            val parsed = context.render(command)
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)) {
                Bukkit.getLogger().warning("[LmVIP] Reward command returned false for ${context.playerName}: $parsed")
                success = false
            }
        }
        return success
    }

    fun periodKey(type: ClaimType): String {
        return when (type) {
            ClaimType.DAILY -> config.periods.dayKey()
            ClaimType.WEEKLY -> config.periods.weekKey()
            ClaimType.MONTHLY -> config.periods.monthKey()
        }
    }

    private fun raw(key: String, vararg placeholders: Pair<String, Any?>): String {
        return config.language.raw(key, *placeholders)
    }
}
