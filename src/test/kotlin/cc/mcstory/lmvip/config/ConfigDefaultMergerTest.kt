package cc.mcstory.lmvip.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigDefaultMergerTest {

    @Test
    fun `missing leaf keys are detected without overwriting existing keys`() {
        val defaults = listOf(
            ConfigDefaultMerger.PathInfo("database-profile", false),
            ConfigDefaultMerger.PathInfo("cache", true),
            ConfigDefaultMerger.PathInfo("cache.snapshot-ttl-seconds", false),
            ConfigDefaultMerger.PathInfo("reward", true),
            ConfigDefaultMerger.PathInfo("reward.command-timeout-seconds", false)
        )
        val existing = setOf("database-profile", "cache.snapshot-ttl-seconds")

        assertEquals(
            listOf("reward.command-timeout-seconds"),
            ConfigDefaultMerger.missingLeafPaths(defaults, existing)
        )
    }
}
