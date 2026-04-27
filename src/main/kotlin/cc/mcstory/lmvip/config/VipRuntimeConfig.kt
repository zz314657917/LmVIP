package cc.mcstory.lmvip.config

import cc.mcstory.lmvip.model.VipLevel
import cc.mcstory.lmvip.time.PeriodService

data class VipRuntimeConfig(
    val databaseProfile: String,
    val messagePrefix: String,
    val levels: List<VipLevel>,
    val gui: GuiRuntimeConfig,
    val periods: PeriodService,
    val snapshotTtlMillis: Long,
)

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
