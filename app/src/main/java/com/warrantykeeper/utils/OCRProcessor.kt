package com.warrantykeeper.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class WarrantyInfo(
    val productName: String? = null,
    val storeName: String? = null,
    val purchaseDate: Date? = null,
    val warrantyEndDate: Date? = null,
    val warrantyMonths: Int? = null
)

@Singleton
class OCRProcessor @Inject constructor(
    private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(imageUri: Uri): WarrantyInfo {
        return try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            processImage(bitmap)
        } catch (e: IOException) {
            e.printStackTrace()
            WarrantyInfo()
        }
    }

    suspend fun processImage(bitmap: Bitmap): WarrantyInfo {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.text
            
            parseWarrantyInfo(text)
        } catch (e: Exception) {
            e.printStackTrace()
            WarrantyInfo()
        }
    }

    private fun parseWarrantyInfo(text: String): WarrantyInfo {
        val lines = text.split("\n").map { it.trim() }
        
        var productName: String? = null
        var storeName: String? = null
        var purchaseDate: Date? = null
        var warrantyEndDate: Date? = null
        var warrantyMonths: Int? = null

        // Парсинг даты покупки
        purchaseDate = extractDate(text)

        // Парсинг срока гарантии в месяцах
        warrantyMonths = extractWarrantyMonths(text)

        // Вычисление даты окончания гарантии
        if (purchaseDate != null && warrantyMonths != null) {
            val calendar = Calendar.getInstance()
            calendar.time = purchaseDate
            calendar.add(Calendar.MONTH, warrantyMonths)
            warrantyEndDate = calendar.time
        }

        // Попытка извлечь название товара (обычно в начале чека)
        productName = extractProductName(lines)

        // Попытка извлечь название магазина
        storeName = extractStoreName(lines)

        return WarrantyInfo(
            productName = productName,
            storeName = storeName,
            purchaseDate = purchaseDate,
            warrantyEndDate = warrantyEndDate,
            warrantyMonths = warrantyMonths
        )
    }

    private fun extractDate(text: String): Date? {
        // Паттерны дат для разных форматов
        val datePatterns = listOf(
            "dd.MM.yyyy" to Regex("""\b(\d{2}\.\d{2}\.\d{4})\b"""),
            "dd/MM/yyyy" to Regex("""\b(\d{2}/\d{2}/\d{4})\b"""),
            "yyyy-MM-dd" to Regex("""\b(\d{4}-\d{2}-\d{2})\b"""),
            "dd-MM-yyyy" to Regex("""\b(\d{2}-\d{2}-\d{4})\b""")
        )

        for ((pattern, regex) in datePatterns) {
            val match = regex.find(text)
            if (match != null) {
                try {
                    val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
                    return dateFormat.parse(match.value)
                } catch (e: Exception) {
                    continue
                }
            }
        }

        return null
    }

    private fun extractWarrantyMonths(text: String): Int? {
        // Ищем упоминания гарантии
        val warrantyPatterns = listOf(
            Regex("""гарант[ия]*\s*:?\s*(\d+)\s*(мес|месяц|месяца|месяцев)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*(мес|месяц|месяца|месяцев)\s*гарант""", RegexOption.IGNORE_CASE),
            Regex("""warranty\s*:?\s*(\d+)\s*(month|months|mo)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*(month|months|mo)\s*warranty""", RegexOption.IGNORE_CASE),
            Regex("""срок\s*гарант[ии]*\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in warrantyPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val monthsStr = match.groupValues[1]
                return monthsStr.toIntOrNull()
            }
        }

        return null
    }

    private fun extractProductName(lines: List<String>): String? {
        // Пытаемся найти название товара
        // Обычно это одна из первых строк после названия магазина
        val skipWords = setOf("чек", "receipt", "invoice", "кассовый", "магазин", "store")
        
        for (line in lines.take(10)) {
            if (line.length > 3 && !skipWords.any { line.lowercase().contains(it) }) {
                // Проверяем, что строка не является датой или числом
                if (!line.matches(Regex(""".*\d{2}[./-]\d{2}[./-]\d{4}.*"""))) {
                    return line
                }
            }
        }

        return null
    }

    private fun extractStoreName(lines: List<String>): String? {
        // Название магазина обычно в самом начале чека
        val storeKeywords = listOf("магазин", "store", "shop", "market", "ооо", "ип", "ltd", "inc")
        
        for (line in lines.take(5)) {
            if (storeKeywords.any { line.lowercase().contains(it) }) {
                return line
            }
        }

        // Если не нашли по ключевым словам, берем первую строку
        return lines.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    fun release() {
        recognizer.close()
    }
}
