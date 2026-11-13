package com.example.projeto_movimenta

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal data class FoodSuggestion(val code: String, val name: String?, val image: String?, val brand: String?)

internal object OpenFoodFacts {
    private const val host = "world.openfoodfacts.org"
    private const val userAgent = "Movimenta/1.1 (Android; com.example.projeto_movimenta)"
    private val imageHosts = setOf("images.openfoodfacts.org", "static.openfoodfacts.org")

    fun lookup(barcode: String): FoodSuggestion? {
        require(StockRules.isBarcode(barcode))
        val fields = "code,product_name,product_name_pt,brands,lang,selected_images,image_front_small_url,image_front_url"
        val url = URL("https://$host/api/v3/product/$barcode?cc=br&lc=pt&fields=$fields")
        return request(url, "application/json") { connection ->
            if (connection.responseCode == 404) return@request null
            ensureSuccess(connection)
            val json = connection.inputStream.use { it.readLimited(512 * 1024).toString(Charsets.UTF_8) }
            parse(json, barcode)
        }
    }

    fun downloadImage(image: String): ByteArray {
        val url = URL(image)
        require(url.protocol == "https" && url.host in imageHosts)
        return request(url, "image/*") { connection ->
            ensureSuccess(connection)
            connection.inputStream.use { it.readLimited(8 * 1024 * 1024) }
        }
    }

    internal fun parse(json: String, barcode: String): FoodSuggestion? {
        val response = JSONObject(json)
        val product = response.optJSONObject("product") ?: return null
        val code = product.text("code")?.takeIf(StockRules::isBarcode) ?: barcode
        val name = product.text("product_name_pt") ?: product.text("product_name")
        val front = product.optJSONObject("selected_images")?.optJSONObject("front")
        val display = front?.optJSONObject("display")
        val small = front?.optJSONObject("small")
        val language = product.text("lang")
        val image = sequenceOf(
            display?.text("pt"), small?.text("pt"),
            language?.let { display?.text(it) }, language?.let { small?.text(it) },
            product.text("image_front_small_url"), product.text("image_front_url"),
            display?.keys()?.asSequence()?.sorted()?.mapNotNull { display.text(it) }?.firstOrNull(),
            small?.keys()?.asSequence()?.sorted()?.mapNotNull { small.text(it) }?.firstOrNull(),
        ).filterNotNull().firstOrNull { value ->
            runCatching { URL(value) }.getOrNull()?.let { it.protocol == "https" && it.host in imageHosts } == true
        }
        return FoodSuggestion(code, name, image, product.text("brands"))
    }

    fun sourcePage(code: String): String = "https://br.openfoodfacts.org/product/$code"

    private fun JSONObject.text(key: String): String? = (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun <T> request(url: URL, accept: String, read: (HttpURLConnection) -> T): T {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 7_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", accept)
        }
        return try {
            read(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureSuccess(connection: HttpURLConnection) {
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count == -1) return output.toByteArray()
            if (output.size() + count > limit) throw IOException("Resposta excede o tamanho permitido.")
            output.write(buffer, 0, count)
        }
    }
}
