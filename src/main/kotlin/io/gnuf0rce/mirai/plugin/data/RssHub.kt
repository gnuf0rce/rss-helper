package io.gnuf0rce.mirai.plugin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.ReadOnlyPluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object RssHubConfig : ReadOnlyPluginConfig("RssHubConfig") {
    @ValueDescription("RssHub域名，默认为官方源")
    val domain by value("rsshub.app")
}

@Serializable
data class RssHubRoutes(
    @SerialName("data")
    val `data`: Map<String, Routes> = emptyMap(),
    @SerialName("message")
    val message: String,
    @SerialName("status")
    val status: Int
) {

    @Serializable
    data class Routes(
        @SerialName("routes")
        val routes: List<String>
    )
}