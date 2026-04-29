package cc.mcstory.lmvip.storage

import cc.mcstory.lmcore.api.DatabaseService
import java.sql.Connection
import java.sql.DriverManager

class H2DatabaseService(private val url: String) : DatabaseService {
    override fun isEnabled(): Boolean = true

    override fun isAvailable(): Boolean = true

    override fun getConnection(): Connection = DriverManager.getConnection(url)

    override fun testConnection(): Boolean = getConnection().use { true }

    override fun jdbcUrl(): String = url

    override fun driverClassName(): String = "org.h2.Driver"

    override fun statusMessage(): String = "ok"
}
