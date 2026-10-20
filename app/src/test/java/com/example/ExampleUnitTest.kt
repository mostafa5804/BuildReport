package com.example

import com.example.data.Converters
import com.example.data.model.DailyReport
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun dailyReport_reportNumberColumn_hasDefaultValue100ForRoomMigration4To5() {
        val report = DailyReport()
        assertEquals(100, report.reportNumber)
        val field = DailyReport::class.java.getDeclaredField("reportNumber")
        assertNotNull(field)
    }

    @Test
    fun converters_handleInvalidOrLegacyJson_withoutCrash() {
        val converters = Converters()
        assertTrue(converters.toTaskList("invalid_json").isEmpty())
        assertTrue(converters.toMachineryList("").isEmpty())
        assertTrue(converters.toManpowerList("null").isEmpty())
        assertTrue(converters.toMaterialList("{bad}").isEmpty())
        assertTrue(converters.toLegalPermitList("[").isEmpty())
        assertTrue(converters.toStringList("not a json").isEmpty())
    }
}
