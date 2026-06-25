package cc.mcstory.lmvip.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BukkitTasksTest {

    @Test
    fun `sync callback scheduler failure invokes callback inline`() {
        val callbacks = mutableListOf<Result<String>>()
        val logs = mutableListOf<String>()

        BukkitTasks.runAsync(
            block = { "ok" },
            callback = { callbacks += it },
            scheduleAsync = { it.run() },
            scheduleSync = { throw IllegalStateException("plugin disabled") },
            logFailure = { message, _ -> logs += message }
        )

        assertEquals("ok", callbacks.single().getOrThrow())
        assertTrue(logs.single().contains("callback"))
    }

    @Test
    fun `async scheduler failure is returned to callback`() {
        val callbacks = mutableListOf<Result<String>>()

        BukkitTasks.runAsync(
            block = { "unused" },
            callback = { callbacks += it },
            scheduleAsync = { throw IllegalStateException("async rejected") },
            scheduleSync = { it.run() },
            logFailure = { _, _ -> }
        )

        assertTrue(callbacks.single().isFailure)
        assertEquals("async rejected", callbacks.single().exceptionOrNull()?.message)
    }
}
