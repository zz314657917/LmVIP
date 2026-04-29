package cc.mcstory.lmvip.service

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RewardCommandContextTest {

    @Test
    fun `reward command placeholders render from context`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val context = RewardCommandContext("Steve", playerId, 3, "season-9", claimId = 12L, periodKey = "2026-04", dispatchId = "claim-12")

        assertEquals(
            "give Steve diamond 3 season-9 00000000-0000-0000-0000-000000000001 12 2026-04 claim-12",
            context.render("give %player% diamond %level% %season% %uuid% %claim_id% %period% %dispatch_id%")
        )
    }
}
