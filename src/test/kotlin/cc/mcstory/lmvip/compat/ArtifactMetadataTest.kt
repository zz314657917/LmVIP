package cc.mcstory.lmvip.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class ArtifactMetadataTest {

    @Test
    fun `metadata constants match supported artifact targets`() {
        assertEquals("1.12.2", ArtifactMetadata.LEGACY_TARGET)
        assertEquals("1.20.1", ArtifactMetadata.MODERN_TARGET)
    }

    @Test
    fun `runtime status summary includes artifact server dependencies and verdict`() {
        val status = RuntimeCompatibilityStatus(
            artifactTarget = "1.20.1",
            artifactJavaTarget = "17",
            javaRuntime = "17.0.10",
            serverType = ServerType.PAPER,
            serverVersion = "git-Paper-196 (MC: 1.20.1)",
            bukkitVersion = "1.20.1-R0.1-SNAPSHOT",
            lmCore = DependencyState.ENABLED,
            luckPerms = DependencyState.ENABLED,
            placeholderApi = DependencyState.MISSING,
            verdict = CompatibilityVerdict.SUPPORTED,
        )

        val summary = status.summary()

        assertEquals(true, "artifact=1.20.1/java17" in summary)
        assertEquals(true, "server=PAPER/1.20.1-R0.1-SNAPSHOT" in summary)
        assertEquals(true, "LmCore:ENABLED" in summary)
        assertEquals(true, "verdict=SUPPORTED" in summary)
    }
}
