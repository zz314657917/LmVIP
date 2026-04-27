package cc.mcstory.lmvip.time

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodServiceTest {
    @Test
    fun `period keys use configured timezone`() {
        val service = PeriodService(ZoneId.of("Asia/Shanghai"), DayOfWeek.MONDAY)
        val millis = LocalDateTime.of(2026, 4, 27, 12, 0)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()
        assertEquals("20260427", service.dayKey(millis))
        assertEquals("202604", service.monthKey(millis))
        assertEquals("2026-W18", service.weekKey(millis))
    }
}
