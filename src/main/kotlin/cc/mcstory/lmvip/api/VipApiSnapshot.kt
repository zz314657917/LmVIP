package cc.mcstory.lmvip.api

import java.util.UUID

data class VipApiSnapshot(
    val playerId: UUID,
    val playerName: String,
    val seasonId: String?,
    val seasonName: String?,
    val totalPoints: Long,
    val seasonPoints: Long,
    val monthlyPoints: Long,
    val dailyPoints: Long,
    val vipLevel: Int,
    val vipLevelName: String,
    val nextLevelNeed: Long,
)
