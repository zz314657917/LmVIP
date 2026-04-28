package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.integration.LmVipPlaceholderExpansion
import cc.mcstory.lmvip.integration.LuckPermsGroupSync
import cc.mcstory.lmvip.model.PointDimension
import cc.mcstory.lmvip.model.RollbackTransactionResult
import cc.mcstory.lmvip.model.TransactionWriteResult
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.storage.JdbcVipRepository
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VipService(
    private var config: VipRuntimeConfig,
    private val repository: JdbcVipRepository,
    private val groupSync: LuckPermsGroupSync,
    val rewards: RewardService,
) {
    private val cache = ConcurrentHashMap<UUID, CachedSnapshot>()

    fun updateConfig(config: VipRuntimeConfig) {
        this.config = config
        repository.updatePeriods(config.periods)
        groupSync.updateLevels(config.levels)
        rewards.updateConfig(config)
        cache.clear()
        LmVipPlaceholderExpansion.clear()
    }

    fun snapshot(player: OfflinePlayer, seasonId: String? = null, force: Boolean = false): VipSnapshot {
        val name = player.name ?: player.uniqueId.toString()
        return snapshot(player.uniqueId, name, seasonId, force)
    }

    fun snapshot(playerId: UUID, playerName: String, seasonId: String? = null, force: Boolean = false): VipSnapshot {
        val cached = cache[playerId]
        val now = System.currentTimeMillis()
        if (!force && seasonId == null && cached != null && now - cached.createdAt <= config.snapshotTtlMillis) {
            return cached.snapshot
        }
        val snapshot = repository.snapshot(playerId, playerName, seasonId, config.levels)
        if (seasonId == null) {
            cache[playerId] = CachedSnapshot(snapshot, now)
        }
        return snapshot
    }

    fun cachedSnapshot(playerId: UUID): VipSnapshot? {
        val cached = cache[playerId] ?: return null
        val now = System.currentTimeMillis()
        if (now - cached.createdAt > config.snapshotTtlMillis) {
            cache.remove(playerId, cached)
            return null
        }
        return cached.snapshot
    }

    fun addRecharge(player: OfflinePlayer, amount: Long, source: String, orderId: String, operator: String, reason: String): TransactionWriteResult {
        require(amount > 0) { "amount must be positive" }
        val name = player.name ?: player.uniqueId.toString()
        val result = repository.addRecharge(player.uniqueId, name, amount, source, orderId, operator, reason)
        if (result is TransactionWriteResult.Inserted) {
            refreshAndSync(player)
        }
        return result
    }

    fun adjustSeason(player: OfflinePlayer, seasonId: String, set: Long?, delta: Long?, operator: String, reason: String): TransactionWriteResult {
        val name = player.name ?: player.uniqueId.toString()
        val result = repository.adjustSeason(player.uniqueId, name, seasonId, set, delta, operator, reason)
        cache.remove(player.uniqueId)
        if (result is TransactionWriteResult.Inserted) {
            LmVipPlaceholderExpansion.refresh(player.uniqueId, name)
        } else {
            LmVipPlaceholderExpansion.invalidate(player.uniqueId)
        }
        return result
    }

    fun adjustDimension(player: OfflinePlayer, dimension: PointDimension, set: Long?, delta: Long?, operator: String, reason: String): TransactionWriteResult {
        val name = player.name ?: player.uniqueId.toString()
        val result = repository.adjustDimension(player.uniqueId, name, dimension, set, delta, operator, reason)
        if (result is TransactionWriteResult.Inserted) {
            refreshAndSync(player)
        } else {
            cache.remove(player.uniqueId)
        }
        return result
    }

    fun rollback(transactionId: Long, operator: String, reason: String): RollbackTransactionResult? {
        val result = repository.rollback(transactionId, operator, reason) ?: return null
        cache.remove(result.playerId)
        if (result.requiresVipSync) {
            refreshAndSync(Bukkit.getOfflinePlayer(result.playerId), result.playerName)
        } else {
            LmVipPlaceholderExpansion.refresh(result.playerId, result.playerName)
        }
        return result
    }

    fun refreshAndSync(player: OfflinePlayer, playerName: String? = null): VipSnapshot {
        val name = playerName ?: player.name ?: player.uniqueId.toString()
        val snapshot = snapshot(player.uniqueId, name, force = true)
        groupSync.sync(player, snapshot.vipLevel)
        LmVipPlaceholderExpansion.refresh(snapshot.playerId, snapshot.playerName)
        return snapshot
    }

    private data class CachedSnapshot(val snapshot: VipSnapshot, val createdAt: Long)
}
