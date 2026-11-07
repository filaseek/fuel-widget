package cz.fuelwidget

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

data class Station(val name: String, val n95: String?, val diesel: String?) {
    /** "ČS Praha - Štěrboholy" -> "Praha - Štěrboholy" */
    val displayName: String get() = name.removePrefix("ČS").trim()
}

data class PriceList(val validFrom: String?, val stations: List<Station>)

/**
 * Reads the official Tank ONO price list (one row per station) and pulls out
 * Natural 95 and Diesel in CZK.
 */
object PriceFetcher {
    const val CENIK_URL = "https://www.tank-ono.cz/cz/index.php?page=cenik"

    private val OPTS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val CELL = Regex("""<t[dh]\b[^>]*>(.*?)</t[dh]>""", OPTS)
    private val TAG = Regex("""<[^>]+>""")
    private val ROW = Regex("""<tr\b[^>]*>(.*?)(?=<tr\b|</table>|$)""", OPTS)
    private val N95_ICON = Regex("""[/"']n95_c\.gif""", RegexOption.IGNORE_CASE)
    private val DIESEL_ICON = Regex("""[/"']d_c\.gif""", RegexOption.IGNORE_CASE)
    private val PRICE_LIKE = Regex("""^(\d+(?:[.,]\d+)?|-{2,}|\*)$""")
    private val NUMBER = Regex("""^\d+(?:[.,]\d+)?$""")
    private val VALID_FROM = Regex("""platnost\s+od:?\s*(\d{1,2}\.\s*\d{1,2}\.\s*\d{4})""", RegexOption.IGNORE_CASE)

    @Throws(IOException::class)
    fun fetch(): PriceList {
        val conn = URL(CENIK_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) FuelWidget/1.0")
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val bytes = conn.inputStream.use { it.readBytes() }
            val html = String(bytes, charsetOf(conn.contentType, bytes))
            val list = parse(html)
            if (list.stations.isEmpty()) throw IOException("Price table not found")
            return list
        } finally {
            conn.disconnect()
        }
    }

    /** The site may be UTF-8 or windows-1250; trust the header, then the <meta> tag. */
    private fun charsetOf(contentType: String?, bytes: ByteArray): Charset {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val candidates = listOf(
            contentType?.let { Regex("""charset=["']?([\w-]+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) },
            Regex("""charset=["']?([\w-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1),
        )
        for (name in candidates) {
            if (name != null) runCatching { return Charset.forName(name) }
        }
        return Charsets.UTF_8
    }

    fun parse(html: String): PriceList {
        val validFrom = VALID_FROM.find(html)?.groupValues?.get(1)?.replace(" ", "")

        // Only the CZK table: from the table row holding the ONO logo up to where the EUR table begins.
        val logo = html.indexOf("ono_logo_cenik")
        if (logo < 0) return PriceList(validFrom, emptyList())
        val start = html.lastIndexOf("<tr", logo, ignoreCase = true).let { if (it < 0) logo else it }
        val end = html.indexOf("(EUR)", logo).let { if (it < 0) html.length else it }
        val section = html.substring(start, end)

        // Rows of cells: raw HTML (to spot the fuel icons) and clean text (for names and prices).
        class Cell(val raw: String, val text: String)
        val rows = ROW.findAll(section).map { row ->
            CELL.findAll(row.groupValues[1]).map { Cell(it.groupValues[1], clean(it.groupValues[1])) }.toList()
        }.filter { it.isNotEmpty() }.toList()

        // Header row: the row with the fuel icons. A column's position = the icon cell's index in that row.
        // The station name sits in column 0 in both the header (logo) and the data rows.
        var n95Col = -1
        var dieselCol = -1
        for (row in rows) {
            val n = row.indexOfFirst { N95_ICON.containsMatchIn(it.raw) }
            val d = row.indexOfFirst { DIESEL_ICON.containsMatchIn(it.raw) }
            if (n >= 0 && d >= 0) {
                val logoCell = row.indexOfFirst { it.raw.contains("ono_logo_cenik") }.coerceAtLeast(0)
                n95Col = n - logoCell
                dieselCol = d - logoCell
                break
            }
        }
        if (n95Col <= 0 || dieselCol <= 0) return PriceList(validFrom, emptyList())

        val seen = HashSet<String>()
        val stations = ArrayList<Station>()
        for (row in rows) {
            val name = row[0].text
            if (name.isEmpty() || PRICE_LIKE.matches(name) || row.size <= maxOf(n95Col, dieselCol)) continue
            val n95 = row[n95Col].text.takeIf { NUMBER.matches(it) }
            val diesel = row[dieselCol].text.takeIf { NUMBER.matches(it) }
            if (n95 == null && diesel == null) continue
            if (seen.add(name)) stations += Station(name, n95, diesel)
        }
        return PriceList(validFrom, stations)
    }

    private fun clean(cellHtml: String): String {
        var t = TAG.replace(cellHtml, " ")
        if (t.contains('&')) t = decodeEntities(t)
        return t.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
    }

    // Plain Kotlin (no Android classes), so the parser can also be tested on the build server.
    private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|[A-Za-z]+);""")
    private val NAMED = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "ndash" to "–",
        "aacute" to "á", "Aacute" to "Á", "eacute" to "é", "Eacute" to "É", "iacute" to "í", "Iacute" to "Í",
        "oacute" to "ó", "Oacute" to "Ó", "uacute" to "ú", "Uacute" to "Ú", "yacute" to "ý", "Yacute" to "Ý",
        "ccaron" to "č", "Ccaron" to "Č", "dcaron" to "ď", "Dcaron" to "Ď", "ecaron" to "ě", "Ecaron" to "Ě",
        "ncaron" to "ň", "Ncaron" to "Ň", "rcaron" to "ř", "Rcaron" to "Ř", "scaron" to "š", "Scaron" to "Š",
        "tcaron" to "ť", "Tcaron" to "Ť", "zcaron" to "ž", "Zcaron" to "Ž", "uring" to "ů", "Uring" to "Ů",
    )

    private fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> NAMED[e]
        } ?: m.value
    }
}
