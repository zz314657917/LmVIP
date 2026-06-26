package cc.mcstory.lmvip.integration

import cc.mcstory.lmvip.model.VipLevel
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class LuckPermsGroupSync(
    levels: List<VipLevel>,
    legacyGroups: List<String> = emptyList(),
    private val apiProvider: (() -> Any?)? = null,
    private val inheritanceNodeFactory: ((String) -> Any)? = null,
    private val syncOverride: ((UUID, Int) -> Boolean)? = null,
) {
    private var groups = emptySet<String>()
    private var levelGroup = emptyMap<Int, String>()
    private var legacyGroupSet = emptySet<String>()
    private val api: Any? by lazy { apiProvider?.invoke() ?: resolveApi() }

    init {
        updateLevels(levels, legacyGroups)
    }

    fun updateLevels(levels: List<VipLevel>, legacyGroups: List<String> = emptyList()) {
        groups = levels.map { it.group }.filter { it.isNotBlank() }.toSet()
        levelGroup = levels.associate { it.level to it.group }
        this.legacyGroupSet = legacyGroups.filter { it.isNotBlank() }.toSet()
    }

    fun managedGroups(): Set<String> {
        return groups + legacyGroupSet
    }

    fun sync(player: OfflinePlayer, vipLevel: Int): Boolean {
        return sync(player.uniqueId, vipLevel)
    }

    fun sync(playerId: UUID, vipLevel: Int): Boolean {
        syncOverride?.let { return it(playerId, vipLevel) }
        return try {
            val api = api ?: return false
            val user = loadUser(api, playerId) ?: return false
            var changed = false
            for (node in getNodes(user).toList()) {
                val key = readNodeKey(node)
                if (key.startsWith("group.", true)) {
                    val group = key.substringAfter("group.")
                    if (managedGroups().any { it.equals(group, true) }) {
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
            true
        } catch (exception: Exception) {
            Bukkit.getLogger().warning("[LmVIP] LuckPerms sync failed for $playerId: ${exception.message}")
            false
        }
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
        val manager = invokeMethod(api, "getUserManager") ?: return
        val method = manager.javaClass.methods.firstOrNull { it.name == "saveUser" && it.parameterTypes.size == 1 } ?: return
        method.isAccessible = true
        val value = method.invoke(manager, user)
        if (value is CompletableFuture<*>) value.get(5L, TimeUnit.SECONDS)
    }

    private fun getNodes(user: Any): Collection<Any> {
        val method = user.javaClass.methods.firstOrNull {
            it.parameterTypes.isEmpty() && (it.name == "getNodes" || it.name == "getDistinctNodes" || it.name == "getOwnNodes")
        } ?: return emptyList()
        method.isAccessible = true
        val value = method.invoke(user)
        return if (value is Collection<*>) value.filterNotNull() else emptyList()
    }

    private fun readNodeKey(node: Any): String {
        val value = invokeQuietly(node, "getKey") ?: invokeQuietly(node, "getPermission")
        return value?.toString()?.trim() ?: ""
    }

    private fun buildInheritanceNode(group: String): Any {
        inheritanceNodeFactory?.let { return it(group) }
        val nodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode")
        val builder = nodeClass.getMethod("builder", String::class.java).invoke(null, group)
        val build = builder.javaClass.methods.first { it.name == "build" && it.parameterTypes.isEmpty() }
        build.isAccessible = true
        return build.invoke(builder)
    }

    private fun invokeDataMutation(user: Any, methodName: String, node: Any) {
        val data = invokeMethod(user, "data") ?: throw NoSuchMethodException("LuckPerms user.data()")
        val method = data.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.size == 1 }
            ?: throw NoSuchMethodException("LuckPerms data.$methodName(node)")
        method.isAccessible = true
        method.invoke(data, node)
    }

    private fun invokeQuietly(target: Any, name: String): Any? {
        return try {
            invokeMethod(target, name)
        } catch (_: Exception) {
            null
        }
    }

    private fun invokeMethod(target: Any, name: String, vararg args: Any): Any? {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
            ?: target.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    private fun findMethod(type: Class<*>, name: String, vararg params: Class<*>): Method? {
        return try {
            type.getMethod(name, *params)
        } catch (_: Exception) {
            null
        }
    }
}
