package com.lover.app.feature.main

import com.lover.app.core.model.LetterType
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class MainViewModelTest {
    private val today = LocalDate.parse("2026-07-14")

    @Test
    fun `instant letter allowed when linked`() {
        MainViewModel.validateLetter("想你", "晚安", LetterType.INSTANT, null, linked = true, today = today)
    }

    @Test
    fun `instant letter blocked when unlinked`() {
        assertThrows(IllegalArgumentException::class.java) {
            MainViewModel.validateLetter("想你", "晚安", LetterType.INSTANT, null, linked = false, today = today)
        }
    }

    @Test
    fun `capsule requires date or bind unlock`() {
        assertThrows(IllegalArgumentException::class.java) {
            MainViewModel.validateLetter("未来", "给明年的你", LetterType.CAPSULE, null, today = today)
        }
        MainViewModel.validateLetter(
            "未来",
            "给明年的你",
            LetterType.CAPSULE,
            unlockDate = null,
            unlockOnPartnerBind = true,
            today = today,
        )
        MainViewModel.validateLetter(
            title = "未来",
            content = "给明年的你",
            type = LetterType.CAPSULE,
            unlockDate = "2026-08-14",
            today = today,
        )
    }

    @Test
    fun `title required`() {
        assertThrows(IllegalArgumentException::class.java) {
            MainViewModel.validateLetter("", "正文", LetterType.INSTANT, null, linked = true, today = today)
        }
    }
}
