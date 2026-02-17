package com.warrantykeeper.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface as AndroidXExif
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class WarrantyInfo(
    val productName: String? = null,
    val storeName: String? = null,
    val purchaseDate: Date? = null,
    val warrantyEndDate: Date? = null,
    val warrantyMonths: Int? = null,
    val rawText: String = ""
)

@Singleton
class OCRProcessor @Inject constructor(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Месяцы на всех нужных языках: EN, DE, LV, PL, RU
    private val monthMap: Map<String, Int> = mapOf(
        // English
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        // German
        "januar" to 1, "februar" to 2, "märz" to 3, "marz" to 3,
        "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8,
        "september" to 9, "oktober" to 10, "dezember" to 12,
        "mär" to 3, "okt" to 10, "dez" to 12,
        // Latvian
        "janvāris" to 1, "februāris" to 2, "marts" to 3, "aprīlis" to 4,
        "maijs" to 5, "jūnijs" to 6, "jūlijs" to 7, "augusts" to 8,
        "septembris" to 9, "oktobris" to 10, "novembris" to 11, "decembris" to 12,
        "janv" to 1, "febr" to 2, "apr" to 4, "jūn" to 6, "jūl" to 7,
        "aug" to 8, "sept" to 9, "okt" to 10,
        // Polish
        "stycznia" to 1, "lutego" to 2, "marca" to 3, "kwietnia" to 4,
        "maja" to 5, "czerwca" to 6, "lipca" to 7, "sierpnia" to 8,
        "września" to 9, "wrzesnia" to 9, "października" to 10, "pazdziernika" to 10,
        "listopada" to 11, "grudnia" to 12,
        "sty" to 1, "lut" to 2, "kwi" to 4, "cze" to 6,
        "lip" to 7, "sie" to 8, "wrz" to 9, "paź" to 10, "paz" to 10,
        "lis" to 11, "gru" to 12,
        // Russian
        "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4,
        "мая" to 5, "июня" to 6, "июля" to 7, "августа" to 8,
        "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12,
        "янв" to 1, "фев" to 2, "мар" to 3, "апр" to 4,
        "июн" to 6, "июл" to 7, "авг" to 8, "сен" to 9,
        "окт" to 10, "ноя" to 11, "дек" to 12
    )

    // ──────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ──────────────────────────────────────────────────────────────────────

    suspend fun processImage(imageUri: Uri): WarrantyInfo {
        return try {
            val bitmap = loadAndRotateBitmap(imageUri) ?: return WarrantyInfo()
            processImage(bitmap)
        } catch (e: Exception) { WarrantyInfo() }
    }

    suspend fun processImage(bitmap: Bitmap): WarrantyInfo {
        return try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
            parseAll(text)
        } catch (e: Exception) { WarrantyInfo() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // CORE PARSER
    // ──────────────────────────────────────────────────────────────────────

    private fun parseAll(text: String): WarrantyInfo {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val purchaseDate = extractPurchaseDate(text)
        val warrantyMonths = extractWarrantyMonths(text)
        val warrantyEndDate = computeEndDate(text, purchaseDate, warrantyMonths)
        return WarrantyInfo(
            productName = extractProductName(lines, text),
            storeName = extractStoreName(lines, text),
            purchaseDate = purchaseDate,
            warrantyEndDate = warrantyEndDate,
            warrantyMonths = warrantyMonths,
            rawText = text
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // DATE EXTRACTION — purchase date
    // ──────────────────────────────────────────────────────────────────────

    private fun extractPurchaseDate(text: String): Date? {
        val lower = text.lowercase()

        // Keywords that signal purchase/order date (all languages)
        val purchaseKeywords = listOf(
            // DE
            "bestelldatum", "kaufdatum", "rechnungsdatum", "lieferdatum",
            "datum:", "bestellt am", "gekauft am",
            // EN
            "date of purchase", "order date", "purchase date",
            "invoice date", "sale date", "sold on", "purchased on",
            "transaction date", "receipt date",
            // LV
            "pirkuma datums", "datums", "pasūtījuma datums",
            // PL
            "data zakupu", "data zamówienia", "data sprzedaży",
            // RU
            "дата покупки", "дата заказа", "дата продажи",
            "дата чека", "дата:"
        )

        val lines = text.lines()

        // Step 1: find a line with a keyword, look for date on same line or next 2
        for (keyword in purchaseKeywords) {
            val idx = lower.indexOf(keyword)
            if (idx < 0) continue
            val snippet = text.substring(idx, minOf(text.length, idx + 100))
            parseAnyDate(snippet)?.let { return it }
            // check next lines too
            val lineIdx = lines.indexOfFirst { it.lowercase().contains(keyword) }
            if (lineIdx >= 0) {
                for (offset in 0..2) {
                    val i = lineIdx + offset
                    if (i < lines.size) {
                        parseAnyDate(lines[i])?.let { return it }
                    }
                }
            }
        }

        // Step 2: no keyword found → take the first valid date in the whole text
        // but skip dates that look like product codes (no context)
        return parseAnyDate(text)
    }

    /**
     * Tries every date format we know. Returns the first valid Date found in [s].
     */
    private fun parseAnyDate(s: String): Date? {
        // 1) "15. Sep 2025" / "15 Sep 2025" / "15.Sep.2025" (DE/EN mixed)
        val named1 = Regex(
            """(\d{1,2})\.?\s+(${monthMap.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})[a-zA-ZāīūēžšģķļņčÄÖÜäöüąćęłńóśźżёа-яА-Я]*\.?\s+(\d{4})""",
            setOf(RegexOption.IGNORE_CASE)
        )
        named1.find(s)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let null
            val mo = monthMap[m.groupValues[2].lowercase().take(4)] ?: return@let null
            val y = m.groupValues[3].toIntOrNull() ?: return@let null
            makeDate(y, mo, d)?.let { return it }
        }

        // 2) "Sep 15, 2025" / "September 15 2025"
        val named2 = Regex(
            """(${monthMap.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})[a-zA-ZāīūēžšģķļņčÄÖÜäöüąćęłńóśźżёа-яА-Я]*\.?\s+(\d{1,2})[,.]?\s+(\d{4})""",
            setOf(RegexOption.IGNORE_CASE)
        )
        named2.find(s)?.let { m ->
            val mo = monthMap[m.groupValues[1].lowercase().take(4)] ?: return@let null
            val d = m.groupValues[2].toIntOrNull() ?: return@let null
            val y = m.groupValues[3].toIntOrNull() ?: return@let null
            makeDate(y, mo, d)?.let { return it }
        }

        // 3) dd.mm.yyyy or dd/mm/yyyy or dd-mm-yyyy
        val numeric1 = Regex("""(\d{2})[.\-/](\d{2})[.\-/](\d{4})""")
        numeric1.find(s)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let null
            val mo = m.groupValues[2].toIntOrNull() ?: return@let null
            val y = m.groupValues[3].toIntOrNull() ?: return@let null
            makeDate(y, mo, d)?.let { return it }
        }

        // 4) yyyy-mm-dd (ISO)
        val iso = Regex("""(\d{4})-(\d{2})-(\d{2})""")
        iso.find(s)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@let null
            val mo = m.groupValues[2].toIntOrNull() ?: return@let null
            val d = m.groupValues[3].toIntOrNull() ?: return@let null
            makeDate(y, mo, d)?.let { return it }
        }

        // 5) dd.mm.yy
        val short = Regex("""(\d{2})[.\-/](\d{2})[.\-/](\d{2})\b""")
        short.find(s)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let null
            val mo = m.groupValues[2].toIntOrNull() ?: return@let null
            val yy = m.groupValues[3].toIntOrNull() ?: return@let null
            val y = if (yy < 50) 2000 + yy else 1900 + yy
            makeDate(y, mo, d)?.let { return it }
        }

        return null
    }

    private fun makeDate(year: Int, month: Int, day: Int): Date? {
        if (year !in 2000..2100) return null
        if (month !in 1..12) return null
        if (day !in 1..31) return null
        return Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    // ──────────────────────────────────────────────────────────────────────
    // WARRANTY DURATION EXTRACTION (months)
    // ──────────────────────────────────────────────────────────────────────

    fun extractWarrantyMonths(text: String): Int? {
        // All patterns: (number, isYear) — ordered by specificity
        val patterns = listOf<Pair<Regex, Boolean>>(
            // German: "12 Monate Gewährleistung/Garantie"
            Regex("""(\d+)\s*Monate?\s+(?:Gewähr\w*|Garantie)""", RegexOption.IGNORE_CASE) to false,
            Regex("""(\d+)\s*Jahre?\s+(?:Gewähr\w*|Garantie)""", RegexOption.IGNORE_CASE) to true,
            Regex("""(?:Gewähr\w*|Garantie)\s*:?\s*(\d+)\s*Monate?""", RegexOption.IGNORE_CASE) to false,
            Regex("""(?:Gewähr\w*|Garantie)\s*:?\s*(\d+)\s*Jahre?""", RegexOption.IGNORE_CASE) to true,
            // English
            Regex("""(\d+)\s*-?\s*months?\s+(?:of\s+)?(?:limited\s+)?warrant\w+""", RegexOption.IGNORE_CASE) to false,
            Regex("""(\d+)\s*-?\s*years?\s+(?:of\s+)?(?:limited\s+)?warrant\w+""", RegexOption.IGNORE_CASE) to true,
            Regex("""warrant\w+\s*:?\s*(\d+)\s*months?""", RegexOption.IGNORE_CASE) to false,
            Regex("""warrant\w+\s*:?\s*(\d+)\s*years?""", RegexOption.IGNORE_CASE) to true,
            Regex("""(\d+)\s*months?\s+warrant\w+""", RegexOption.IGNORE_CASE) to false,
            Regex("""(\d+)\s*years?\s+warrant\w+""", RegexOption.IGNORE_CASE) to true,
            // Latvian: "12 mēnešu garantija"
            Regex("""(\d+)\s*m[eē]ne[sš]\w*\s*garantij\w*""", RegexOption.IGNORE_CASE) to false,
            Regex("""(\d+)\s*gad\w*\s*garantij\w*""", RegexOption.IGNORE_CASE) to true,
            Regex("""garantij\w*\s*:?\s*(\d+)\s*m[eē]ne[sš]\w*""", RegexOption.IGNORE_CASE) to false,
            // Polish: "12 miesięcy gwarancji"
            Regex("""(\d+)\s*miesięcy?\s+gwarancj\w*""", RegexOption.IGNORE_CASE) to false,
            Regex("""(\d+)\s*mies\w*\s+gwarancj\w*""", RegexOption.IGNORE_CASE) to false,
            Regex("""(\d+)\s*lat\w*\s+gwarancj\w*""", RegexOption.IGNORE_CASE) to true,
            Regex("""gwarancj\w*\s*:?\s*(\d+)\s*miesięcy?""", RegexOption.IGNORE_CASE) to false,
            // Russian: "12 месяцев гарантии"
            Regex("""(\d+)\s*месяц\w*\s+гаранти\w*""", RegexOption.IGNORE_CASE) to false,
            Regex("""(\d+)\s*год\w*\s+гаранти\w*""", RegexOption.IGNORE_CASE) to true,
            Regex("""гаранти\w*\s*:?\s*(\d+)\s*месяц\w*""", RegexOption.IGNORE_CASE) to false,
            Regex("""гаранти\w*\s*:?\s*(\d+)\s*год\w*""", RegexOption.IGNORE_CASE) to true,
            Regex("""срок\s+гаранти\w*\s*:?\s*(\d+)""", RegexOption.IGNORE_CASE) to false,
        )

        for ((pattern, isYear) in patterns) {
            val m = pattern.find(text) ?: continue
            val num = m.groupValues.drop(1).firstOrNull { it.toIntOrNull() != null }?.toInt() ?: continue
            if (num !in 1..120) continue
            return if (isYear) num * 12 else num
        }

        // Fallback: look for number near warranty keyword (in any language)
        val warrantyWords = listOf(
            "warrant", "garantie", "garantij", "gwarancj", "гаранти",
            "gewähr", "garant"
        )
        val lower = text.lowercase()
        for (word in warrantyWords) {
            val idx = lower.indexOf(word)
            if (idx < 0) continue
            val zone = text.substring(maxOf(0, idx - 40), minOf(text.length, idx + 80))
            val mMonth = Regex("""(\d+)\s*(?:months?|monate?|mēneš\w*|miesięcy|месяц\w*|mēn\w*)""", RegexOption.IGNORE_CASE).find(zone)
            val mYear = Regex("""(\d+)\s*(?:years?|jahre?|gad\w*|lat\w*|год\w*)""", RegexOption.IGNORE_CASE).find(zone)
            mMonth?.groupValues?.get(1)?.toIntOrNull()?.let { if (it in 1..120) return it }
            mYear?.groupValues?.get(1)?.toIntOrNull()?.let { if (it in 1..30) return it * 12 }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────
    // END DATE COMPUTATION
    // ──────────────────────────────────────────────────────────────────────

    private fun computeEndDate(text: String, purchaseDate: Date?, warrantyMonths: Int?): Date? {
        // 1) Explicit end date in text
        extractExplicitEndDate(text)?.let { return it }
        // 2) Purchase date + months; if no purchase date → today
        if (warrantyMonths != null) {
            val base = purchaseDate ?: Date()
            return Calendar.getInstance().apply {
                time = base
                add(Calendar.MONTH, warrantyMonths)
            }.time
        }
        return null
    }

    private fun extractExplicitEndDate(text: String): Date? {
        val keywords = listOf(
            // DE
            "garantie bis", "gültig bis", "gewährleistung bis",
            // EN
            "warranty until", "warranty expires", "valid until",
            "expires on", "expiry date", "expiration date",
            // LV
            "garantija līdz", "derīga līdz",
            // PL
            "gwarancja do", "ważna do",
            // RU
            "гарантия до", "действительна до"
        )
        val lower = text.lowercase()
        for (kw in keywords) {
            val idx = lower.indexOf(kw)
            if (idx < 0) continue
            val snippet = text.substring(idx, minOf(text.length, idx + 60))
            parseAnyDate(snippet)?.let { return it }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────
    // PRODUCT NAME
    // ──────────────────────────────────────────────────────────────────────

    private fun extractProductName(lines: List<String>, text: String): String? {
        val skipKeywords = setOf(
            // Store names / brands
            "ebay", "amazon", "maxima", "rimi", "lidl", "mediamarkt", "saturn",
            "wildberries", "ozon", "aliexpress",
            // Doc type words
            "rechnung", "packzettel", "invoice", "receipt", "чек", "кассовый",
            "paragon", "faktura",
            // DE document fields
            "bestellung", "lieferadresse", "absenderadresse", "artikelnr",
            "stückzahl", "artikelpreis", "gesamtbetrag", "versand",
            "zwischensumme", "mwst",
            // LV
            "summa", "atlaide", "čeks", "kasieris", "veikals",
            // Common noise
            "total", "итого", "сумма", "https://", "http://",
            "pvn", "ндс", "mwst", "vat"
        )
        val skipPatterns = listOf(
            Regex("""^\d+[.,]\d{2}\s*[€$£]?$"""),      // price only
            Regex("""^\d{2}[.\-/]\d{2}[.\-/]\d{4}"""),  // date
            Regex("""^[+\-#*=_]{3,}"""),                 // separator
            Regex("""^\d{5,}$"""),                       // long number
            Regex("""^https?://"""),                     // URL
            Regex("""^[A-Z0-9]{15,}$"""),               // hash/code
        )

        // For eBay-style: look for "Artikelname" or product in item table
        val ebayPattern = Regex("""(Lenovo|Apple|Samsung|Sony|HP|Dell|Asus|Acer|Xiaomi|Huawei|LG|Panasonic|Philips|Bosch|Siemens)\s+[\w\s\-]+""", RegexOption.IGNORE_CASE)
        ebayPattern.find(text)?.let { return it.value.trim().take(80) }

        for (line in lines) {
            if (line.length < 5 || line.length > 120) continue
            val lower = line.lowercase()
            if (skipKeywords.any { lower.contains(it) }) continue
            if (skipPatterns.any { it.containsMatchIn(line) }) continue
            if (line.contains(Regex("""[A-Za-zА-Яа-яĀāĒēĪīŪūÄÖÜäöüąćęłń]{4,}"""))) {
                return line.take(80)
            }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────
    // STORE NAME
    // ──────────────────────────────────────────────────────────────────────

    private fun extractStoreName(lines: List<String>, text: String): String? {
        // Known store brands (highest priority)
        val knownStores = listOf(
            "eBay", "Amazon", "MAXIMA", "Maxima", "Rimi", "Lidl", "Aldi",
            "Saturn", "MediaMarkt", "Media Markt", "Wildberries", "Ozon",
            "AliExpress", "Elmo", "Euronics", "Pigu", "RD Electronics",
            "Top-Notebook", "TopNotebook"
        )
        for (store in knownStores) {
            if (text.contains(store, ignoreCase = true)) return store
        }

        // Keywords that precede or describe the store
        val storeKeywords = listOf(
            "veikals", "sia ", "ооо ", "оао ", "ип ", "магазин",
            "store", "shop", "market", "ltd", "inc", "gmbh", "s.a.", "sp. z o.o."
        )
        for (line in lines.take(8)) {
            val lower = line.lowercase()
            if (storeKeywords.any { lower.contains(it) }) return line.take(60)
        }

        // First non-empty, non-noise line
        return lines.firstOrNull { it.length in 3..60 && !it.matches(Regex("""[\d\s\-+*#=_.]+""")) }
    }

    // ──────────────────────────────────────────────────────────────────────
    // IMAGE LOADING + EXIF ROTATION FIX
    // ──────────────────────────────────────────────────────────────────────

    private fun loadAndRotateBitmap(uri: Uri): Bitmap? {
        return try {
            val stream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()

            val exifStream: InputStream? = context.contentResolver.openInputStream(uri)
            val degrees = exifStream?.use { s ->
                try {
                    val exif = AndroidXExif(s)
                    when (exif.getAttributeInt(AndroidXExif.TAG_ORIENTATION, AndroidXExif.ORIENTATION_NORMAL)) {
                        AndroidXExif.ORIENTATION_ROTATE_90 -> 90f
                        AndroidXExif.ORIENTATION_ROTATE_180 -> 180f
                        AndroidXExif.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } catch (e: Exception) { 0f }
            } ?: 0f

            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else bitmap
        } catch (e: Exception) { null }
    }

    fun release() { recognizer.close() }
}
