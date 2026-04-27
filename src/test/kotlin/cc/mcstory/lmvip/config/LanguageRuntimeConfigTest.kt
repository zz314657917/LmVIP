package cc.mcstory.lmvip.config

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageRuntimeConfigTest {

    @Test
    fun `language message replaces placeholders and applies prefix`() {
        val language = LanguageRuntimeConfig(
            prefix = "&6[LmVIP]&r ",
            messages = mapOf("claim.success" to "&a%type%领取成功"),
            lists = emptyMap()
        )

        assertEquals("§6[LmVIP]§r §a日礼包领取成功", language.message("claim.success", "type" to "日礼包"))
    }

    @Test
    fun `language list supports placeholders`() {
        val language = LanguageRuntimeConfig(
            prefix = "",
            messages = emptyMap(),
            lists = mapOf("help.vip" to listOf("&6/vip", "&7等级: %level%"))
        )

        assertEquals(listOf("§6/vip", "§7等级: VIP3"), language.list("help.vip", "level" to "VIP3"))
    }
}
