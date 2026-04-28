package cc.mcstory.lmvip.api

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.service.VipService
import org.bukkit.Bukkit
import taboolib.platform.BukkitPlugin
import java.util.UUID
import java.util.concurrent.CompletableFuture

class BukkitLmVipApi(
    private val service: VipService,
) : LmVipApi {
    override fun isReady(): Boolean {
        return LmVipServices.ready
    }

    override fun getCachedSnapshot(playerId: UUID): VipApiSnapshot? {
        return service.cachedSnapshot(playerId)?.let(VipApiSnapshots::from)
    }

    override fun getCachedVipLevel(playerId: UUID): Int? {
        return getCachedSnapshot(playerId)?.vipLevel
    }

    override fun getCachedTotalPoints(playerId: UUID): Long? {
        return getCachedSnapshot(playerId)?.totalPoints
    }

    override fun getSnapshotAsync(playerId: UUID, playerName: String): CompletableFuture<VipApiSnapshot> {
        val cached = service.cachedSnapshot(playerId)
        if (cached != null) {
            return CompletableFuture.completedFuture(VipApiSnapshots.from(cached))
        }
        return loadAsync(playerId, playerName, force = false)
    }

    override fun refreshSnapshotAsync(playerId: UUID, playerName: String): CompletableFuture<VipApiSnapshot> {
        return loadAsync(playerId, playerName, force = true)
    }

    override fun getVipLevelAsync(playerId: UUID, playerName: String): CompletableFuture<Int> {
        return getSnapshotAsync(playerId, playerName).thenApply { it.vipLevel }
    }

    override fun getTotalPointsAsync(playerId: UUID, playerName: String): CompletableFuture<Long> {
        return getSnapshotAsync(playerId, playerName).thenApply { it.totalPoints }
    }

    private fun loadAsync(playerId: UUID, playerName: String, force: Boolean): CompletableFuture<VipApiSnapshot> {
        val future = CompletableFuture<VipApiSnapshot>()
        if (!LmVipServices.ready) {
            future.completeExceptionally(IllegalStateException("LmVIP is not ready."))
            return future
        }
        val normalizedName = playerName.takeIf { it.isNotBlank() } ?: playerId.toString()
        Bukkit.getScheduler().runTaskAsynchronously(BukkitPlugin.getInstance(), Runnable {
            try {
                val snapshot = service.snapshot(playerId, normalizedName, force = force)
                future.complete(VipApiSnapshots.from(snapshot))
            } catch (exception: Throwable) {
                future.completeExceptionally(exception)
            }
        })
        return future
    }
}
