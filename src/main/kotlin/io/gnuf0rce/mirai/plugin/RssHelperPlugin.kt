package io.gnuf0rce.mirai.plugin

import io.gnuf0rce.mirai.plugin.command.*
import io.gnuf0rce.mirai.plugin.data.FeedRecordData
import io.gnuf0rce.mirai.plugin.data.SubscribeRecordData
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin

object RssHelperPlugin : KotlinPlugin(
    JvmPluginDescription("io.github.gnuf0rce.rss-helper", "1.0.0-dev-1") {
        name("rss-helper")
        author("cssxsh")
    }
) {

    override fun onEnable() {
        FeedRecordData.reload()
        SubscribeRecordData.reload()

        RssBaseCommand.register()
        RssGithubCommand.register()
        RssMikanCommand.register()
        RssTestCommand.register()

        RssSubscriber.start()
    }

    override fun onDisable() {
        RssBaseCommand.unregister()
        RssGithubCommand.unregister()
        RssMikanCommand.unregister()
        RssTestCommand.unregister()

        RssSubscriber.stop()
    }
}