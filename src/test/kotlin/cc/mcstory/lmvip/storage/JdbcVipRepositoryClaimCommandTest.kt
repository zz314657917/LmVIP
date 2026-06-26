package cc.mcstory.lmvip.storage

import cc.mcstory.lmvip.model.ClaimCommandStatus
import cc.mcstory.lmvip.model.ClaimDispatchStatus
import cc.mcstory.lmvip.model.ClaimWriteResult
import cc.mcstory.lmvip.time.PeriodService
import cc.mcstory.lmvip.util.CommandHash
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcVipRepositoryClaimCommandTest {

    @Test
    fun `begin claim creates command state rows in same repository path`() {
        val repository = repository()
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000021")

        val write = repository.beginClaim(
            playerId,
            "tester",
            "season-1",
            "daily",
            1,
            "20260429",
            listOf("say first", "say second")
        )

        val claim = assertIs<ClaimWriteResult.Inserted>(write).claim
        val commands = repository.listClaimCommands(claim.id)
        assertEquals(2, commands.size)
        assertEquals(0, commands[0].commandIndex)
        assertEquals("say first", commands[0].commandTemplate)
        assertEquals(CommandHash.sha256("say first"), commands[0].commandHash)
        assertEquals(ClaimCommandStatus.PENDING, commands[0].status)
        assertIs<ClaimWriteResult.Existing>(
            repository.beginClaim(playerId, "tester", "season-1", "daily", 1, "20260429", listOf("ignored"))
        )
    }

    @Test
    fun `manual review keeps command state rows with claim`() {
        val repository = repository()
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val claim = assertIs<ClaimWriteResult.Inserted>(
            repository.beginClaim(playerId, "tester", "season-1", "weekly", 1, "2026-W18", listOf("say first"))
        ).claim

        repository.updateClaimStatus(claim.id, ClaimDispatchStatus.FAILED, "failed")
        assertTrue(repository.markClaimManualReview(claim.id, "manual check"))

        assertEquals(
            ClaimDispatchStatus.MANUAL_REVIEW,
            repository.findClaim(playerId, "season-1", "weekly", 1, "2026-W18")?.status
        )
        assertEquals(1, repository.listClaimCommands(claim.id).size)
    }

    @Test
    fun `claim command status can be resumed after failed dispatch`() {
        val repository = repository()
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000023")
        val claim = assertIs<ClaimWriteResult.Inserted>(
            repository.beginClaim(playerId, "tester", "season-1", "monthly", 1, "202604", listOf("say first", "bad command"))
        ).claim

        repository.updateClaimCommandStatus(claim.id, 0, ClaimCommandStatus.SUCCEEDED, "say first")
        repository.updateClaimCommandStatus(claim.id, 1, ClaimCommandStatus.FAILED, "bad command", "failed")

        val commands = repository.listClaimCommands(claim.id)
        assertEquals(ClaimCommandStatus.SUCCEEDED, commands[0].status)
        assertEquals(ClaimCommandStatus.FAILED, commands[1].status)
        assertNotNull(commands[1].failureReason)
    }

    @Test
    fun `claim running transition is atomic`() {
        val repository = repository()
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000024")
        val claim = assertIs<ClaimWriteResult.Inserted>(
            repository.beginClaim(playerId, "tester", "season-1", "daily", 1, "20260429", listOf("say first"))
        ).claim
        repository.updateClaimStatus(claim.id, ClaimDispatchStatus.FAILED, "failed")

        assertTrue(repository.tryMarkClaimRunning(claim.id, ClaimDispatchStatus.FAILED, "worker-a", 12345L))
        assertEquals(false, repository.tryMarkClaimRunning(claim.id, ClaimDispatchStatus.FAILED, "worker-b", 12345L))
        assertEquals(ClaimDispatchStatus.RUNNING, repository.findClaim(playerId, "season-1", "daily", 1, "20260429")?.status)
    }

    @Test
    fun `initialize recovers expired running claim`() {
        val database = H2DatabaseService("jdbc:h2:mem:lmvip_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
        val periods = PeriodService(ZoneId.of("Asia/Shanghai"), DayOfWeek.MONDAY)
        val repository = JdbcVipRepository(database, periods)
        repository.initialize()
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000025")
        val claim = assertIs<ClaimWriteResult.Inserted>(
            repository.beginClaim(playerId, "tester", "season-1", "daily", 1, "20260429", listOf("say first"))
        ).claim
        assertTrue(repository.tryMarkClaimRunning(claim.id, ClaimDispatchStatus.PENDING, "worker-a", 1L))
        assertTrue(repository.tryMarkClaimCommandRunning(claim.id, 0))

        JdbcVipRepository(database, periods).initialize()

        assertEquals(ClaimDispatchStatus.FAILED, repository.findClaim(playerId, "season-1", "daily", 1, "20260429")?.status)
        assertEquals(ClaimCommandStatus.FAILED, repository.listClaimCommands(claim.id).single().status)
    }

    private fun repository(): JdbcVipRepository {
        val url = "jdbc:h2:mem:lmvip_${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        val repository = JdbcVipRepository(
            H2DatabaseService(url),
            PeriodService(ZoneId.of("Asia/Shanghai"), DayOfWeek.MONDAY)
        )
        repository.initialize()
        return repository
    }
}
