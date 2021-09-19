package io.github.gnuf0rce.mirai.plugin

import io.github.gnuf0rce.mirai.plugin.command.*
import io.github.gnuf0rce.mirai.plugin.data.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.plugin.jvm.*

object RssHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "io.github.gnuf0rce.rss-helper", version = "1.0.4") {
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
        HttpClientConfig.save()
        RssContentConfig.reload()
        RssContentConfig.save()

        RssBaseCommand.register()
        RssGithubCommand.register()
        RssMikanCommand.register()
        RssTestCommand.register()
        RssMiraiCommand.register()
        RssHubCommand.register()
        RssMoeCommand.register()

        RssSubscriber.start()
    }

    override fun onDisable() {
        RssBaseCommand.unregister()
        RssGithubCommand.unregister()
        RssMikanCommand.unregister()
        RssTestCommand.unregister()
        RssMiraiCommand.unregister()
        RssHubCommand.unregister()
        RssMoeCommand.unregister()

        RssSubscriber.stop()
    }
}