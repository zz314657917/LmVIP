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

enum class ClaimDispatchStatus {
    PENDING,
    RUNNING,
    CLAIMED,
    FAILED,
    MANUAL_REVIEW;

    val dbKey: String
        get() = name.lowercase()

    companion object {
        fun parse(value: String?): ClaimDispatchStatus {
            if (value.isNullOrBlank()) return CLAIMED
            return values().firstOrNull { it.name.equals(value, true) || it.dbKey.equals(value, true) } ?: CLAIMED
        }
    }
}

enum class ClaimCommandStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    val dbKey: String
        get() = name.lowercase()

    companion object {
        fun parse(value: String?): ClaimCommandStatus {
            if (value.isNullOrBlank()) return PENDING
            return values().firstOrNull { it.name.equals(value, true) || it.dbKey.equals(value, true) } ?: PENDING
        }
    }
}

data class ClaimRecord(
    val id: Long,
    val playerId: UUID,
    val playerName: String,
    val seasonId: String,
    val claimType: String,
    val level: Int,
    val periodKey: String,
    val status: ClaimDispatchStatus,
    val dispatchId: String?,
    val failureReason: String?,
    val workerId: String?,
    val leaseUntil: Long?,
    val version: Long,
)

data class ClaimCommandRecord(
    val id: Long,
    val claimId: Long,
    val commandIndex: Int,
    val commandHash: String,
    val commandTemplate: String,
    val status: ClaimCommandStatus,
    val failureReason: String?,
)

sealed class ClaimWriteResult {
    data class Inserted(val claim: ClaimRecord) : ClaimWriteResult()
    data class Existing(val claim: ClaimRecord) : ClaimWriteResult()
}

data class OperationResult(
    val success: Boolean,
    val message: String,
    val transactionId: Long? = null,
)

sealed class TransactionWriteResult {
    data class Inserted(val transactionId: Long) : TransactionWriteResult()
    data class DuplicateOrder(val source: String, val orderId: String) : TransactionWriteResult()
    data class DuplicateMismatch(val source: String, val orderId: String) : TransactionWriteResult()
    object NoChange : TransactionWriteResult()

    fun transactionIdOrNull(): Long? {
        return when (this) {
            is Inserted -> transactionId
            is DuplicateOrder,
            is DuplicateMismatch,
            NoChange -> null
        }
    }

    fun adminMessage(prefix: String, noChangeText: String = "无变更"): String {
        return when (this) {
            is Inserted -> "$prefix: $transactionId"
            is DuplicateOrder -> "$prefix: 重复订单 $source/$orderId"
            is DuplicateMismatch -> "$prefix: 重复订单内容不一致 $source/$orderId"
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
