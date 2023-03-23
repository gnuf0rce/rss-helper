package io.github.gnuf0rce.mirai.rss

import net.mamoe.mirai.console.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.plugin.*
import net.mamoe.mirai.console.util.*

@PublishedApi
internal object RssHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "io.github.gnuf0rce.rss-helper", version = "1.2.6") {
        name("rss-helper")
        author("cssxsh")
    }
) {

    private val commands: List<Command> by services()
    private val config: List<PluginConfig> by services()
    private val data: List<PluginData> by services()

    override fun onEnable() {
        // XXX: mirai console version check
        check(SemVersion.parseRangeRequirement(">= 2.12.0-RC").test(MiraiConsole.version)) {
            "$name $version 需要 Mirai-Console 版本 >= 2.12.0，目前版本是 ${MiraiConsole.version}"
        }

        for (config in config) config.reload()
        for (data in data) data.reload()
        for (command in commands) command.register()

        RssSubscriber.start()
    }

    override fun onDisable() {
        for (command in commands) command.unregister()

        RssSubscriber.stop()
    }
}