package cc.mcstory.lmvip.storage

import cc.mcstory.lmvip.model.PointDimension
import cc.mcstory.lmvip.model.SeasonRecord
import cc.mcstory.lmvip.model.VipSnapshot
import cc.mcstory.lmvip.service.VipCalculator
import cc.mcstory.lmvip.time.PeriodService
import cc.mcstory.lmcore.api.DatabaseRegistryService
import cc.mcstory.lmcore.api.DatabaseService
import cc.mcstory.lmcore.api.LmCoreApi
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.util.UUID

class JdbcVipRepository(
    private val database: DatabaseService,
    private var periods: PeriodService,
) {

    companion object {
        fun fromLmCore(profile: String, periods: PeriodService): JdbcVipRepository {
            val registry = LmCoreApi.require(DatabaseRegistryService::class.java)
            val database = registry.database(profile)
            if (!database.isAvailable()) {
                throw IllegalStateException("LmCore database profile '$profile' is unavailable: ${database.statusMessage()}")
            }
            return JdbcVipRepository(database, periods)
        }
    }

    fun initialize() {
        database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS lmvip_seasons (
                        season_id VARCHAR(64) NOT NULL PRIMARY KEY,
                        display_name VARCHAR(128) NOT NULL,
                        active TINYINT(1) NOT NULL DEFAULT 0,
                        started_at BIGINT NOT NULL,
                        ended_at BIGINT NULL,
                        created_by VARCHAR(64) NOT NULL DEFAULT 'system',
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS lmvip_transactions (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        season_id VARCHAR(64) NULL,
                        dimension VARCHAR(16) NOT NULL,
                        amount BIGINT NOT NULL,
                        source VARCHAR(64) NULL,
                        order_id VARCHAR(128) NULL,
                        operator VARCHAR(64) NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        day_key VARCHAR(16) NOT NULL,
                        month_key VARCHAR(16) NOT NULL,
                        created_at BIGINT NOT NULL,
                        rolled_back_id BIGINT NULL,
                        UNIQUE KEY uk_lmvip_source_order (source, order_id),
                        INDEX idx_lmvip_player (player_uuid),
                        INDEX idx_lmvip_player_season (player_uuid, season_id),
                        INDEX idx_lmvip_period (player_uuid, season_id, day_key, month_key)
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS lmvip_accounts (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        player_name VARCHAR(32) NOT NULL,
                        season_id VARCHAR(64) NULL,
                        total_points BIGINT NOT NULL DEFAULT 0,
                        season_points BIGINT NOT NULL DEFAULT 0,
                        monthly_points BIGINT NOT NULL DEFAULT 0,
                        daily_points BIGINT NOT NULL DEFAULT 0,
                        vip_level INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS lmvip_claims (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        season_id VARCHAR(64) NOT NULL,
                        claim_type VARCHAR(16) NOT NULL,
                        level INT NOT NULL,
                        period_key VARCHAR(32) NOT NULL,
                        claimed_at BIGINT NOT NULL,
                        UNIQUE KEY uk_lmvip_claim (player_uuid, season_id, claim_type, level, period_key),
                        INDEX idx_lmvip_claim_player (player_uuid, season_id)
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS lmvip_admin_audit (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        action VARCHAR(64) NOT NULL,
                        operator VARCHAR(64) NOT NULL,
                        target VARCHAR(128) NOT NULL,
                        detail VARCHAR(512) NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    fun updatePeriods(periods: PeriodService) {
        this.periods = periods
    }

    fun createSeason(seasonId: String, displayName: String, operator: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT season_id FROM lmvip_seasons WHERE season_id=?").use {
                it.setString(1, seasonId)
                it.executeQuery().use { rs -> if (rs.next()) return false }
            }
            connection.prepareStatement(
                "INSERT INTO lmvip_seasons(season_id, display_name, active, started_at, ended_at, created_by, created_at) VALUES(?,?,?,?,?,?,?)"
            ).use {
                val now = periods.nowMillis()
                it.setString(1, seasonId)
                it.setString(2, displayName)
                it.setInt(3, 0)
                it.setLong(4, now)
                it.setNull(5, Types.BIGINT)
                it.setString(6, operator)
                it.setLong(7, now)
                it.executeUpdate()
            }
            audit(connection, "season_create", operator, seasonId, displayName)
            return true
        }
    }

    fun activateSeason(seasonId: String, operator: String): Boolean {
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
                if (!seasonExists(connection, seasonId)) {
                    connection.rollback()
                    return false
                }
                val now = periods.nowMillis()
                connection.prepareStatement("UPDATE lmvip_seasons SET active=0, ended_at=COALESCE(ended_at, ?) WHERE active=1").use {
                    it.setLong(1, now)
                    it.executeUpdate()
                }
                connection.prepareStatement("UPDATE lmvip_seasons SET active=1, ended_at=NULL WHERE season_id=?").use {
                    it.setString(1, seasonId)
                    it.executeUpdate()
                }
                audit(connection, "season_activate", operator, seasonId, "")
                connection.commit()
                return true
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun startSeason(seasonId: String, displayName: String, operator: String): Boolean {
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
                val now = periods.nowMillis()
                if (!seasonExists(connection, seasonId)) {
                    connection.prepareStatement(
                        "INSERT INTO lmvip_seasons(season_id, display_name, active, started_at, ended_at, created_by, created_at) VALUES(?,?,?,?,?,?,?)"
                    ).use {
                        it.setString(1, seasonId)
                        it.setString(2, displayName)
                        it.setInt(3, 0)
                        it.setLong(4, now)
                        it.setNull(5, Types.BIGINT)
                        it.setString(6, operator)
                        it.setLong(7, now)
                        it.executeUpdate()
                    }
                }
                connection.prepareStatement("UPDATE lmvip_seasons SET active=0, ended_at=COALESCE(ended_at, ?) WHERE active=1").use {
                    it.setLong(1, now)
                    it.executeUpdate()
                }
                connection.prepareStatement("UPDATE lmvip_seasons SET display_name=?, active=1, started_at=?, ended_at=NULL WHERE season_id=?").use {
                    it.setString(1, displayName)
                    it.setLong(2, now)
                    it.setString(3, seasonId)
                    it.executeUpdate()
                }
                audit(connection, "season_start", operator, seasonId, displayName)
                connection.commit()
                return true
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun activeSeason(): SeasonRecord? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM lmvip_seasons WHERE active=1 ORDER BY started_at DESC LIMIT 1").use {
                it.executeQuery().use { rs -> return if (rs.next()) readSeason(rs) else null }
            }
        }
    }

    fun listSeasons(): List<SeasonRecord> {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM lmvip_seasons ORDER BY started_at ASC").use {
                it.executeQuery().use { rs ->
                    val result = mutableListOf<SeasonRecord>()
                    while (rs.next()) result += readSeason(rs)
                    return result
                }
            }
        }
    }

    fun addRecharge(playerId: UUID, playerName: String, amount: Long, source: String, orderId: String, operator: String, reason: String): Long? {
        val season = activeSeason() ?: throw IllegalStateException("No active season. Use /vipadmin season start first.")
        return insertTransaction(playerId, playerName, season.seasonId, "recharge", amount, source, orderId, operator, reason)
    }

    fun adjustSeason(playerId: UUID, playerName: String, seasonId: String, targetValue: Long?, delta: Long?, operator: String, reason: String): Long? {
        val current = snapshot(playerId, playerName, seasonId, emptyList()).seasonPoints
        val amount = when {
            targetValue != null -> targetValue.coerceAtLeast(0L) - current
            delta != null && delta < 0 -> -((-delta).coerceAtMost(current))
            delta != null -> delta
            else -> 0L
        }
        if (amount == 0L) return null
        return insertTransaction(playerId, playerName, seasonId, "season", amount, null, null, operator, reason)
    }

    fun adjustDimension(playerId: UUID, playerName: String, dimension: PointDimension, targetValue: Long?, delta: Long?, operator: String, reason: String): Long? {
        val season = activeSeason()
        val snapshot = snapshot(playerId, playerName, season?.seasonId, emptyList())
        val current = when (dimension) {
            PointDimension.TOTAL -> snapshot.totalPoints
            PointDimension.SEASON -> snapshot.seasonPoints
            PointDimension.MONTHLY -> snapshot.monthlyPoints
            PointDimension.DAILY -> snapshot.dailyPoints
        }
        val amount = when {
            targetValue != null -> targetValue.coerceAtLeast(0L) - current
            delta != null && delta < 0 -> -((-delta).coerceAtMost(current))
            delta != null -> delta
            else -> 0L
        }
        if (amount == 0L) return null
        val seasonId = if (dimension == PointDimension.TOTAL) season?.seasonId else requireNotNull(season?.seasonId) { "No active season." }
        return insertTransaction(playerId, playerName, seasonId, dimension.dbKey, amount, null, null, operator, reason)
    }

    fun rollback(transactionId: Long, operator: String, reason: String): Long? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM lmvip_transactions WHERE id=?").use {
                it.setLong(1, transactionId)
                it.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    val playerId = UUID.fromString(rs.getString("player_uuid"))
                    val playerName = rs.getString("player_name")
                    val seasonId = rs.getString("season_id")
                    val dimension = rs.getString("dimension")
                    val amount = -rs.getLong("amount")
                    val dayKey = rs.getString("day_key")
                    val monthKey = rs.getString("month_key")
                    return insertTransaction(
                        playerId, playerName, seasonId, dimension, amount, "rollback", "tx-$transactionId",
                        operator, reason.ifBlank { "rollback $transactionId" }, dayKey, monthKey, transactionId
                    )
                }
            }
        }
    }

    fun snapshot(playerId: UUID, playerName: String, seasonId: String?, levels: List<cc.mcstory.lmvip.model.VipLevel>): VipSnapshot {
        val shouldUpsertAccount = seasonId == null && levels.isNotEmpty()
        val season = if (seasonId != null) {
            findSeason(seasonId) ?: throw IllegalArgumentException("Season not found: $seasonId")
        } else {
            activeSeason()
        }
        val actualSeasonId = season?.seasonId
        val dayKey = periods.dayKey()
        val monthKey = periods.monthKey()
        database.getConnection().use { connection ->
            val total = sum(connection, playerId, null, null, null, setOf("recharge", "total"))
            val seasonPoints = actualSeasonId?.let { sum(connection, playerId, it, null, null, setOf("recharge", "season")) } ?: 0L
            val monthly = actualSeasonId?.let { sum(connection, playerId, it, null, monthKey, setOf("recharge", "monthly")) } ?: 0L
            val daily = actualSeasonId?.let { sum(connection, playerId, it, dayKey, null, setOf("recharge", "daily")) } ?: 0L
            val level = VipCalculator.levelFor(total, levels)
            val snapshot = VipSnapshot(
                playerId = playerId,
                playerName = playerName,
                seasonId = actualSeasonId,
                seasonName = season?.displayName,
                totalPoints = total,
                seasonPoints = seasonPoints,
                monthlyPoints = monthly,
                dailyPoints = daily,
                vipLevel = level?.level ?: 0,
                vipLevelName = level?.plainName ?: "无",
                nextLevelNeed = VipCalculator.nextNeed(total, levels),
            )
            if (shouldUpsertAccount) {
                upsertAccount(connection, snapshot)
            }
            return snapshot
        }
    }

    fun hasClaim(playerId: UUID, seasonId: String, claimType: String, level: Int, periodKey: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(
                "SELECT id FROM lmvip_claims WHERE player_uuid=? AND season_id=? AND claim_type=? AND level=? AND period_key=?"
            ).use {
                it.setString(1, playerId.toString())
                it.setString(2, seasonId)
                it.setString(3, claimType)
                it.setInt(4, level)
                it.setString(5, periodKey)
                it.executeQuery().use { rs -> return rs.next() }
            }
        }
    }

    fun insertClaim(playerId: UUID, playerName: String, seasonId: String, claimType: String, level: Int, periodKey: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO lmvip_claims(player_uuid, player_name, season_id, claim_type, level, period_key, claimed_at) VALUES(?,?,?,?,?,?,?)"
            ).use {
                it.setString(1, playerId.toString())
                it.setString(2, playerName)
                it.setString(3, seasonId)
                it.setString(4, claimType)
                it.setInt(5, level)
                it.setString(6, periodKey)
                it.setLong(7, periods.nowMillis())
                return try {
                    it.executeUpdate() > 0
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    private fun findSeason(seasonId: String): SeasonRecord? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM lmvip_seasons WHERE season_id=?").use {
                it.setString(1, seasonId)
                it.executeQuery().use { rs -> return if (rs.next()) readSeason(rs) else null }
            }
        }
    }

    private fun insertTransaction(
        playerId: UUID,
        playerName: String,
        seasonId: String?,
        dimension: String,
        amount: Long,
        source: String?,
        orderId: String?,
        operator: String,
        reason: String,
        dayKey: String = periods.dayKey(),
        monthKey: String = periods.monthKey(),
        rolledBackId: Long? = null,
    ): Long? {
        if (amount == 0L) return null
        if (!source.isNullOrBlank() && !orderId.isNullOrBlank() && existingSourceOrder(source, orderId)) {
            return null
        }
        database.getConnection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO lmvip_transactions(
                    player_uuid, player_name, season_id, dimension, amount, source, order_id,
                    operator, reason, day_key, month_key, created_at, rolled_back_id
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
                Statement.RETURN_GENERATED_KEYS
            ).use {
                it.setString(1, playerId.toString())
                it.setString(2, playerName)
                it.setString(3, seasonId)
                it.setString(4, dimension)
                it.setLong(5, amount)
                it.setString(6, source)
                it.setString(7, orderId)
                it.setString(8, operator)
                it.setString(9, reason)
                it.setString(10, dayKey)
                it.setString(11, monthKey)
                it.setLong(12, periods.nowMillis())
                if (rolledBackId == null) it.setNull(13, Types.BIGINT) else it.setLong(13, rolledBackId)
                return try {
                    it.executeUpdate()
                    it.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else null }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun existingSourceOrder(source: String, orderId: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT id FROM lmvip_transactions WHERE source=? AND order_id=? LIMIT 1").use {
                it.setString(1, source)
                it.setString(2, orderId)
                it.executeQuery().use { rs -> return rs.next() }
            }
        }
    }

    private fun sum(connection: Connection, playerId: UUID, seasonId: String?, dayKey: String?, monthKey: String?, dimensions: Set<String>): Long {
        val clauses = mutableListOf("player_uuid=?")
        if (seasonId != null) clauses += "season_id=?"
        if (dayKey != null) clauses += "day_key=?"
        if (monthKey != null) clauses += "month_key=?"
        clauses += "dimension IN (${dimensions.joinToString(",") { "?" }})"
        val sql = "SELECT COALESCE(SUM(amount),0) FROM lmvip_transactions WHERE ${clauses.joinToString(" AND ")}"
        connection.prepareStatement(sql).use {
            var index = 1
            it.setString(index++, playerId.toString())
            if (seasonId != null) it.setString(index++, seasonId)
            if (dayKey != null) it.setString(index++, dayKey)
            if (monthKey != null) it.setString(index++, monthKey)
            for (dimension in dimensions) it.setString(index++, dimension)
            it.executeQuery().use { rs -> return if (rs.next()) rs.getLong(1).coerceAtLeast(0L) else 0L }
        }
    }

    private fun upsertAccount(connection: Connection, snapshot: VipSnapshot) {
        connection.prepareStatement(
            """
            INSERT INTO lmvip_accounts(player_uuid, player_name, season_id, total_points, season_points, monthly_points, daily_points, vip_level, updated_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
              player_name=VALUES(player_name),
              season_id=VALUES(season_id),
              total_points=VALUES(total_points),
              season_points=VALUES(season_points),
              monthly_points=VALUES(monthly_points),
              daily_points=VALUES(daily_points),
              vip_level=VALUES(vip_level),
              updated_at=VALUES(updated_at)
            """.trimIndent()
        ).use {
            it.setString(1, snapshot.playerId.toString())
            it.setString(2, snapshot.playerName)
            it.setString(3, snapshot.seasonId)
            it.setLong(4, snapshot.totalPoints)
            it.setLong(5, snapshot.seasonPoints)
            it.setLong(6, snapshot.monthlyPoints)
            it.setLong(7, snapshot.dailyPoints)
            it.setInt(8, snapshot.vipLevel)
            it.setLong(9, periods.nowMillis())
            it.executeUpdate()
        }
    }

    private fun seasonExists(connection: Connection, seasonId: String): Boolean {
        connection.prepareStatement("SELECT season_id FROM lmvip_seasons WHERE season_id=?").use {
            it.setString(1, seasonId)
            it.executeQuery().use { rs -> return rs.next() }
        }
    }

    private fun readSeason(rs: ResultSet): SeasonRecord {
        val ended = rs.getLong("ended_at").let { if (rs.wasNull()) null else it }
        return SeasonRecord(
            seasonId = rs.getString("season_id"),
            displayName = rs.getString("display_name"),
            active = rs.getInt("active") == 1,
            startedAt = rs.getLong("started_at"),
            endedAt = ended,
        )
    }

    private fun audit(connection: Connection, action: String, operator: String, target: String, detail: String) {
        connection.prepareStatement("INSERT INTO lmvip_admin_audit(action, operator, target, detail, created_at) VALUES(?,?,?,?,?)").use {
            it.setString(1, action)
            it.setString(2, operator)
            it.setString(3, target)
            it.setString(4, detail)
            it.setLong(5, periods.nowMillis())
            it.executeUpdate()
        }
    }
}
