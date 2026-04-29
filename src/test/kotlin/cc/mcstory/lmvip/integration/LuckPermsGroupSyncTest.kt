package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.model.RewardRule
import cc.mcstory.lmvip.model.VipLevel
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LuckPermsGroupSyncTest {

    @Test
    fun `managed groups include configured groups and legacy groups`() {
        val sync = LuckPermsGroupSync(
            levels = listOf(level(1, "vip1"), level(2, "vip2")),
            legacyGroups = listOf("vip_old", "vip2", "")
        )

        assertEquals(setOf("vip1", "vip2", "vip_old"), sync.managedGroups())
    }

    @Test
    fun `sync removes managed and legacy groups adds target group and saves user`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000051")
        val user = FakeUser(
            mutableListOf(
                FakeNode("group.vip1"),
                FakeNode("group.vip_old"),
                FakeNode("group.member")
            )
        )
        val manager = FakeUserManager(user)
        val sync = LuckPermsGroupSync(
            levels = listOf(level(1, "vip1"), level(2, "vip2")),
            legacyGroups = listOf("vip_old"),
            apiProvider = { FakeLuckPermsApi(manager) },
            inheritanceNodeFactory = { FakeNode("group.$it") }
        )

        assertTrue(sync.sync(playerId, 2))

        assertEquals(listOf("group.vip1", "group.vip_old"), user.data.removed.map { it.permissionKey })
        assertEquals(listOf("group.vip2"), user.data.added.map { it.permissionKey })
        assertEquals(1, manager.saved)
    }

    private fun level(level: Int, group: String) = VipLevel(
        level = level,
        name = "VIP$level",
        plainName = "VIP$level",
        totalPoints = level * 100L,
        group = group,
        benefits = emptyList(),
        daily = RewardRule(0, emptyList()),
        weekly = RewardRule(0, emptyList()),
        monthly = RewardRule(0, emptyList()),
        once = RewardRule(0, emptyList()),
    )

    class FakeLuckPermsApi(private val manager: FakeUserManager) {
        fun getUserManager(): FakeUserManager = manager
    }

    class FakeUserManager(private val user: FakeUser) {
        var saved: Int = 0

        fun getUser(playerId: UUID): FakeUser = user

        fun saveUser(user: FakeUser) {
            saved++
        }
    }

    class FakeUser(private val nodes: MutableList<FakeNode>) {
        val data = FakeData(nodes)

        fun getNodes(): Collection<Any> = nodes.toList()

        fun data(): FakeData = data
    }

    class FakeData(private val nodes: MutableList<FakeNode>) {
        val removed = mutableListOf<FakeNode>()
        val added = mutableListOf<FakeNode>()

        fun remove(node: Any) {
            val fakeNode = node as FakeNode
            removed += fakeNode
            nodes.removeIf { it.permissionKey.equals(fakeNode.permissionKey, true) }
        }

        fun add(node: Any) {
            val fakeNode = node as FakeNode
            added += fakeNode
            nodes += fakeNode
        }
    }

    data class FakeNode(val permissionKey: String) {
        fun getKey(): String = permissionKey
    }
}
