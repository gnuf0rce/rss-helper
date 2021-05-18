package io.gnuf0rce.mirai.plugin.data

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.PluginDataExtensions.mapKeys
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

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
    val histories by value(mutableMapOf<String, Long>())
}

object SubscribeRecordData : AutoSavePluginConfig("SubscribeRecordData") {
    @ValueDescription("订阅记录")
    val records by value<MutableMap<String, SubscribeRecord>>().mapKeys(::Url, Url::toString)
}