package cc.mcstory.lmvip.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CommandHashTest {
    @Test
    fun `sha256 returns stable lowercase hex`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            CommandHash.sha256("hello")
        )
    }
}
