package io.gnuf0rce.mirai.plugin.command

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(OffsetDateTime::class.qualifiedName!!, PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): OffsetDateTime = Instant.ofEpochMilli(decoder.decodeLong())
        .atOffset(ZoneOffset.UTC).withOffsetSameLocal(OffsetDateTime.now().offset)

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