package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.model.ClaimStatus
import cc.mcstory.lmvip.model.ClaimType
import cc.mcstory.lmvip.model.VipSnapshot
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.platform.compat.PlaceholderExpansion
import taboolib.platform.BukkitPlugin
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object LmVipPlaceholderExpansion : PlaceholderExpansion {
    override val identifier: String = "lmvip"
    private val cache = ConcurrentHashMap<UUID, CachedPlaceholder>()
    private val refreshing = Collections.newSetFromMap(ConcurrentHashMap<UUID, Boolean>())

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        if (player == null || !LmVipServices.ready) return ""
        val service = LmVipServices.vipService ?: return ""
        val playerId = player.uniqueId
        val playerName = player.name ?: playerId.toString()
        val cached = cache[playerId]
        val entry = if (Bukkit.isPrimaryThread()) {
            if (cached == null || cached.isExpired()) {
                refreshAsync(playerId, playerName)
            }
            cached
        } else {
            loadEntry(playerId, playerName) ?: cached
        } ?: return ""
        return valueFor(entry, args)
    }

    private fun valueFor(entry: CachedPlaceholder, args: String): String {
        val snapshot = entry.snapshot
        return when (args.lowercase()) {
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

    private fun refreshAsync(playerId: UUID, playerName: String) {
        if (!refreshing.add(playerId)) return
        Bukkit.getScheduler().runTaskAsynchronously(BukkitPlugin.getInstance(), Runnable {
            try {
                loadEntry(playerId, playerName)
            } finally {
                refreshing.remove(playerId)
            }
        })
    }

    private fun loadEntry(playerId: UUID, playerName: String): CachedPlaceholder? {
        val service = LmVipServices.vipService ?: return null
        return runCatching {
            val snapshot = service.snapshot(playerId, playerName)
            val statuses = ClaimType.values().associateWith { service.rewards.status(snapshot, it) }
            val entry = CachedPlaceholder(snapshot, statuses, System.currentTimeMillis())
            cache[playerId] = entry
            entry
        }.getOrElse {
            Bukkit.getLogger().warning("[LmVIP] Placeholder cache refresh failed for $playerId: ${it.message}")
            null
        }
    }

    private data class CachedPlaceholder(
        val snapshot: VipSnapshot,
        val statuses: Map<ClaimType, ClaimStatus>,
        val createdAt: Long,
    ) {
        fun isExpired(): Boolean {
            val ttl = LmVipServices.config?.snapshotTtlMillis ?: 30_000L
            return System.currentTimeMillis() - createdAt > ttl
        }
    }
}
