package cc.mcstory.lmvip.util

import org.bukkit.Bukkit
import taboolib.platform.BukkitPlugin

object BukkitTasks {
    fun <T> async(block: () -> T, callback: (Result<T>) -> Unit) {
        val plugin = BukkitPlugin.getInstance()
        runAsync(
            block = block,
            callback = callback,
            scheduleAsync = { Bukkit.getScheduler().runTaskAsynchronously(plugin, it) },
            scheduleSync = { Bukkit.getScheduler().runTask(plugin, it) },
            logFailure = { message, error ->
                Bukkit.getLogger().warning("[LmVIP] $message: ${error.message ?: error.javaClass.simpleName}")
            }
        )
    }

    internal fun <T> runAsync(
        block: () -> T,
        callback: (Result<T>) -> Unit,
        scheduleAsync: (Runnable) -> Unit,
        scheduleSync: (Runnable) -> Unit,
        logFailure: (String, Throwable) -> Unit,
    ) {
        runCatching {
            scheduleAsync(Runnable {
                val result = runCatching(block)
                val callbackTask = Runnable {
                    invokeCallback(callback, result, logFailure)
                }
                runCatching {
                    scheduleSync(callbackTask)
                }.onFailure {
                    logFailure("BukkitTasks callback scheduling failed", it)
                    callbackTask.run()
                }
            })
        }.onFailure {
            logFailure("BukkitTasks async scheduling failed", it)
            invokeCallback(callback, Result.failure(it), logFailure)
        }
    }

    private fun <T> invokeCallback(
        callback: (Result<T>) -> Unit,
        result: Result<T>,
        logFailure: (String, Throwable) -> Unit,
    ) {
        runCatching {
            callback(result)
        }.onFailure {
            logFailure("BukkitTasks callback failed", it)
        }
    }
}
