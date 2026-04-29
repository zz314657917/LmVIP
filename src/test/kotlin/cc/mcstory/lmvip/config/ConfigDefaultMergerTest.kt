package cc.mcstory.lmvip.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigDefaultMergerTest {

    @Test
    fun `missing leaf keys are detected without overwriting existing keys`() {
        val defaults = listOf(
            ConfigDefaultMerger.PathInfo("database-profile", false),
            ConfigDefaultMerger.PathInfo("sync", true),
            ConfigDefaultMerger.PathInfo("sync.legacy-groups", false),
            ConfigDefaultMerger.PathInfo("cache", true),
            ConfigDefaultMerger.PathInfo("cache.snapshot-ttl-seconds", false),
            ConfigDefaultMerger.PathInfo("cache.retain-after-quit-seconds", false),
            ConfigDefaultMerger.PathInfo("reward", true),
            ConfigDefaultMerger.PathInfo("reward.command-timeout-seconds", false)
        )
        val existing = setOf("database-profile", "cache.snapshot-ttl-seconds")

        assertEquals(
            listOf("sync.legacy-groups", "cache.retain-after-quit-seconds", "reward.command-timeout-seconds"),
            ConfigDefaultMerger.missingLeafPaths(defaults, existing)
        )
    }

    @Test
    fun `levels file is skipped when default merging existing resources`() {
        assertEquals(false, ConfigDefaultMerger.shouldMergeExistingResource("levels.yml"))
        assertEquals(true, ConfigDefaultMerger.shouldMergeExistingResource("config.yml"))
        assertEquals(true, ConfigDefaultMerger.shouldMergeExistingResource("gui.yml"))
        assertEquals(true, ConfigDefaultMerger.shouldMergeExistingResource("lang.yml"))
    }
}
