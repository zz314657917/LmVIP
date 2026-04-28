package cc.mcstory.lmvip.api

import java.util.UUID
import java.util.concurrent.CompletableFuture

interface LmVipApi {
    fun isReady(): Boolean

    fun getCachedSnapshot(playerId: UUID): VipApiSnapshot?

    fun getCachedVipLevel(playerId: UUID): Int?

    fun getCachedTotalPoints(playerId: UUID): Long?

    fun getSnapshotAsync(playerId: UUID, playerName: String): CompletableFuture<VipApiSnapshot>

    fun refreshSnapshotAsync(playerId: UUID, playerName: String): CompletableFuture<VipApiSnapshot>

    fun getVipLevelAsync(playerId: UUID, playerName: String): CompletableFuture<Int>

    fun getTotalPointsAsync(playerId: UUID, playerName: String): CompletableFuture<Long>
}
