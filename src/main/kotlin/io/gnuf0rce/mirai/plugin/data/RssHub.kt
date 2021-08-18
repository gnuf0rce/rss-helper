package io.gnuf0rce.mirai.plugin.data

import kotlinx.serialization.*
import net.mamoe.mirai.console.data.*

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