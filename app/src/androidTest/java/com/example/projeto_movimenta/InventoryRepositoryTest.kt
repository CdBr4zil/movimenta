package com.example.projeto_movimenta

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class InventoryRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migratesVersionOneWithoutLosingLotsOrLeadingZeros() = withDatabase { name ->
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("""CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
                quantity INTEGER NOT NULL CHECK(quantity >= 0), purchase_date TEXT NOT NULL,
                expiration_date TEXT NOT NULL, barcode TEXT NOT NULL
            )""")
            db.execSQL("INSERT INTO products VALUES (1, 'Nome antigo', 7, '10/11/2026', '20/11/2026', '000123')")
            db.execSQL("INSERT INTO products VALUES (4, 'Arroz', 0, '11/11/2026', '21/12/2026', '000123')")
            db.execSQL("INSERT INTO products VALUES (6, 'Feijão', 3, '09/11/2026', '22/12/2026', '000124')")
            db.version = 1
        }
        InventoryRepository(context, name).use { repository ->
            val stock = repository.snapshot()
            val product = stock.products.single { it.barcode == "000123" }
            assertEquals("Arroz", product.name)
            assertEquals(0, product.minimum)
            val lots = stock.lots.filter { it.productId == product.id }.sortedBy { it.id }
            assertEquals(listOf(1L, 4L), lots.map { it.id })
            assertEquals(listOf(7, 0), lots.map { it.quantity })
            assertEquals(listOf("10/11/2026", "11/11/2026"), lots.map { it.delivery })
            assertEquals(listOf("20/11/2026", "21/12/2026"), lots.map { it.expiration })
            val sector = stock.sectors.single()
            assertEquals("Sem setor", sector.name)
            assertTrue(stock.lots.all { it.sectorId == sector.id && it.unitsPerBox == 1 })
            assertEquals(3, repository.readableDatabase.version)
        }
    }

    @Test fun adjustsAndEditsOneLotAndPersistsStockAcrossReopening() = withDatabase { name ->
        var firstLotId = 0L
        InventoryRepository(context, name).use { repository ->
            val cold = repository.createSector("Câmara fria")
            val shelf = repository.createSector("Prateleira")
            assertEquals(cold, repository.createSector("  câmara fria  "))
            val product = Product(name = "Leite", barcode = "000123", minimum = 7)
            val first = StockLot(sectorId = cold.id, quantity = 24, unitsPerBox = 12, delivery = "11/11/2026", expiration = "20/11/2026")
            firstLotId = repository.register(product, first)
            val stock = repository.snapshot()
            val savedProduct = stock.products.single()
            val savedLot = stock.lots.single()
            repository.register(savedProduct, first.copy(sectorId = shelf.id, quantity = 5, expiration = "25/11/2026"))
            repository.editLot(savedLot.copy(expiration = "30/11/2026", unitsPerBox = 10))
            assertEquals(24, repository.snapshot().lots.single { it.id == firstLotId }.quantity)
            assertTrue(repository.adjust(firstLotId, 2, true, StockDirection.OUT))
            assertFalse(repository.adjust(firstLotId, 1, true, StockDirection.OUT))
            assertEquals(9L, repository.snapshot().summarize("11/11/2026").products.single().total)
        }
        InventoryRepository(context, name).use { repository ->
            val lots = repository.snapshot().lots
            val first = lots.single { it.id == firstLotId }
            assertEquals(4, first.quantity)
            assertEquals("30/11/2026", first.expiration)
            assertEquals("25/11/2026", lots.single { it.id != firstLotId }.expiration)
        }
    }

    @Test fun migratesVersionTwoAndDeletesOnlyTheConfirmedProduct() = withDatabase { name ->
        val bytes = byteArrayOf(1, 2, 3)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, barcode TEXT UNIQUE, minimum INTEGER NOT NULL, photo_revision INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sectors (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, name_key TEXT NOT NULL UNIQUE)")
            db.execSQL("""CREATE TABLE lots (
                id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES products(id),
                sector_id INTEGER NOT NULL REFERENCES sectors(id), quantity INTEGER NOT NULL,
                units_per_box INTEGER NOT NULL, delivery TEXT NOT NULL, expiration TEXT, qr_code TEXT
            )""")
            db.execSQL("CREATE INDEX lots_by_product ON lots(product_id)")
            db.execSQL("CREATE INDEX lots_by_sector ON lots(sector_id)")
            db.execSQL("CREATE TABLE photos (product_id INTEGER PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE, bytes BLOB NOT NULL, source_code TEXT)")
            db.execSQL("INSERT INTO products VALUES (4, 'Arroz', '000123', 10, 1), (5, 'Feijão', '000124', 5, 0)")
            db.execSQL("INSERT INTO sectors VALUES (8, 'Depósito', 'depósito')")
            db.execSQL("""INSERT INTO lots VALUES
                (9, 4, 8, 12, 6, '01/11/2026', '30/11/2026', 'CAIXA-1'),
                (10, 5, 8, 3, 1, '01/11/2026', NULL, 'CAIXA-2')""")
            db.execSQL("INSERT INTO photos VALUES (4, ?, '000123')", arrayOf(bytes))
            db.version = 2
        }
        InventoryRepository(context, name).use { repository ->
            assertEquals(3, repository.readableDatabase.version)
            val migrated = repository.snapshot()
            val arroz = requireNotNull(migrated.findByBarcode("000123"))
            assertEquals(4L, arroz.id)
            assertNull(arroz.brand)
            assertEquals(StockLot(9, 4, 8, 12, 6, "01/11/2026", "30/11/2026"), migrated.lots.single { it.id == 9L })
            assertArrayEquals(bytes, repository.photo(arroz.id)?.bytes)
            assertEquals("000123", repository.photo(arroz.id)?.sourceCode)
            repository.readableDatabase.rawQuery("SELECT * FROM lots LIMIT 0", null).use {
                assertEquals(-1, it.getColumnIndex("qr_code"))
            }
            repository.editProduct(arroz.copy(brand = "Marca teste"))
            assertEquals("Marca teste", repository.snapshot().findByBarcode("000123")?.brand)
            repository.removeProduct(arroz.id)
        }
        InventoryRepository(context, name).use { repository ->
            val remaining = repository.snapshot()
            assertEquals(listOf(5L), remaining.products.map { it.id })
            assertEquals(listOf(10L), remaining.lots.map { it.id })
            assertEquals(listOf(8L), remaining.sectors.map { it.id })
            assertNull(repository.photo(4))
        }
    }

    private fun withDatabase(test: (String) -> Unit) {
        val name = "movimenta-test-${UUID.randomUUID()}.db"
        try {
            test(name)
        } finally {
            context.deleteDatabase(name)
        }
    }
}
