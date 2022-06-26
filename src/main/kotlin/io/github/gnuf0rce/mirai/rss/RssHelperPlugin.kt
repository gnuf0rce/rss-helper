package io.github.gnuf0rce.mirai.rss

import io.github.gnuf0rce.mirai.rss.command.*
import io.github.gnuf0rce.mirai.rss.data.*
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.plugin.*
import net.mamoe.mirai.console.util.*

object RssHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "io.github.gnuf0rce.rss-helper", version = "1.2.1") {
        name("rss-helper")
        author("cssxsh")
    }
) {

    override fun onEnable() {
        // XXX: mirai console version check
        check(SemVersion.parseRangeRequirement(">= 2.12.0-RC").test(MiraiConsole.version)) {
            "$name $version 需要 Mirai-Console 版本 >= 2.12.0，目前版本是 ${MiraiConsole.version}"
        }

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