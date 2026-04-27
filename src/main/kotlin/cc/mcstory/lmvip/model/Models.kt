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
    val once: RewardRule,
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
    MONTHLY,
    ONCE;

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

sealed class TransactionWriteResult {
    data class Inserted(val transactionId: Long) : TransactionWriteResult()
    data class DuplicateOrder(val source: String, val orderId: String) : TransactionWriteResult()
    object NoChange : TransactionWriteResult()

    fun transactionIdOrNull(): Long? {
        return when (this) {
            is Inserted -> transactionId
            is DuplicateOrder,
            NoChange -> null
        }
    }

    fun adminMessage(prefix: String, noChangeText: String = "无变更"): String {
        return when (this) {
            is Inserted -> "$prefix: $transactionId"
            is DuplicateOrder -> "$prefix: 重复订单 $source/$orderId"
            NoChange -> "$prefix: $noChangeText"
        }
    }
}

data class RollbackTransactionResult(
    val playerId: UUID,
    val playerName: String,
    val dimension: String,
    val writeResult: TransactionWriteResult,
) {
    val requiresVipSync: Boolean
        get() = writeResult is TransactionWriteResult.Inserted &&
            (dimension.equals("recharge", true) || dimension.equals(PointDimension.TOTAL.dbKey, true))
}
