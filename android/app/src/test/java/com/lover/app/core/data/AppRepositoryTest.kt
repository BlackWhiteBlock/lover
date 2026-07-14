package com.lover.app.core.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRepositoryTest {
    @Test
    fun `capsule date converts to local midnight with offset`() {
        assertEquals(
            "2027-02-14T00:00:00+08:00",
            AppRepository.localMidnightWithOffset(
                "2027-02-14",
                ZoneId.of("Asia/Shanghai"),
            ),
        )
    }

    @Test
    fun `midnight conversion observes daylight saving offset`() {
        assertEquals(
            "2027-07-14T00:00:00-04:00",
            AppRepository.localMidnightWithOffset(
                "2027-07-14",
                ZoneId.of("America/New_York"),
            ),
        )
    }
}
