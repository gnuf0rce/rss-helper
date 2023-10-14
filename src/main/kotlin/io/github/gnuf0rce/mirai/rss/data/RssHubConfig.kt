package io.github.gnuf0rce.mirai.rss.data

import net.mamoe.mirai.console.data.*

@PublishedApi
internal object RssHubConfig : ReadOnlyPluginConfig(saveName = "RssHubConfig") {
    @ValueDescription("RssHub域名，默认为官方源")
    val host by value("https://rsshub.app:443")
}