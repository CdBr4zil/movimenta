package com.example.projeto_movimenta

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import java.util.Locale

data class ProductPhoto(val bytes: ByteArray, val sourceCode: String? = null)

sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange
    data class Replace(val photo: ProductPhoto) : PhotoChange
}

class InventoryRepository(context: Context, name: String = "movimenta.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 3) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE products (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL, barcode TEXT UNIQUE, brand TEXT,
            minimum INTEGER NOT NULL DEFAULT 0 CHECK(minimum BETWEEN 0 AND 2147483647),
            photo_revision INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("""CREATE TABLE sectors (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, name_key TEXT NOT NULL UNIQUE
        )""")
        createLots(db)
        createLotIndexes(db)
        db.execSQL("""CREATE TABLE photos (
            product_id INTEGER PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
            bytes BLOB NOT NULL CHECK(length(bytes) BETWEEN 1 AND 262144), source_code TEXT
        )""")
    }

    private fun createLots(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE lots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_id INTEGER NOT NULL REFERENCES products(id),
            sector_id INTEGER NOT NULL REFERENCES sectors(id),
            quantity INTEGER NOT NULL CHECK(quantity BETWEEN 0 AND 2147483647),
            units_per_box INTEGER NOT NULL CHECK(units_per_box BETWEEN 1 AND 2147483647),
            delivery TEXT NOT NULL, expiration TEXT
        )""")
    }

    private fun createLotIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX lots_by_product ON lots(product_id)")
        db.execSQL("CREATE INDEX lots_by_sector ON lots(sector_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1..2 && newVersion == 3)
        if (oldVersion == 2) {
            db.execSQL("ALTER TABLE products ADD COLUMN brand TEXT")
            db.execSQL("ALTER TABLE lots RENAME TO old_lots")
            createLots(db)
            val columns = "id, product_id, sector_id, quantity, units_per_box, delivery, expiration"
            db.execSQL("INSERT INTO lots ($columns) SELECT $columns FROM old_lots")
            db.execSQL("""DELETE FROM sqlite_sequence WHERE name = 'lots'
                AND EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'old_lots')""")
            db.execSQL("UPDATE sqlite_sequence SET name = 'lots' WHERE name = 'old_lots'")
            db.execSQL("DROP TABLE old_lots")
            createLotIndexes(db)
            return
        }
        // SQLiteOpenHelper wraps the upgrade and schema version change in one transaction.
        db.execSQL("ALTER TABLE products RENAME TO old_products")
        onCreate(db)
        db.execSQL("INSERT INTO sectors (name, name_key) VALUES ('Sem setor', 'sem setor')")
        db.execSQL("""INSERT INTO products (id, name, barcode)
            SELECT id, name, barcode FROM old_products
            WHERE id IN (SELECT MAX(id) FROM old_products GROUP BY barcode)
        """)
        db.execSQL("""INSERT INTO lots (id, product_id, sector_id, quantity, units_per_box, delivery, expiration)
            SELECT old.id, products.id, sectors.id, old.quantity, 1, old.purchase_date, old.expiration_date
            FROM old_products AS old JOIN products ON old.barcode = products.barcode
            JOIN sectors ON sectors.name_key = 'sem setor'
        """)
        db.execSQL("DROP TABLE old_products")
    }

    fun snapshot(): Inventory = readableDatabase.transaction(exclusive = false) {
        val products = rawQuery("""SELECT products.id, name, barcode, minimum,
            CASE WHEN photos.product_id IS NOT NULL THEN photo_revision END, brand
            FROM products LEFT JOIN photos ON products.id = photos.product_id
            ORDER BY name COLLATE NOCASE, products.id""", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Product(
                    cursor.getLong(0), cursor.getString(1), cursor.getStringOrNull(2),
                    cursor.getInt(3), cursor.getLongOrNull(4), cursor.getStringOrNull(5),
                ))
            }
        }
        val lots = rawQuery("""SELECT id, product_id, sector_id, quantity, units_per_box,
            delivery, expiration FROM lots ORDER BY id DESC""", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(StockLot(
                    cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3),
                    cursor.getInt(4), cursor.getString(5), cursor.getStringOrNull(6),
                ))
            }
        }
        val sectors = rawQuery("SELECT id, name FROM sectors ORDER BY name_key", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Sector(cursor.getLong(0), cursor.getString(1)))
            }
        }
        Inventory(products, lots, sectors)
    }

    fun photo(productId: Long): ProductPhoto? =
        readableDatabase.rawQuery("SELECT bytes, source_code FROM photos WHERE product_id = ?", args(productId))
            .use { if (it.moveToFirst()) ProductPhoto(it.getBlob(0), it.getStringOrNull(1)) else null }

    fun createSector(name: String): Sector = writableDatabase.transaction {
        val label = name.trim()
        require(label.isNotEmpty())
        val key = label.lowercase(Locale.ROOT)
        insertWithOnConflict("sectors", null, ContentValues().apply {
            put("name", label)
            put("name_key", key)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        rawQuery("SELECT id, name FROM sectors WHERE name_key = ?", arrayOf(key)).use {
            check(it.moveToFirst())
            Sector(it.getLong(0), it.getString(1))
        }
    }

    fun register(product: Product, lot: StockLot, photo: PhotoChange = PhotoChange.Keep): Long =
        writableDatabase.transaction {
            require(lot.quantity > 0)
            val productId = if (product.id == 0L) {
                insertOrThrow("products", null, product.values()).also { savePhoto(it, photo) }
            } else {
                product.id
            }
            insertOrThrow("lots", null, lot.values().apply {
                put("product_id", productId)
                put("quantity", lot.quantity)
            })
        }

    fun editProduct(product: Product, photo: PhotoChange = PhotoChange.Keep) {
        require(product.id > 0)
        writableDatabase.transaction {
            check(update("products", product.values(), "id = ?", args(product.id)) == 1)
            savePhoto(product.id, photo)
        }
    }

    fun editLot(lot: StockLot) {
        check(writableDatabase.update("lots", lot.values(), "id = ? AND product_id = ?", args(lot.id, lot.productId)) == 1)
    }

    fun removeProduct(id: Long) {
        writableDatabase.transaction {
            delete("lots", "product_id = ?", args(id))
            check(delete("products", "id = ?", args(id)) == 1)
        }
    }

    fun adjust(lotId: Long, amount: Int, boxes: Boolean, direction: StockDirection): Boolean =
        writableDatabase.transaction {
            rawQuery("SELECT quantity, units_per_box FROM lots WHERE id = ?", args(lotId)).use { cursor ->
                if (!cursor.moveToFirst()) return@transaction false
                val units = StockRules.units(amount.toString(), boxes, cursor.getInt(1)) ?: return@transaction false
                val next = StockRules.adjusted(cursor.getInt(0), units, direction) ?: return@transaction false
                update("lots", ContentValues().apply { put("quantity", next) }, "id = ?", args(lotId)) == 1
            }
        }

    fun removeLot(id: Long) {
        check(writableDatabase.delete("lots", "id = ?", args(id)) == 1)
    }

    private fun SQLiteDatabase.savePhoto(productId: Long, change: PhotoChange) {
        when (change) {
            PhotoChange.Keep -> return
            PhotoChange.Remove -> delete("photos", "product_id = ?", args(productId))
            is PhotoChange.Replace -> insertWithOnConflict("photos", null, ContentValues().apply {
                put("product_id", productId)
                put("bytes", change.photo.bytes)
                put("source_code", change.photo.sourceCode)
            }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
        }
        execSQL("UPDATE products SET photo_revision = photo_revision + 1 WHERE id = ?", arrayOf(productId))
    }

    private fun Product.values(): ContentValues {
        require(name.isNotBlank() && minimum >= 0 && (barcode == null || StockRules.isBarcode(barcode)))
        return ContentValues().apply {
            put("name", name.trim())
            put("barcode", barcode)
            put("minimum", minimum)
            put("brand", brand?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    private fun StockLot.values(): ContentValues {
        require(unitsPerBox > 0 && StockRules.dateErrors(delivery, expiration).isEmpty())
        return ContentValues().apply {
            put("sector_id", sectorId)
            put("units_per_box", unitsPerBox)
            put("delivery", delivery)
            put("expiration", expiration)
        }
    }

    private fun args(vararg ids: Long): Array<String> = Array(ids.size) { ids[it].toString() }
}
