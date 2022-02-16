package io.github.gnuf0rce.mirai.plugin.command

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.time.*

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(OffsetDateTime::class.qualifiedName!!, PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(decoder.decodeLong()), ZoneId.systemDefault())
    }

    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeLong(value.toEpochSecond() * 1000)
}

@Serializable
data class BangumiRecent(
    @SerialName("acgdb_id")
    val acgId: String = "",
    @SerialName("cover")
    val cover: String,
    @SerialName("credit")
    val credit: String,
    @SerialName("endDate")
    @Serializable(OffsetDateTimeSerializer::class)
    val endDate: OffsetDateTime,
    @SerialName("icon")
    val icon: String,
    @SerialName("_id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("showOn")
    val showOn: Int,
    @SerialName("startDate")
    @Serializable(OffsetDateTimeSerializer::class)
    val startDate: OffsetDateTime,
    @SerialName("tag_id")
    val tagId: String
)

@Serializable
data class BangumiTag(
    @SerialName("found")
    val found: Boolean,
    @SerialName("success")
    val success: Boolean,
    @SerialName("tag")
    val tag: List<Tag> = emptyList()
) {
    @Serializable
    data class Tag(
        @SerialName("activity")
        val activity: Int,
        @SerialName("_id")
        val id: String,
        @SerialName("locale")
        val locale: Map<String, String>,
        @SerialName("name")
        val name: String,
        @SerialName("syn_lowercase")
        val lowercase: List<String>,
        @SerialName("synonyms")
        val synonyms: List<String>,
        @SerialName("type")
        val type: String
    )
}