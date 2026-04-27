package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.LanguageRuntimeConfig
import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.integration.LmVipPlaceholderExpansion
import cc.mcstory.lmvip.model.ClaimStatus
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.model.OperationResult
import cc.mcstory.lmvip.model.VipLevel
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
    companion object {
        const val ONCE_SEASON_ID = "__global__"
        const val ONCE_PERIOD_KEY = "once"
        val PERIODIC_TYPES = listOf(ClaimType.DAILY, ClaimType.WEEKLY, ClaimType.MONTHLY)
    }

    fun updateConfig(config: VipRuntimeConfig) {
        this.config = config
    }

    fun status(snapshot: VipSnapshot, type: ClaimType): ClaimStatus {
        if (type == ClaimType.ONCE) {
            return onceStatus(snapshot, snapshot.vipLevel)
        }
        val seasonId = snapshot.seasonId ?: return ClaimStatus(false, false, raw("reward.no-active-season"))
        val level = config.levels.firstOrNull { it.level == snapshot.vipLevel }
            ?: return ClaimStatus(false, false, raw("reward.no-vip-level"))
        val periodKey = periodKey(type)
        val claimed = repository.hasClaim(snapshot.playerId, seasonId, type.dbKey, level.level, periodKey)
        val available = when (type) {
            ClaimType.DAILY -> snapshot.dailyPoints >= level.daily.threshold
            ClaimType.WEEKLY -> snapshot.monthlyPoints >= level.weekly.threshold
            ClaimType.MONTHLY -> snapshot.monthlyPoints >= level.monthly.threshold
            ClaimType.ONCE -> false
        }
        val required = when (type) {
            ClaimType.DAILY -> level.daily.threshold
            ClaimType.WEEKLY -> level.weekly.threshold
            ClaimType.MONTHLY -> level.monthly.threshold
            ClaimType.ONCE -> 0L
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
        if (type == ClaimType.ONCE) {
            return claimOnce(player, snapshot, snapshot.vipLevel)
        }
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
            ClaimType.ONCE -> level.once.commands
        }
        val context = RewardCommandContext(player.name, player.uniqueId, level.level, seasonId)
        if (!dispatchCommands(context, commands)) {
            repository.deleteClaim(player.uniqueId, seasonId, type.dbKey, level.level, periodKey)
            return OperationResult(false, raw("reward.dispatch-failed-rollback"))
        }
        LmVipPlaceholderExpansion.refresh(player.uniqueId, player.name)
        return OperationResult(true, raw("reward.claim-success"))
    }

    fun onceStatus(snapshot: VipSnapshot, targetLevel: Int): ClaimStatus {
        val level = config.levels.firstOrNull { it.level == targetLevel }
            ?: return ClaimStatus(false, false, raw("reward.once-level-not-found", "level" to targetLevel))
        val claimed = repository.hasClaim(snapshot.playerId, ONCE_SEASON_ID, ClaimType.ONCE.dbKey, level.level, ONCE_PERIOD_KEY)
        return OnceRewardPolicy.status(snapshot.vipLevel, level, claimed, config.language)
    }

    fun claimOnce(player: Player, snapshot: VipSnapshot, targetLevel: Int): OperationResult {
        val level = config.levels.firstOrNull { it.level == targetLevel }
            ?: return OperationResult(false, raw("reward.once-level-not-found", "level" to targetLevel))
        val status = onceStatus(snapshot, targetLevel)
        if (!status.available || status.claimed) {
            return OperationResult(false, status.reason)
        }
        if (!repository.insertClaim(player.uniqueId, player.name, ONCE_SEASON_ID, ClaimType.ONCE.dbKey, level.level, ONCE_PERIOD_KEY)) {
            return OperationResult(false, raw("reward.already-claimed"))
        }
        val context = RewardCommandContext(player.name, player.uniqueId, level.level, snapshot.seasonId ?: ONCE_SEASON_ID)
        if (!dispatchCommands(context, level.once.commands)) {
            repository.deleteClaim(player.uniqueId, ONCE_SEASON_ID, ClaimType.ONCE.dbKey, level.level, ONCE_PERIOD_KEY)
            return OperationResult(false, raw("reward.dispatch-failed-rollback"))
        }
        LmVipPlaceholderExpansion.refresh(player.uniqueId, player.name)
        return OperationResult(true, raw("reward.once-claim-success", "level" to level.level, "level_name" to level.plainName))
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
            ClaimType.ONCE -> ONCE_PERIOD_KEY
        }
    }

    private fun raw(key: String, vararg placeholders: Pair<String, Any?>): String {
        return config.language.raw(key, *placeholders)
    }
}

object OnceRewardPolicy {

    fun status(snapshotVipLevel: Int, targetLevel: VipLevel, claimed: Boolean, language: LanguageRuntimeConfig): ClaimStatus {
        return when {
            claimed -> ClaimStatus(false, true, language.raw("reward.status.claimed"))
            snapshotVipLevel < targetLevel.level -> ClaimStatus(
                available = false,
                claimed = false,
                reason = language.raw(
                    "reward.once-not-reached",
                    "level" to targetLevel.level,
                    "level_name" to targetLevel.plainName
                )
            )
            else -> ClaimStatus(true, false, language.raw("reward.status.available"))
        }
    }
}
