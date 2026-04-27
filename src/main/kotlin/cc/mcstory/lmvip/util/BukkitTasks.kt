package cc.mcstory.lmvip.util

import org.bukkit.Bukkit
import taboolib.platform.BukkitPlugin

object BukkitTasks {
    fun <T> async(block: () -> T, callback: (Result<T>) -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(BukkitPlugin.getInstance(), Runnable {
            val result = runCatching(block)
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), Runnable {
                callback(result)
            })
        })
    }
}
