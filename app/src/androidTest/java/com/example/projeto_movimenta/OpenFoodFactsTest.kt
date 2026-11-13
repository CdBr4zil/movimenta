package com.example.projeto_movimenta

import org.junit.Assert.*
import org.junit.Test

class OpenFoodFactsTest {
    @Test fun prefersPortugueseNameAndFrontPhotoAndAcceptsPartialData() {
        val result = OpenFoodFacts.parse("""{
          "product": {
            "code": "000123",
            "product_name": "Milk",
            "product_name_pt": "Leite",
            "brands": "Marca A, Marca B",
            "lang": "en",
            "selected_images": {"front": {"display": {
              "en": "https://images.openfoodfacts.org/en.jpg",
              "pt": "https://images.openfoodfacts.org/pt.jpg"
            }}}
          }
        }""", "000123")
        assertEquals("Leite", result?.name)
        assertEquals("Marca A, Marca B", result?.brand)
        assertEquals("https://images.openfoodfacts.org/pt.jpg", result?.image)
        assertEquals("000123", result?.code)
        val partial = OpenFoodFacts.parse("""{"product":{"product_name":"Milk"}}""", "000123")
        assertEquals("Milk", partial?.name)
        assertNull(partial?.image)
        assertNull(partial?.brand)
        assertNull(OpenFoodFacts.parse("""{"status":"failure"}""", "000123"))
    }
}
