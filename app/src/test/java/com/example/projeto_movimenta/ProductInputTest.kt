package com.example.projeto_movimenta

import org.junit.Assert.*
import org.junit.Test

class ProductInputTest {
    @Test fun formatsProgressiveDatesAndLimitsToEightDigits() {
        val expected = listOf("", "1", "10", "10/1", "10/11", "10/11/2", "10/11/20", "10/11/202", "10/11/2026")
        for (length in 0..8) assertEquals(expected[length], DateInput.formatted("10112026".take(length)))
        assertEquals("10112026", DateInput.digits("10/11/202612"))
        assertEquals("10/11/2026", DateInput.formatted("10.11.2026"))
    }

    @Test fun cursorMappingsStayWithinBoundsAndRoundTripAcrossSeparators() {
        for (length in 0..8) {
            val displayLength = DateInput.formatted("10112026".take(length)).length
            for (offset in 0..length) {
                val mapped = DateInput.originalToTransformed(offset, length)
                assertTrue(mapped in 0..displayLength)
                assertEquals(offset, DateInput.transformedToOriginal(mapped, length))
            }
            val mappedOffsets = (0..displayLength).map { DateInput.transformedToOriginal(it, length) }
            assertTrue(mappedOffsets.all { it in 0..length })
            assertEquals(mappedOffsets.sorted(), mappedOffsets)
        }
    }

    @Test fun barcodeRemovesPunctuationAndPreservesLeadingZeros() {
        assertEquals("0012345678901", numericInput("00.123 456-78901"))
        assertEquals("", numericInput("abc/.-"))
    }

    @Test fun formattedDatesStillUseCalendarValidation() {
        assertTrue(StockRules.dateErrors(DateInput.formatted("10112026"), DateInput.formatted("10122026")).isEmpty())
        assertNull(StockRules.date(DateInput.formatted("31022026")))
        assertNull(StockRules.date("29/02/2025"))
        assertNotNull(StockRules.date("29/02/2024"))
    }
}
