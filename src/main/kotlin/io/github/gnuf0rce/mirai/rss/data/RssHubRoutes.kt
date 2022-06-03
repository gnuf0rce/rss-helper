package io.github.gnuf0rce.mirai.rss.data

import kotlinx.serialization.*

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