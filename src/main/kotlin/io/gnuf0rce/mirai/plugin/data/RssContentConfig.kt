package io.gnuf0rce.mirai.plugin.data

import net.mamoe.mirai.console.data.*

object RssContentConfig : ReadOnlyPluginConfig("RssContentConfig") {
    @ValueDescription("Content limit length")
    val limit: Int by value(1024)
}