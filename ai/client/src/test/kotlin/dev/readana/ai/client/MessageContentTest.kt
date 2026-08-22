package dev.readana.ai.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MessageContentTest {
    private val figure = ImageAttachment(
        id = "figure:1",
        mediaType = "image/png",
        bytes = byteArrayOf(1, 2, 3)
    )

    @Test
    fun `a turn with no picture stays a plain string`() {
        assertEquals(JsonPrimitive("hello"), MessageContent.anthropic("hello", emptyList()))
        assertEquals(JsonPrimitive("hello"), MessageContent.openAi("hello", emptyList()))
    }

    @Test
    fun `a picture in a format no provider decodes is left behind`() {
        val svg = ImageAttachment(id = "figure:2", mediaType = "image/svg+xml", bytes = byteArrayOf(9))

        assertEquals(JsonPrimitive("hello"), MessageContent.anthropic("hello", listOf(svg)))
        assertEquals(JsonPrimitive("hello"), MessageContent.openAi("hello", listOf(svg)))
    }

    @Test
    fun `a picture that came back empty is left behind`() {
        val empty = ImageAttachment(id = "figure:3", mediaType = "image/png", bytes = ByteArray(0))

        assertEquals(JsonPrimitive("hello"), MessageContent.anthropic("hello", listOf(empty)))
        assertEquals(JsonPrimitive("hello"), MessageContent.openAi("hello", listOf(empty)))
    }

    @Test
    fun `the sendable pictures go out even when an unusable one sits beside them`() {
        val svg = ImageAttachment(id = "figure:2", mediaType = "image/svg+xml", bytes = byteArrayOf(9))

        val parts = assertIsArray(MessageContent.openAi("what is this?", listOf(svg, figure)))

        assertEquals(2, parts.size)
        assertEquals("image_url", parts[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("what is this?", parts[1].jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `anthropic gets base64 image blocks before the text`() {
        val content = MessageContent.anthropic("what is this?", listOf(figure))

        val parts = assertIsArray(content)
        assertEquals("image", parts[0].jsonObject["type"]?.jsonPrimitive?.content)
        val source = parts[0].jsonObject.getValue("source").jsonObject
        assertEquals("base64", source["type"]?.jsonPrimitive?.content)
        assertEquals("image/png", source["media_type"]?.jsonPrimitive?.content)
        assertEquals("AQID", source["data"]?.jsonPrimitive?.content)
        assertEquals("what is this?", parts[1].jsonObject["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the openAi family gets a data url`() {
        val content = MessageContent.openAi("what is this?", listOf(figure))

        val parts = assertIsArray(content)
        val url = parts[0].jsonObject.getValue("image_url").jsonObject["url"]?.jsonPrimitive?.content
        assertEquals("data:image/png;base64,AQID", url)
        assertTrue(parts[1].jsonObject.containsKey("text"))
    }

    private fun assertIsArray(content: JsonElement): JsonArray = content.jsonArray
}
