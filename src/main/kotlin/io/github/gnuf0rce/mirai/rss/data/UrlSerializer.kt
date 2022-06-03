package io.github.gnuf0rce.mirai.rss.data

import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

internal object UrlSerializer : KSerializer<Url> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(Url::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Url = Url(urlString = decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Url) = encoder.encodeString(value = value.toString())
}