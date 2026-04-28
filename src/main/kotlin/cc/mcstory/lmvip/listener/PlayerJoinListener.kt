package cc.mcstory.lmvip.listener

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.util.BukkitTasks
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.BukkitPlugin

object PlayerJoinListener {
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        val service = LmVipServices.vipService ?: return
        BukkitTasks.async({ service.refreshAndSync(event.player) }) { }
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        val retainMillis = LmVipServices.config?.cacheRetainAfterQuitMillis ?: 300_000L
        val delayTicks = (retainMillis / 50L).coerceAtLeast(1L)
        Bukkit.getScheduler().runTaskLater(BukkitPlugin.getInstance(), Runnable {
            if (Bukkit.getPlayer(playerId) == null) {
                LmVipServices.vipService?.invalidateCache(playerId)
            }
        }, delayTicks)
    }
}
