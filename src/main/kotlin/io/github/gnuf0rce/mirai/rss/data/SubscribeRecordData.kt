package io.github.gnuf0rce.mirai.rss.data

import io.ktor.http.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.mamoe.mirai.console.data.*

object SubscribeRecordData : AutoSavePluginConfig("SubscribeRecordData") {
    override val serializersModule: SerializersModule = SerializersModule {
        contextual(UrlSerializer)
    }

    @ValueDescription("订阅记录")
    val records: MutableMap<Url, SubscribeRecord> by value()
}