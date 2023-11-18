package io.github.gnuf0rce.mirai.rss

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.plugin.jvm.*

@PublishedApi
internal object RssHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "io.github.gnuf0rce.rss-helper", version = "1.4.2") {
        name("rss-helper")
        author("cssxsh")
    }
) {

    private val commands: List<Command> by services()
    private val config: List<PluginConfig> by services()
    private val data: List<PluginData> by services()

    override fun onEnable() {
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