package com.lover.app.feature.main

import com.lover.app.core.model.LetterType
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class MainViewModelTest {
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun `instant letter accepts content without unlock date`() {
        MainViewModel.validateLetter("想你", "晚安", LetterType.INSTANT, null, today)
    }

    @Test
    fun `capsule requires future unlock date`() {
        assertThrows(IllegalArgumentException::class.java) {
            MainViewModel.validateLetter("未来", "给明年的你", LetterType.CAPSULE, null, today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MainViewModel.validateLetter("未来", "给明年的你", LetterType.CAPSULE, "2026-07-14", today)
        }
        MainViewModel.validateLetter(
            "未来",
            "给明年的你",
            LetterType.CAPSULE,
            "2027-07-14",
            today,
        )
    }

    @Test
    fun `letter rejects blank title`() {
        assertThrows(IllegalArgumentException::class.java) {
            MainViewModel.validateLetter("", "正文", LetterType.INSTANT, null, today)
        }
    }
}
