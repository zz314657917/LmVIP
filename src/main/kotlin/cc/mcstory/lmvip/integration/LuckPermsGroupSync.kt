package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.model.VipLevel
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class LuckPermsGroupSync(levels: List<VipLevel>) {
    private var groups = emptySet<String>()
    private var levelGroup = emptyMap<Int, String>()
    private val api = resolveApi()

    init {
        updateLevels(levels)
    }

    fun updateLevels(levels: List<VipLevel>) {
        groups = levels.map { it.group }.filter { it.isNotBlank() }.toSet()
        levelGroup = levels.associate { it.level to it.group }
    }

    fun sync(player: OfflinePlayer, vipLevel: Int): Boolean {
        val api = api ?: return false
        val user = loadUser(api, player.uniqueId) ?: return false
        var changed = false
        for (node in getNodes(user).toList()) {
            val key = readNodeKey(node)
            if (key.startsWith("group.", true)) {
                val group = key.substringAfter("group.")
                if (groups.any { it.equals(group, true) }) {
                    invokeDataMutation(user, "remove", node)
                    changed = true
                }
            }
        }
        val targetGroup = levelGroup[vipLevel]
        if (!targetGroup.isNullOrBlank()) {
            val node = buildInheritanceNode(targetGroup)
            invokeDataMutation(user, "add", node)
            changed = true
        }
        if (changed) {
            saveUser(api, user)
        }
        return true
    }

    private fun resolveApi(): Any? {
        val plugin = Bukkit.getPluginManager().getPlugin("LuckPerms")
        if (plugin == null || !plugin.isEnabled) return null
        return try {
            val providerClass = Class.forName("net.luckperms.api.LuckPermsProvider", true, plugin.javaClass.classLoader)
            providerClass.getMethod("get").invoke(null)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadUser(api: Any, playerId: UUID): Any? {
        val manager = api.javaClass.getMethod("getUserManager").invoke(api)
        findMethod(manager.javaClass, "getUser", UUID::class.java)?.let {
            val cached = it.invoke(manager, playerId)
            if (cached != null) return cached
        }
        val method = findMethod(manager.javaClass, "loadUser", UUID::class.java) ?: return null
        val value = method.invoke(manager, playerId)
        return if (value is CompletableFuture<*>) value.get(5L, TimeUnit.SECONDS) else value
    }

    private fun saveUser(api: Any, user: Any) {
        val manager = api.javaClass.getMethod("getUserManager").invoke(api)
        val method = manager.javaClass.methods.firstOrNull { it.name == "saveUser" && it.parameterTypes.size == 1 } ?: return
        val value = method.invoke(manager, user)
        if (value is CompletableFuture<*>) value.get(5L, TimeUnit.SECONDS)
    }

    private fun getNodes(user: Any): Collection<Any> {
        val method = user.javaClass.methods.firstOrNull {
            it.name == "getNodes" || it.name == "getDistinctNodes" || it.name == "getOwnNodes"
        } ?: return emptyList()
        val value = method.invoke(user)
        return if (value is Collection<*>) value.filterNotNull() else emptyList()
    }

    private fun readNodeKey(node: Any): String {
        val value = invokeQuietly(node, "getKey") ?: invokeQuietly(node, "getPermission")
        return value?.toString()?.trim() ?: ""
    }

    private fun buildInheritanceNode(group: String): Any {
        val nodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode")
        val builder = nodeClass.getMethod("builder", String::class.java).invoke(null, group)
        val build = builder.javaClass.methods.first { it.name == "build" && it.parameterTypes.isEmpty() }
        return build.invoke(builder)
    }

    private fun invokeDataMutation(user: Any, methodName: String, node: Any) {
        val data = user.javaClass.getMethod("data").invoke(user)
        val method = data.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.size == 1 }
            ?: throw NoSuchMethodException("LuckPerms data.$methodName(node)")
        method.invoke(data, node)
    }

    private fun invokeQuietly(target: Any, name: String): Any? {
        return try {
            target.javaClass.getMethod(name).invoke(target)
        } catch (_: Exception) {
            null
        }
    }

    private fun findMethod(type: Class<*>, name: String, vararg params: Class<*>): Method? {
        return try {
            type.getMethod(name, *params)
        } catch (_: Exception) {
            null
        }
    }
}
