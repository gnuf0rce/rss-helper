package io.github.gnuf0rce.mirai.rss.data

import net.mamoe.mirai.console.data.*

object FeedRecordData : AutoSavePluginData("FeedRecordData") {
    @ValueDescription("流记录")
    val histories by value<MutableMap<String, Double>>()
}