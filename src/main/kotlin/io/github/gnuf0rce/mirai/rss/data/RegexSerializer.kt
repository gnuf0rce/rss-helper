package io.github.gnuf0rce.mirai.rss.data

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@PublishedApi
internal object RegexSerializer : KSerializer<Regex> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(Regex::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Regex = Regex(pattern = decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Regex) = encoder.encodeString(value = value.pattern)
}