package com.example.projeto_movimenta

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Product(
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val minimum: Int = 0,
    val photoVersion: Long? = null,
    val brand: String? = null,
)

data class Sector(val id: Long, val name: String)

data class StockLot(
    val id: Long = 0,
    val productId: Long = 0,
    val sectorId: Long,
    val quantity: Int,
    val unitsPerBox: Int,
    val delivery: String,
    val expiration: String?,
)

data class Inventory(
    val products: List<Product> = emptyList(),
    val lots: List<StockLot> = emptyList(),
    val sectors: List<Sector> = emptyList(),
) {
    fun findByBarcode(code: String): Product? = products.find { it.barcode == code }

    fun summarize(today: String): StockSummary {
        val firstDay = requireNotNull(StockRules.date(today))
        val lastDay = Calendar.getInstance(StockRules.utc).apply {
            time = firstDay
            add(Calendar.DAY_OF_YEAR, 7)
        }.time
        val dates = lots.associate { it.id to it.expiration?.let(StockRules::date) }
        val expired = lots.filter { it.quantity > 0 && dates[it.id]?.before(firstDay) == true }
            .sortedBy { dates[it.id] }
        val expiredIds = expired.mapTo(mutableSetOf()) { it.id }
        val nearExpiry = lots.filter { lot ->
            val date = dates[lot.id]
            lot.quantity > 0 && date != null && date in firstDay..lastDay
        }.sortedBy { dates[it.id] }
        val lotsByProduct = lots.groupBy { it.productId }
        val totals = products.map { product ->
            val productLots = lotsByProduct[product.id].orEmpty()
            ProductStock(
                product,
                total = productLots.sumOf { it.quantity.toLong() },
                available = productLots.sumOf { if (it.id in expiredIds) 0L else it.quantity.toLong() },
            )
        }
        return StockSummary(totals, totals.filter { it.available <= it.product.minimum }
            .sortedByDescending { it.product.minimum - it.available }, nearExpiry, expired)
    }
}

data class ProductStock(val product: Product, val total: Long, val available: Long)

data class StockSummary(
    val products: List<ProductStock>,
    val toBuy: List<ProductStock>,
    val nearExpiry: List<StockLot>,
    val expired: List<StockLot>,
)

enum class StockDirection { IN, OUT }

object StockRules {
    internal val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val datePattern = Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}")
    private val barcodePattern = Regex("[0-9]+")

    fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date())

    fun date(value: String): Date? {
        if (!datePattern.matches(value)) return null
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = utc
            isLenient = false
        }
        return try { format.parse(value) } catch (_: java.text.ParseException) { null }
    }

    fun dateErrors(delivery: String, expiration: String?): Map<String, String> = buildMap {
        val delivered = date(delivery)
        if (delivered == null) put("delivery", "Informe uma data válida (dd/mm/aaaa).")
        if (expiration != null) {
            val expires = date(expiration)
            if (expires == null) put("expiration", "Informe uma data válida (dd/mm/aaaa).")
            else if (delivered != null && expires.before(delivered)) {
                put("expiration", "A validade não pode ser anterior à entrega.")
            }
        }
    }

    fun isBarcode(value: String): Boolean = barcodePattern.matches(value)

    fun integer(input: String): Int? = input.takeIf { barcodePattern.matches(it) }?.toIntOrNull()

    fun units(input: String, boxes: Boolean, unitsPerBox: Int): Int? {
        val amount = integer(input)?.takeIf { it > 0 } ?: return null
        val total = if (boxes) amount.toLong() * unitsPerBox else amount.toLong()
        return total.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }

    fun adjusted(current: Int, units: Int, direction: StockDirection): Int? {
        if (units <= 0) return null
        val next = when (direction) {
            StockDirection.IN -> current.toLong() + units
            StockDirection.OUT -> current.toLong() - units
        }
        return next.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }
}
