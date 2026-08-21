package dev.readana.ai.client

import java.util.Base64
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Both dialects let a user turn be either a plain string or a list of parts, and only the list form
 * can carry pictures. Building the JSON by hand keeps one shape for the common text-only case —
 * which is what providers' prompt caches were measured on — and only widens it when a figure is
 * actually attached.
 */
internal object MessageContent {
    /** Anthropic: `[{"type":"image","source":{...}}, {"type":"text","text":"…"}]`. */
    fun anthropic(text: String, images: List<ImageAttachment>): JsonElement {
        if (images.isEmpty()) return JsonPrimitive(text)
        return buildJsonArray {
            images.forEach { image ->
                add(
                    buildJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "base64")
                            put("media_type", image.mediaType)
                            put("data", image.bytes.base64())
                        }
                    }
                )
            }
            add(textPart(text))
        }
    }

    /** OpenAI-compatible: `[{"type":"image_url","image_url":{"url":"data:…;base64,…"}}, …]`. */
    fun openAi(text: String, images: List<ImageAttachment>): JsonElement {
        if (images.isEmpty()) return JsonPrimitive(text)
        return buildJsonArray {
            images.forEach { image ->
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:" + image.mediaType + ";base64," + image.bytes.base64())
                        }
                    }
                )
            }
            add(textPart(text))
        }
    }

    private fun textPart(text: String): JsonElement = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)
}
