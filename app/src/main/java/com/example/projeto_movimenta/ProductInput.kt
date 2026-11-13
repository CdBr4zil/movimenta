package com.example.projeto_movimenta

/** Keeps barcodes as text, including leading zeroes. Also accepts pasted punctuation. */
fun numericInput(value: String): String = value.filter { it in '0'..'9' }

/** Separators are only displayed, so deleting and moving the cursor edits the digits. */
object DateInput {
    fun digits(value: String): String = numericInput(value).take(8)

    fun formatted(value: String): String = buildString {
        digits(value).forEachIndexed { index, digit ->
            if (index == 2 || index == 4) append('/')
            append(digit)
        }
    }

    fun originalToTransformed(offset: Int, length: Int): Int {
        val position = offset.coerceIn(0, length)
        return position + (if (position > 2) 1 else 0) + (if (position > 4) 1 else 0)
    }

    fun transformedToOriginal(offset: Int, length: Int): Int =
        (offset - (if (offset > 2) 1 else 0) - (if (offset > 5) 1 else 0)).coerceIn(0, length)
}
