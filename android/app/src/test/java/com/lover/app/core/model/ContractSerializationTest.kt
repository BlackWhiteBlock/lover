package com.lover.app.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ContractSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `media request uses backend enum and field names`() {
        val encoded = json.encodeToString(
            CreateMediaRequest(
                type = MediaType.IMAGE,
                assetId = "72f44cc4-ae70-43a0-bcf8-b18e3d11a882",
                caption = "今天",
                mediaDate = "2026-07-14",
            ),
        )
        assertEquals(
            """{"type":"image","assetId":"72f44cc4-ae70-43a0-bcf8-b18e3d11a882","caption":"今天","mediaDate":"2026-07-14"}""",
            encoded,
        )
    }

    @Test
    fun `optional login nickname is omitted instead of null`() {
        val encoded = json.encodeToString(LoginRequest("13800138000", "123456"))
        assertFalse(encoded.contains("nickname"))
    }

    @Test
    fun `locked capsule decodes without protected content`() {
        val letter = json.decodeFromString<Letter>(
            """{
                "id":"letter-id",
                "senderId":"user-id",
                "senderNickname":"小恋",
                "title":"未来",
                "type":"capsule",
                "unlockAt":"2027-07-14T00:00:00+08:00",
                "isUnlocked":false,
                "createdAt":"2026-07-14T12:00:00.000Z"
            }""".trimIndent(),
        )
        assertFalse(letter.isUnlocked)
        assertNull(letter.content)
    }
}
