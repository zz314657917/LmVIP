package cc.mcstory.lmvip.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ClaimTypeTest {

    @Test
    fun `claim type parses once reward key`() {
        assertEquals(ClaimType.ONCE, ClaimType.parse("once"))
        assertEquals("once", ClaimType.ONCE.dbKey)
    }
}
