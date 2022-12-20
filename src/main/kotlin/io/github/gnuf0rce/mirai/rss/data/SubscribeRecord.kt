package io.github.gnuf0rce.mirai.rss.data

import kotlinx.serialization.*

@Serializable
public data class SubscribeRecord(
    @SerialName("name")
    val name: String = "",
    @SerialName("contact")
    val contacts: Set<Long> = emptySet(),
    @SerialName("interval")
    val interval: Int = 10
)