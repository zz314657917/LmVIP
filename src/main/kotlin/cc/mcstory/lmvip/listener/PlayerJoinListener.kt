package cc.mcstory.lmvip.listener

import cc.mcstory.lmvip.LmVipServices
import cc.mcstory.lmvip.util.BukkitTasks
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent

object PlayerJoinListener {
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        val service = LmVipServices.vipService ?: return
        BukkitTasks.async({ service.refreshAndSync(event.player) }) { }
    }
}
