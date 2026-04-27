package cc.mcstory.lmvip.service

import cc.mcstory.lmvip.model.VipLevel

object VipCalculator {

    fun levelFor(totalPoints: Long, levels: List<VipLevel>): VipLevel? {
        return levels.filter { totalPoints >= it.totalPoints }.maxByOrNull { it.level }
    }

    fun nextNeed(totalPoints: Long, levels: List<VipLevel>): Long {
        val next = levels.filter { totalPoints < it.totalPoints }.minByOrNull { it.totalPoints }
            ?: return 0L
        return (next.totalPoints - totalPoints).coerceAtLeast(0L)
    }
}
