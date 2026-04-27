package cc.mcstory.lmvip.model

import java.util.UUID

data class RewardRule(
    val threshold: Long,
    val commands: List<String>,
)

data class VipLevel(
    val level: Int,
    val name: String,
    val plainName: String,
    val totalPoints: Long,
    val group: String,
    val benefits: List<String>,
    val daily: RewardRule,
    val weekly: RewardRule,
    val monthly: RewardRule,
)

data class SeasonRecord(
    val seasonId: String,
    val displayName: String,
    val active: Boolean,
    val startedAt: Long,
    val endedAt: Long?,
)

data class VipSnapshot(
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

enum class PointDimension {
    TOTAL,
    SEASON,
    MONTHLY,
    DAILY;

    val dbKey: String
        get() = name.lowercase()

    companion object {
        fun parse(value: String): PointDimension? {
            return values().firstOrNull { it.name.equals(value, true) || it.dbKey.equals(value, true) }
        }
    }
}

enum class ClaimType {
    DAILY,
    WEEKLY,
    MONTHLY;

    val dbKey: String
        get() = name.lowercase()

    companion object {
        fun parse(value: String): ClaimType? {
            return values().firstOrNull { it.name.equals(value, true) || it.dbKey.equals(value, true) }
        }
    }
}

data class ClaimStatus(
    val available: Boolean,
    val claimed: Boolean,
    val reason: String,
)

data class OperationResult(
    val success: Boolean,
    val message: String,
    val transactionId: Long? = null,
)
