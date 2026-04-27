package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.model.ClaimType
import org.bukkit.OfflinePlayer
import taboolib.platform.compat.PlaceholderExpansion

object LmVipPlaceholderExpansion : PlaceholderExpansion {
    override val identifier: String = "lmvip"

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        if (player == null || !LmVipServices.ready) return ""
        val service = LmVipServices.vipService ?: return ""
        val snapshot = runCatching { service.snapshot(player) }.getOrNull() ?: return ""
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
            "daily_reward_available" -> service.rewards.status(snapshot, ClaimType.DAILY).available.toString()
            "weekly_reward_available" -> service.rewards.status(snapshot, ClaimType.WEEKLY).available.toString()
            "monthly_reward_available" -> service.rewards.status(snapshot, ClaimType.MONTHLY).available.toString()
            "daily_claimed" -> service.rewards.status(snapshot, ClaimType.DAILY).claimed.toString()
            "weekly_claimed" -> service.rewards.status(snapshot, ClaimType.WEEKLY).claimed.toString()
            "monthly_claimed" -> service.rewards.status(snapshot, ClaimType.MONTHLY).claimed.toString()
            else -> ""
        }
    }
}
