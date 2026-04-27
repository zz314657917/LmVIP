package cc.mcstory.lmvip.service

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RewardCommandContextTest {

    @Test
    fun `reward command placeholders render from context`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val context = RewardCommandContext("Steve", playerId, 3, "season-9")

        assertEquals(
            "give Steve diamond 3 season-9 00000000-0000-0000-0000-000000000001",
            context.render("give %player% diamond %level% %season% %uuid%")
        )
    }
}
