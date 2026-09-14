package com.b1g.player.core.xtream

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Xtream panels are not consistent about JSON types: `stream_id` arrives as a number
 * on one server and a string on the next, `rating` may be `""`, `"0"`, `0` or `7.4`,
 * and absent values show up as `null`, `""` or the literal string `"null"`.
 * These serializers accept all of those shapes so a single odd field cannot fail the
 * whole response.
 */
private fun JsonPrimitive.normalisedOrNull(): String? =
    content.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

private fun Decoder.readPrimitive(): JsonPrimitive? {
    val input = this as? JsonDecoder ?: return JsonPrimitive(decodeString())
    return when (val element = input.decodeJsonElement()) {
        is JsonNull -> null
        is JsonPrimitive -> element
        else -> null // an object/array where a scalar was expected is treated as absent
    }
}

object FlexibleString : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? = decoder.readPrimitive()?.normalisedOrNull()

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

object FlexibleInt : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? {
        val text = decoder.readPrimitive()?.normalisedOrNull() ?: return null
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt()
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

object FlexibleLong : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG).nullable

    override fun deserialize(decoder: Decoder): Long? {
        val text = decoder.readPrimitive()?.normalisedOrNull() ?: return null
        return text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong()
    }

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

object FlexibleDouble : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? =
        decoder.readPrimitive()?.normalisedOrNull()?.toDoubleOrNull()

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

/** `auth`, `tv_archive` and friends come back as `1`, `"1"` or `true`. */
object FlexibleBoolean : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN).nullable

    override fun deserialize(decoder: Decoder): Boolean? {
        val text = decoder.readPrimitive()?.normalisedOrNull() ?: return null
        return when (text.lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}
