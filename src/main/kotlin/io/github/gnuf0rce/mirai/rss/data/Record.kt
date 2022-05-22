package io.github.gnuf0rce.mirai.rss.data

import io.ktor.http.*
import kotlinx.serialization.*
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.data.PluginDataExtensions.mapKeys

@Serializable
data class SubscribeRecord(
    @SerialName("name")
    val name: String = "",
    @SerialName("contact")
    val contacts: Set<Long> = emptySet(),
    @SerialName("interval")
    val interval: Int = 10
)

object FeedRecordData : AutoSavePluginData("FeedRecordData") {
    @ValueDescription("流记录")
    val histories by value<MutableMap<String, Double>>()
}

object SubscribeRecordData : AutoSavePluginConfig("SubscribeRecordData") {
    @ValueDescription("订阅记录")
    val records by value<MutableMap<String, SubscribeRecord>>().mapKeys(::Url, Url::toString)
}