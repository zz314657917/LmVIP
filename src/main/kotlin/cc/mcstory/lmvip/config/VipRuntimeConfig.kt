package cc.mcstory.lmvip.config

import cc.mcstory.lmvip.model.VipLevel
import cc.mcstory.lmvip.time.PeriodService

data class VipRuntimeConfig(
    val databaseProfile: String,
    val messagePrefix: String,
    val language: LanguageRuntimeConfig,
    val levels: List<VipLevel>,
    val legacyGroups: List<String>,
    val gui: GuiRuntimeConfig,
    val periods: PeriodService,
    val snapshotTtlMillis: Long,
    val cacheRetainAfterQuitMillis: Long,
    val rewardCommandTimeoutSeconds: Long,
)

data class LanguageRuntimeConfig(
    val prefix: String,
    val messages: Map<String, String>,
    val lists: Map<String, List<String>>,
) {
    fun message(key: String, vararg placeholders: Pair<String, Any?>): String {
        return color(prefix) + raw(key, *placeholders)
    }

    fun raw(key: String, vararg placeholders: Pair<String, Any?>): String {
        return color(render(messages[key] ?: key, placeholders))
    }

    fun list(key: String, vararg placeholders: Pair<String, Any?>): List<String> {
        return lists[key].orEmpty().map { color(render(it, placeholders)) }
    }

    private fun render(value: String, placeholders: Array<out Pair<String, Any?>>): String {
        var result = value
        for ((key, replacement) in placeholders) {
            result = result.replace("%$key%", replacement?.toString() ?: "")
        }
        return result
    }

    private fun color(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '&' && index + 1 < value.length && isColorCode(value[index + 1])) {
                builder.append('§')
                builder.append(value[index + 1].lowercaseChar())
                index += 2
            } else {
                builder.append(current)
                index++
            }
        }
        return builder.toString()
    }

    private fun isColorCode(char: Char): Boolean {
        return char in '0'..'9' ||
            char.lowercaseChar() in 'a'..'f' ||
            char.lowercaseChar() in 'k'..'o' ||
            char.equals('r', true) ||
            char.equals('x', true)
    }
}

data class GuiRuntimeConfig(
    val title: String,
    val rows: Int,
    val slots: GuiSlots,
    val items: Map<String, GuiItemConfig>,
)

data class GuiSlots(
    val status: Int,
    val daily: Int,
    val weekly: Int,
    val monthly: Int,
    val levels: List<Int>,
)

data class GuiItemConfig(
    val material: String,
    val name: String,
)
