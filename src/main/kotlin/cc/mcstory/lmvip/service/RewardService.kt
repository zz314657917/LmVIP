package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.LanguageRuntimeConfig
import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.integration.LmVipPlaceholderExpansion
import cc.mcstory.lmvip.model.ClaimCommandRecord
import cc.mcstory.lmvip.model.ClaimCommandStatus
import cc.mcstory.lmvip.model.ClaimDispatchStatus
import cc.mcstory.lmvip.model.ClaimRecord
import cc.mcstory.lmvip.model.ClaimStatus
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.model.ClaimWriteResult
import cc.mcstory.lmvip.model.OperationResult
import cc.mcstory.lmvip.model.VipLevel
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.storage.JdbcVipRepository
import cc.mcstory.lmvip.util.CommandHash
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.platform.BukkitPlugin
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class RewardCommandContext(
    val playerName: String,
    val playerId: UUID,
    val level: Int,
    val seasonId: String,
    val claimId: Long = 0L,
    val periodKey: String = "",
    val dispatchId: String = "",
) {
    fun render(command: String): String {
        return command
            .replace("%player%", playerName)
            .replace("%uuid%", playerId.toString())
            .replace("%level%", level.toString())
            .replace("%season%", seasonId)
            .replace("%claim_id%", claimId.toString())
            .replace("%period%", periodKey)
            .replace("%dispatch_id%", dispatchId)
    }
}

interface RewardCommandExecutor {
    fun execute(context: RewardCommandContext, commandTemplate: String): Boolean
}

object BukkitRewardCommandExecutor : RewardCommandExecutor {
    override fun execute(context: RewardCommandContext, commandTemplate: String): Boolean {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), context.render(commandTemplate))
    }
}

data class CommandDispatchOutcome(
    val success: Boolean,
    val message: String,
)

