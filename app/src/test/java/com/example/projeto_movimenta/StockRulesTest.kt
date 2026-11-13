package com.example.projeto_movimenta

import org.junit.Assert.*
import org.junit.Test

class StockRulesTest {
    @Test fun barcodeLookupUsesTheFullCodeIncludingZeros() {
        val first = Product(id = 1, name = "Produto A", barcode = "000123")
        val second = Product(id = 2, name = "Produto B", barcode = "000124")
        val inventory = Inventory(products = listOf(first, second))
        assertEquals(first, inventory.findByBarcode("000123"))
        assertEquals(second, inventory.findByBarcode("000124"))
        assertNull(inventory.findByBarcode("00012"))
        assertNull(inventory.findByBarcode("123"))
        assertNull(inventory.findByBarcode("000125"))
    }
    @Test fun convertsBoxesAndRejectsInvalidAdjustments() {
        assertEquals(24, StockRules.units("2", true, 12))
        assertEquals(2, StockRules.units("2", false, 12))
        assertNull(StockRules.units("0", true, 12))
        assertNull(StockRules.units("-1", false, 12))
        assertNull(StockRules.units("+1", false, 12))
        assertNull(StockRules.units("1,5", false, 12))
        assertNull(StockRules.units(Int.MAX_VALUE.toString(), true, 2))
        assertEquals(0, StockRules.adjusted(24, 24, StockDirection.OUT))
        assertNull(StockRules.adjusted(23, 24, StockDirection.OUT))
        assertNull(StockRules.adjusted(Int.MAX_VALUE, 1, StockDirection.IN))
    }

    @Test fun summarizesByProductAcrossSectorsAndUsesInclusiveDates() {
        val product = Product(id = 1, name = "Arroz", barcode = "000123", minimum = 8)
        val lots = listOf(
            StockLot(1, 1, 1, 5, 1, "01/11/2026", "11/11/2026"),
            StockLot(2, 1, 2, 2, 1, "01/11/2026", "18/11/2026"),
            StockLot(3, 1, 2, 1, 1, "01/11/2026", null),
            StockLot(4, 1, 1, 9, 1, "01/11/2026", "10/11/2026"),
            StockLot(5, 1, 1, 0, 1, "01/11/2026", "12/11/2026"),
        )
        val summary = Inventory(listOf(product), lots, emptyList()).summarize("11/11/2026")
        val row = summary.products.single()
        assertEquals(17L, row.total)
        assertEquals(8L, row.available)
        assertEquals(listOf(row), summary.toBuy)
        assertEquals(listOf(1L, 2L), summary.nearExpiry.map { it.id })
        assertEquals(listOf(4L), summary.expired.map { it.id })
        assertTrue(StockRules.dateErrors("11/11/2026", null).isEmpty())
        assertTrue(StockRules.dateErrors("11/11/2026", "10/11/2026").containsKey("expiration"))
    }
}
