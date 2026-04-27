package cc.mcstory.lmvip.config

import cc.mcstory.lmvip.model.RewardRule
import cc.mcstory.lmvip.model.VipLevel
import cc.mcstory.lmvip.time.PeriodService
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.DayOfWeek
import java.time.ZoneId

object VipConfigManager {

    private val defaultResources = listOf("config.yml", "levels.yml", "gui.yml")

    fun ensureDefaults(plugin: JavaPlugin) {
        for (resource in defaultResources) {
            val target = File(plugin.dataFolder, resource)
            if (!target.exists()) {
                plugin.saveResource(resource, false)
            }
        }
    }

    fun load(plugin: JavaPlugin): VipRuntimeConfig {
        plugin.reloadConfig()
        val config = plugin.config
        val levelsFile = File(plugin.dataFolder, "levels.yml")
        val levelsConfig = YamlConfiguration.loadConfiguration(levelsFile)
        val guiFile = File(plugin.dataFolder, "gui.yml")
        val guiConfig = YamlConfiguration.loadConfiguration(guiFile)
        val zone = ZoneId.of(config.getString("timezone", "Asia/Shanghai")!!)
        val weekStart = runCatching {
            DayOfWeek.valueOf(config.getString("week-start", "MONDAY")!!.uppercase())
        }.getOrDefault(DayOfWeek.MONDAY)
        val levels = readLevels(levelsConfig)
        return VipRuntimeConfig(
            databaseProfile = config.getString("database-profile", "LmVIP")!!,
            messagePrefix = color(config.getString("message-prefix", "&6[LmVIP]&r ")!!),
            levels = levels,
            gui = readGui(guiConfig),
            periods = PeriodService(zone, weekStart),
            snapshotTtlMillis = config.getLong("cache.snapshot-ttl-seconds", 30L) * 1000L,
        )
    }

    private fun readGui(config: YamlConfiguration): GuiRuntimeConfig {
        return GuiRuntimeConfig(
            title = color(config.getString("title", "&6VIP 权益")!!),
            rows = config.getInt("rows", 6).coerceIn(1, 6),
            slots = GuiSlots(
                status = config.getInt("slots.status", 4),
                daily = config.getInt("slots.daily", 20),
                weekly = config.getInt("slots.weekly", 22),
                monthly = config.getInt("slots.monthly", 24),
                levels = config.getIntegerList("slots.levels").ifEmpty { listOf(28, 29, 30, 31, 32, 33, 34) }
            ),
            items = mapOf(
                "status" to readGuiItem(config, "status", "BOOK", "&e我的 VIP"),
                "daily" to readGuiItem(config, "daily", "CHEST", "&a日累充奖励"),
                "weekly" to readGuiItem(config, "weekly", "ENDER_CHEST", "&bVIP 周礼包"),
                "monthly" to readGuiItem(config, "monthly", "DIAMOND", "&d月累充奖励"),
                "level" to readGuiItem(config, "level", "PAPER", "&e%level_name%")
            )
        )
    }

    private fun readGuiItem(config: YamlConfiguration, key: String, material: String, name: String): GuiItemConfig {
        return GuiItemConfig(
            material = config.getString("items.$key.material", material)!!,
            name = color(config.getString("items.$key.name", name)!!)
        )
    }

    private fun readLevels(config: YamlConfiguration): List<VipLevel> {
        val section = config.getConfigurationSection("levels") ?: return emptyList()
        return section.getKeys(false).mapNotNull { key ->
            val level = key.toIntOrNull() ?: return@mapNotNull null
            val path = "levels.$key"
            VipLevel(
                level = level,
                name = color(config.getString("$path.name", "VIP$level")!!),
                plainName = ChatColor.stripColor(color(config.getString("$path.name", "VIP$level")!!)) ?: "VIP$level",
                totalPoints = config.getLong("$path.total-points", 0L),
                group = config.getString("$path.group", "vip$level")!!,
                benefits = config.getStringList("$path.benefits").map(::color),
                daily = RewardRule(
                    threshold = config.getLong("$path.daily-reward.threshold", 0L),
                    commands = config.getStringList("$path.daily-reward.commands")
                ),
                weekly = RewardRule(
                    threshold = config.getLong("$path.weekly-reward.monthly-threshold", 0L),
                    commands = config.getStringList("$path.weekly-reward.commands")
                ),
                monthly = RewardRule(
                    threshold = config.getLong("$path.monthly-reward.threshold", 0L),
                    commands = config.getStringList("$path.monthly-reward.commands")
                )
            )
        }.sortedBy { it.level }
    }

    fun color(value: String): String {
        return ChatColor.translateAlternateColorCodes('&', value)
    }
}
