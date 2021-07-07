package io.gnuf0rce.mirai.plugin

import io.gnuf0rce.mirai.plugin.command.*
import io.gnuf0rce.mirai.plugin.data.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.PluginConfig
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin

object RssHelperPlugin : KotlinPlugin(
    JvmPluginDescription("io.github.gnuf0rce.rss-helper", "1.0.0") {
        name("rss-helper")
        author("cssxsh")
    }
) {

    private fun <T : PluginConfig> T.save() = loader.configStorage.store(this@RssHelperPlugin, this)

    override fun onEnable() {
        FeedRecordData.reload()
        SubscribeRecordData.reload()
        RssHubConfig.reload()
        RssHubConfig.save()
        HttpClientConfig.reload()
        HttpClientConfig.reload()

        RssBaseCommand.register()
        RssGithubCommand.register()
        RssMikanCommand.register()
        RssTestCommand.register()
        RssMiraiCommand.register()
        RssHubCommand.register()

        RssSubscriber.start()
    }

    override fun onDisable() {
        RssBaseCommand.unregister()
        RssGithubCommand.unregister()
        RssMikanCommand.unregister()
        RssTestCommand.unregister()
        RssMiraiCommand.unregister()
        RssHubCommand.unregister()

        RssSubscriber.stop()
    }
}