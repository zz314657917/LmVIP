package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.cache.CacheStats
import cc.mcstory.lmvip.cache.RefreshingValueCache
import cc.mcstory.lmvip.model.ClaimStatus
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.service.RewardService
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.platform.compat.PlaceholderExpansion
import taboolib.platform.BukkitPlugin
import java.util.UUID

object LmVipPlaceholderExpansion : PlaceholderExpansion {
    override val identifier: String = "lmvip"
    private val cache = RefreshingValueCache<UUID, CachedPlaceholder>(
        ttlMillis = { LmVipServices.config?.snapshotTtlMillis ?: 30_000L }
    )

    fun refresh(player: OfflinePlayer) {
        refresh(player.uniqueId, player.name ?: player.uniqueId.toString())
    }

    fun refresh(playerId: UUID, playerName: String) {
        if (!LmVipServices.ready) {
            invalidate(playerId)
            return
        }
        if (Bukkit.isPrimaryThread()) {
            refreshAsync(playerId, playerName)
        } else {
            loadEntry(playerId, playerName) ?: invalidate(playerId)
        }
    }

    fun invalidate(playerId: UUID) {
        cache.invalidate(playerId)
    }

    fun clear() {
        cache.clear()
    }

    fun stats(): CacheStats {
        return cache.stats()
    }

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        if (player == null || !LmVipServices.ready) return ""
        val playerId = player.uniqueId
        val playerName = player.name ?: playerId.toString()
        val read = cache.read(playerId)
        val entry = if (Bukkit.isPrimaryThread()) {
            if (read.refreshStarted) {
                scheduleRefresh(playerId, playerName)
            }
            read.value
        } else {
            if (read.refreshStarted) {
                loadEntry(playerId, playerName) ?: read.value
            } else {
                read.value
            }
        } ?: return ""
        return valueFor(entry, args)
    }

    private fun valueFor(entry: CachedPlaceholder, args: String): String {
        val snapshot = entry.snapshot
        val normalized = args.lowercase()
        if (normalized.startsWith("once_claimed_")) {
            return onceStatusValue(entry, normalized.removePrefix("once_claimed_")) { it.claimed }
        }
        if (normalized.startsWith("once_reward_available_")) {
            return onceStatusValue(entry, normalized.removePrefix("once_reward_available_")) { it.available }
        }
        return when (normalized) {
            "level" -> snapshot.vipLevel.toString()
            "level_name" -> snapshot.vipLevelName
            "total_points" -> snapshot.totalPoints.toString()
            "season_id" -> snapshot.seasonId ?: ""
            "season_name" -> snapshot.seasonName ?: ""
            "season_points" -> snapshot.seasonPoints.toString()
            "monthly_points" -> snapshot.monthlyPoints.toString()
            "daily_points" -> snapshot.dailyPoints.toString()
            "next_level_need" -> snapshot.nextLevelNeed.toString()
            "daily_reward_available" -> (entry.statuses[ClaimType.DAILY]?.available ?: false).toString()
            "weekly_reward_available" -> (entry.statuses[ClaimType.WEEKLY]?.available ?: false).toString()
            "monthly_reward_available" -> (entry.statuses[ClaimType.MONTHLY]?.available ?: false).toString()
            "daily_claimed" -> (entry.statuses[ClaimType.DAILY]?.claimed ?: false).toString()
            "weekly_claimed" -> (entry.statuses[ClaimType.WEEKLY]?.claimed ?: false).toString()
            "monthly_claimed" -> (entry.statuses[ClaimType.MONTHLY]?.claimed ?: false).toString()
            else -> ""
        }
    }

    private fun onceStatusValue(entry: CachedPlaceholder, levelText: String, extractor: (ClaimStatus) -> Boolean): String {
        val level = levelText.toIntOrNull() ?: return ""
        return extractor(entry.onceStatuses[level] ?: return "false").toString()
    }

    private fun refreshAsync(playerId: UUID, playerName: String) {
        if (cache.beginRefresh(playerId)) {
            scheduleRefresh(playerId, playerName)
        }
    }

    private fun scheduleRefresh(playerId: UUID, playerName: String) {
        Bukkit.getScheduler().runTaskAsynchronously(BukkitPlugin.getInstance(), Runnable {
            loadEntry(playerId, playerName)
        })
    }

    private fun loadEntry(playerId: UUID, playerName: String): CachedPlaceholder? {
        val service = LmVipServices.vipService ?: run {
            cache.finishRefreshWithoutValue(playerId)
            return null
        }
        return runCatching {
            val snapshot = service.snapshot(playerId, playerName, force = true)
            val statuses = RewardService.PERIODIC_TYPES.associateWith { service.rewards.status(snapshot, it) }
            val onceStatuses = LmVipServices.config?.levels.orEmpty()
                .associate { it.level to service.rewards.onceStatus(snapshot, it.level) }
            val entry = CachedPlaceholder(snapshot, statuses, onceStatuses)
            cache.refreshSucceeded(playerId, entry)
            entry
        }.getOrElse {
            cache.refreshFailed(playerId, it)
            Bukkit.getLogger().warning("[LmVIP] Placeholder cache refresh failed for $playerId: ${it.message}")
            null
        }
    }

    private data class CachedPlaceholder(
        val snapshot: VipSnapshot,
        val statuses: Map<ClaimType, ClaimStatus>,
        val onceStatuses: Map<Int, ClaimStatus>,
    )
}
