package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.config.VipRuntimeConfig
import cc.mcstory.lmvip.integration.LuckPermsGroupSync
import cc.mcstory.lmvip.model.PointDimension
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.storage.JdbcVipRepository
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
    }

    fun snapshot(player: OfflinePlayer, seasonId: String? = null, force: Boolean = false): VipSnapshot {
        val name = player.name ?: player.uniqueId.toString()
        val cached = cache[player.uniqueId]
        val now = System.currentTimeMillis()
        if (!force && seasonId == null && cached != null && now - cached.createdAt <= config.snapshotTtlMillis) {
            return cached.snapshot
        }
        val snapshot = repository.snapshot(player.uniqueId, name, seasonId, config.levels)
        if (seasonId == null) {
            cache[player.uniqueId] = CachedSnapshot(snapshot, now)
        }
        return snapshot
    }

    fun addRecharge(player: OfflinePlayer, amount: Long, source: String, orderId: String, operator: String, reason: String): Long? {
        require(amount > 0) { "amount must be positive" }
        val name = player.name ?: player.uniqueId.toString()
        val id = repository.addRecharge(player.uniqueId, name, amount, source, orderId, operator, reason)
        refreshAndSync(player)
        return id
    }

    fun adjustSeason(player: OfflinePlayer, seasonId: String, set: Long?, delta: Long?, operator: String, reason: String): Long? {
        val name = player.name ?: player.uniqueId.toString()
        val id = repository.adjustSeason(player.uniqueId, name, seasonId, set, delta, operator, reason)
        cache.remove(player.uniqueId)
        return id
    }

    fun adjustDimension(player: OfflinePlayer, dimension: PointDimension, set: Long?, delta: Long?, operator: String, reason: String): Long? {
        val name = player.name ?: player.uniqueId.toString()
        val id = repository.adjustDimension(player.uniqueId, name, dimension, set, delta, operator, reason)
        refreshAndSync(player)
        return id
    }

    fun rollback(transactionId: Long, operator: String, reason: String): Long? {
        cache.clear()
        return repository.rollback(transactionId, operator, reason)
    }

    fun refreshAndSync(player: OfflinePlayer): VipSnapshot {
        val snapshot = snapshot(player, force = true)
        groupSync.sync(player, snapshot.vipLevel)
        return snapshot
    }

    private data class CachedSnapshot(val snapshot: VipSnapshot, val createdAt: Long)
}
