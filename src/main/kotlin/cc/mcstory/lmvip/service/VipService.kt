package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.cache.SnapshotCacheStore
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

class VipService(
    private var config: VipRuntimeConfig,
    private val repository: JdbcVipRepository,
    private val groupSync: LuckPermsGroupSync,
    val rewards: RewardService,
    private val primaryThreadCheck: () -> Boolean = { Bukkit.isPrimaryThread() },
) {
    private val cache = SnapshotCacheStore<VipSnapshot>({ config.snapshotTtlMillis })

    fun updateConfig(config: VipRuntimeConfig) {
        this.config = config
        repository.updatePeriods(config.periods)
        groupSync.updateLevels(config.levels, config.legacyGroups)
        rewards.updateConfig(config)
        cache.clear()
        LmVipPlaceholderExpansion.clear()
    }

    fun snapshot(player: OfflinePlayer, seasonId: String? = null, force: Boolean = false): VipSnapshot {
        val name = player.name ?: player.uniqueId.toString()
        return snapshot(player.uniqueId, name, seasonId, force)
    }

    fun snapshot(playerId: UUID, playerName: String, seasonId: String? = null, force: Boolean = false): VipSnapshot {
        val cached = cache.getFresh(playerId)
        if (!force && seasonId == null && cached != null) {
            return cached
        }
        val token = if (seasonId == null) cache.beginLoad(playerId, force) else null
        val snapshot = repository.snapshot(playerId, playerName, seasonId, config.levels)
        if (seasonId == null) {
            cache.putIfCurrent(requireNotNull(token), snapshot)
        }
        return snapshot
    }

    fun cachedSnapshot(playerId: UUID): VipSnapshot? {
        return cache.getFresh(playerId)
    }

    fun invalidateCache(playerId: UUID) {
        cache.invalidate(playerId)
        LmVipPlaceholderExpansion.invalidate(playerId)
    }

    fun clearCache() {
        cache.clear()
        LmVipPlaceholderExpansion.clear()
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
        cache.invalidate(player.uniqueId)
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
            cache.invalidate(player.uniqueId)
        }
        return result
    }

    fun rollback(transactionId: Long, operator: String, reason: String): RollbackTransactionResult? {
        val result = repository.rollback(transactionId, operator, reason) ?: return null
        cache.invalidate(result.playerId)
        if (result.requiresVipSync) {
            refreshAndSync(Bukkit.getOfflinePlayer(result.playerId), result.playerName)
        } else {
            LmVipPlaceholderExpansion.refresh(result.playerId, result.playerName)
        }
        return result
    }

    fun refreshAndSync(player: OfflinePlayer, playerName: String? = null): VipSnapshot {
        check(!primaryThreadCheck()) {
            "VipService.refreshAndSync must be called from an async thread."
        }
        val name = playerName ?: player.name ?: player.uniqueId.toString()
        val snapshot = snapshot(player.uniqueId, name, force = true)
        groupSync.sync(player, snapshot.vipLevel)
        LmVipPlaceholderExpansion.refresh(snapshot.playerId, snapshot.playerName)
        return snapshot
    }
}