class RewardService(
    private var config: VipRuntimeConfig,
    private val repository: JdbcVipRepository,
    private val commandExecutor: RewardCommandExecutor = BukkitRewardCommandExecutor,
    private val serverThreadDispatcher: ((Long, () -> CommandDispatchOutcome) -> CommandDispatchOutcome)? = null,
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
        repository.findClaim(snapshot.playerId, seasonId, type.dbKey, level.level, periodKey)?.let { return statusForClaim(it) }
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
            available -> raw("reward.status.available")
            type == ClaimType.DAILY -> raw("reward.status.daily-not-enough", "current" to snapshot.dailyPoints, "required" to required)
            else -> raw("reward.status.monthly-not-enough", "current" to snapshot.monthlyPoints, "required" to required)
        }
        return ClaimStatus(available, false, reason)
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
        val commands = when (type) {
            ClaimType.DAILY -> level.daily.commands
            ClaimType.WEEKLY -> level.weekly.commands
            ClaimType.MONTHLY -> level.monthly.commands
            ClaimType.ONCE -> level.once.commands
        }
        val claim = when (val write = repository.beginClaim(player.uniqueId, player.name, seasonId, type.dbKey, level.level, periodKey, commands)) {
            is ClaimWriteResult.Inserted -> write.claim
            is ClaimWriteResult.Existing -> return OperationResult(false, statusForClaim(write.claim).reason)
        }
        val context = RewardCommandContext(player.name, player.uniqueId, level.level, seasonId, claim.id, periodKey, claim.dispatchId ?: "")
        val dispatch = dispatchCommands(context, claim.id, commands, ClaimDispatchStatus.PENDING)
        if (!dispatch.success) {
            return OperationResult(false, dispatch.message)
        }
        LmVipPlaceholderExpansion.refresh(player.uniqueId, player.name)
        return OperationResult(true, raw("reward.claim-success"))
    }

    fun onceStatus(snapshot: VipSnapshot, targetLevel: Int): ClaimStatus {
        val level = config.levels.firstOrNull { it.level == targetLevel }
            ?: return ClaimStatus(false, false, raw("reward.once-level-not-found", "level" to targetLevel))
        repository.findClaim(snapshot.playerId, ONCE_SEASON_ID, ClaimType.ONCE.dbKey, level.level, ONCE_PERIOD_KEY)?.let {
            return statusForClaim(it)
        }
        return OnceRewardPolicy.status(snapshot.vipLevel, level, claimed = false, config.language)
    }

    fun claimOnce(player: Player, snapshot: VipSnapshot, targetLevel: Int): OperationResult {
        val level = config.levels.firstOrNull { it.level == targetLevel }
            ?: return OperationResult(false, raw("reward.once-level-not-found", "level" to targetLevel))
        val status = onceStatus(snapshot, targetLevel)
        if (!status.available || status.claimed) {
            return OperationResult(false, status.reason)
        }
        val claim = when (val write = repository.beginClaim(player.uniqueId, player.name, ONCE_SEASON_ID, ClaimType.ONCE.dbKey, level.level, ONCE_PERIOD_KEY, level.once.commands)) {
            is ClaimWriteResult.Inserted -> write.claim
            is ClaimWriteResult.Existing -> return OperationResult(false, statusForClaim(write.claim).reason)
        }
        val context = RewardCommandContext(player.name, player.uniqueId, level.level, snapshot.seasonId ?: ONCE_SEASON_ID, claim.id, ONCE_PERIOD_KEY, claim.dispatchId ?: "")
        val dispatch = dispatchCommands(context, claim.id, level.once.commands, ClaimDispatchStatus.PENDING)
        if (!dispatch.success) {
            return OperationResult(false, dispatch.message)
        }
        LmVipPlaceholderExpansion.refresh(player.uniqueId, player.name)
        return OperationResult(true, raw("reward.once-claim-success", "level" to level.level, "level_name" to level.plainName))
    }

    fun retryClaim(playerId: UUID, playerName: String, snapshot: VipSnapshot, type: ClaimType, targetLevel: Int? = null): OperationResult {
        val target = claimTarget(snapshot, type, targetLevel) ?: return OperationResult(false, raw("reward.no-vip-level"))
        val claim = repository.findClaim(playerId, target.seasonId, type.dbKey, target.level.level, target.periodKey)
            ?: return OperationResult(false, raw("reward.claim-not-found"))
        if (!ClaimDispatchPolicy.canRetry(claim.status)) {
            return OperationResult(false, raw("reward.claim-not-retryable"))
        }
        val context = RewardCommandContext(playerName, playerId, target.level.level, target.seasonId, claim.id, target.periodKey, claim.dispatchId ?: "")
        val dispatch = dispatchCommands(context, claim.id, target.commands, ClaimDispatchStatus.FAILED)
        if (!dispatch.success) {
            return OperationResult(false, dispatch.message)
        }
        LmVipPlaceholderExpansion.refresh(playerId, playerName)
        return OperationResult(true, raw("reward.retry-success"))
    }

    fun resetClaim(playerId: UUID, playerName: String, snapshot: VipSnapshot, type: ClaimType, targetLevel: Int? = null): OperationResult {
        val target = claimTarget(snapshot, type, targetLevel) ?: return OperationResult(false, raw("reward.no-vip-level"))
        val claim = repository.findClaim(playerId, target.seasonId, type.dbKey, target.level.level, target.periodKey)
            ?: return OperationResult(false, raw("reward.claim-not-found"))
        if (!ClaimDispatchPolicy.canReset(claim.status)) {
            return OperationResult(false, raw("reward.claim-not-resettable"))
        }
        val commands = repository.listClaimCommands(claim.id)
        if (commands.any { it.status == ClaimCommandStatus.SUCCEEDED }) {
            return OperationResult(false, raw("reward.reset-has-succeeded"))
        }
        val reset = repository.markClaimManualReview(claim.id, raw("reward.reset-manual-review"))
        if (!reset) {
            return OperationResult(false, raw("reward.claim-not-resettable"))
        }
        LmVipPlaceholderExpansion.refresh(playerId, playerName)
        return OperationResult(true, raw("reward.reset-success"))
    }

    private fun claimTarget(snapshot: VipSnapshot, type: ClaimType, targetLevel: Int?): ClaimTarget? {
        return if (type == ClaimType.ONCE) {
            val level = config.levels.firstOrNull { it.level == (targetLevel ?: snapshot.vipLevel) } ?: return null
            ClaimTarget(ONCE_SEASON_ID, ONCE_PERIOD_KEY, level, level.once.commands)
        } else {
            val seasonId = snapshot.seasonId ?: return null
            val level = config.levels.firstOrNull { it.level == (targetLevel ?: snapshot.vipLevel) } ?: return null
            val periodKey = periodKey(type)
            val commands = when (type) {
                ClaimType.DAILY -> level.daily.commands
                ClaimType.WEEKLY -> level.weekly.commands
                ClaimType.MONTHLY -> level.monthly.commands
                ClaimType.ONCE -> level.once.commands
            }
            ClaimTarget(seasonId, periodKey, level, commands)
        }
    }

    private fun statusForClaim(claim: ClaimRecord): ClaimStatus {
        return when (claim.status) {
            ClaimDispatchStatus.CLAIMED -> ClaimStatus(false, true, raw("reward.status.claimed"))
            ClaimDispatchStatus.PENDING -> ClaimStatus(false, false, raw("reward.status.pending"))
            ClaimDispatchStatus.RUNNING -> ClaimStatus(false, false, raw("reward.status.pending"))
            ClaimDispatchStatus.FAILED -> ClaimStatus(false, false, raw("reward.status.failed", "error" to (claim.failureReason ?: "-")))
            ClaimDispatchStatus.MANUAL_REVIEW -> ClaimStatus(false, false, raw("reward.status.failed", "error" to (claim.failureReason ?: "-")))
        }
    }

    private fun dispatchCommands(
        context: RewardCommandContext,
        claimId: Long,
        commands: List<String>,
        expectedStatus: ClaimDispatchStatus,
    ): CommandDispatchOutcome {
        if (commands.isEmpty()) {
            return if (finishClaim(claimId, context, expectedStatus, ClaimDispatchStatus.CLAIMED, null)) {
                CommandDispatchOutcome(true, raw("reward.claim-success"))
            } else {
                CommandDispatchOutcome(false, raw("reward.dispatch-claimed-by-other"))
            }
        }
        val workerId = "lmvip-${context.playerId}-${claimId}-${System.nanoTime()}"
        val leaseUntil = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.rewardCommandTimeoutSeconds.coerceAtLeast(1).toLong())
        if (!repository.tryMarkClaimRunning(claimId, expectedStatus, workerId, leaseUntil)) {
            return CommandDispatchOutcome(false, raw("reward.dispatch-claimed-by-other"))
        }
        val dispatch = runCatching { runRewardCommands(context, claimId, commands) }.getOrElse {
            warn("Reward command dispatch failed for ${context.playerName}: ${it.message}")
            CommandDispatchOutcome(false, raw("reward.dispatch-failed"))
        }
        val completeStatus = if (dispatch.success) ClaimDispatchStatus.CLAIMED else ClaimDispatchStatus.FAILED
        val completeReason = if (dispatch.success) null else dispatch.message
        if (!repository.completeRunningClaim(claimId, workerId, completeStatus, completeReason)) {
            warn("Reward claim completion lost CAS for ${context.playerName}: claim $claimId worker $workerId")
            return CommandDispatchOutcome(false, raw("reward.dispatch-claimed-by-other"))
        }
        return dispatch
    }

    private fun runRewardCommands(context: RewardCommandContext, claimId: Long, commands: List<String>): CommandDispatchOutcome {
        if (!isClaimRunning(claimId)) {
            warn("Skipped reward command dispatch for ${context.playerName}: claim $claimId is no longer pending")
            return CommandDispatchOutcome(false, raw("reward.dispatch-timeout"))
        }
        val records = repository.listClaimCommands(claimId)
        val validation = validateClaimCommands(records, commands)
        if (!validation.success) return validation
        for (record in records.sortedBy { it.commandIndex }) {
            if (record.status == ClaimCommandStatus.SUCCEEDED) continue
            if (!isClaimRunning(claimId)) {
                warn("Stopped reward command dispatch for ${context.playerName}: claim $claimId is no longer pending")
                return CommandDispatchOutcome(false, raw("reward.dispatch-timeout"))
            }
            val command = commands.getOrNull(record.commandIndex)
                ?: return CommandDispatchOutcome(false, raw("reward.retry-command-changed"))
            if (!repository.tryMarkClaimCommandRunning(claimId, record.commandIndex)) {
                return CommandDispatchOutcome(false, raw("reward.dispatch-claimed-by-other"))
            }
            val executed = executeCommandOnServerThread(context, command)
            if (executed) {
                repository.updateClaimCommandStatus(claimId, record.commandIndex, ClaimCommandStatus.SUCCEEDED, command)
            } else {
                warn("Reward command returned false for ${context.playerName}: ${context.render(command)}")
                repository.updateClaimCommandStatus(claimId, record.commandIndex, ClaimCommandStatus.FAILED, command, raw("reward.dispatch-failed"))
                return CommandDispatchOutcome(false, raw("reward.dispatch-failed-rollback"))
            }
        }
        return CommandDispatchOutcome(true, raw("reward.claim-success"))
    }

    private fun executeCommandOnServerThread(context: RewardCommandContext, command: String): Boolean {
        val task = { commandExecutor.execute(context, command) }
        serverThreadDispatcher?.let { dispatcher ->
            return dispatcher(config.rewardCommandTimeoutSeconds) {
                CommandDispatchOutcome(task(), raw("reward.claim-success"))
            }.success
        }
        if (Bukkit.isPrimaryThread()) {
            return task()
        }
        val future = CompletableFuture<Boolean>()
        return runCatching {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), Runnable {
                if (!future.isCancelled) {
                    future.complete(task())
                }
            })
            future.get(config.rewardCommandTimeoutSeconds, TimeUnit.SECONDS)
        }.getOrElse {
            warn("Reward command dispatch failed or timed out for ${context.playerName}: ${it.message}")
            future.cancel(false)
            if (it is TimeoutException) false else throw it
        }
    }

    private fun finishClaim(
        claimId: Long,
        context: RewardCommandContext,
        expectedStatus: ClaimDispatchStatus,
        completeStatus: ClaimDispatchStatus,
        reason: String?,
    ): Boolean {
        val workerId = "lmvip-${context.playerId}-${claimId}-${System.nanoTime()}"
        val leaseUntil = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.rewardCommandTimeoutSeconds.coerceAtLeast(1).toLong())
        return repository.tryMarkClaimRunning(claimId, expectedStatus, workerId, leaseUntil) &&
            repository.completeRunningClaim(claimId, workerId, completeStatus, reason)
    }

    private fun isClaimRunning(claimId: Long): Boolean {
        return repository.claimStatus(claimId) == ClaimDispatchStatus.RUNNING
    }

    private fun validateClaimCommands(records: List<ClaimCommandRecord>, commands: List<String>): CommandDispatchOutcome {
        if (records.isEmpty()) {
            return CommandDispatchOutcome(false, raw("reward.retry-missing-command-state"))
        }
        if (records.size != commands.size) {
            return CommandDispatchOutcome(false, raw("reward.retry-command-changed"))
        }
        val byIndex = records.associateBy { it.commandIndex }
        for (index in commands.indices) {
            val record = byIndex[index] ?: return CommandDispatchOutcome(false, raw("reward.retry-command-changed"))
            val currentHash = CommandHash.sha256(commands[index])
            if (record.status == ClaimCommandStatus.SUCCEEDED && !record.commandHash.equals(currentHash, true)) {
                return CommandDispatchOutcome(false, raw("reward.retry-command-changed"))
            }
        }
        return CommandDispatchOutcome(true, raw("reward.claim-success"))
    }

    private fun warn(message: String) {
        runCatching { Bukkit.getLogger().warning("[LmVIP] $message") }
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

    private data class ClaimTarget(
        val seasonId: String,
        val periodKey: String,
        val level: VipLevel,
        val commands: List<String>,
    )
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
