package cz.fuelwidget

import android.text.Html
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
    private val GIF = Regex("""images/([A-Za-z0-9_]+)\.gif""", RegexOption.IGNORE_CASE)
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

        // Only the CZK table: from the first ONO logo up to where the EUR table begins.
        val start = html.indexOf("ono_logo_cenik").let { if (it < 0) 0 else it }
        val end = html.indexOf("(EUR)", start).let { if (it < 0) html.length else it }
        val section = html.substring(start, end)

        val cells = CELL.findAll(section).map { it.range.first to clean(it.groupValues[1]) }.toList()

        fun isStation(i: Int): Boolean {
            val t = cells[i].second
            if (t.isEmpty() || PRICE_LIKE.matches(t) || t.contains("(CZK)")) return false
            val next = cells.getOrNull(i + 1)?.second ?: return false
            return PRICE_LIKE.matches(next)
        }

        val firstStation = cells.indices.firstOrNull { isStation(it) }
            ?: return PriceList(validFrom, emptyList())

        // Column order comes from the fuel icons in the header (n95_c.gif, d_c.gif, ...).
        val header = section.substring(0, cells[firstStation].first)
        val icons = GIF.findAll(header).map { it.groupValues[1].lowercase() }
            .filter { it != "ono_logo_cenik" }.toList()
        val n95Offset = icons.indexOf("n95_c").let { if (it < 0) 1 else it + 1 }
        val dieselOffset = icons.indexOf("d_c").let { if (it < 0) 4 else it + 1 }

        val seen = HashSet<String>()
        val stations = ArrayList<Station>()
        for (i in firstStation until cells.size) {
            if (!isStation(i)) continue
            val name = cells[i].second
            val n95 = cells.getOrNull(i + n95Offset)?.second?.takeIf { NUMBER.matches(it) }
            val diesel = cells.getOrNull(i + dieselOffset)?.second?.takeIf { NUMBER.matches(it) }
            if (n95 == null && diesel == null) continue
            if (seen.add(name)) stations += Station(name, n95, diesel)
        }
        return PriceList(validFrom, stations)
    }

    private fun clean(cellHtml: String): String {
        var t = TAG.replace(cellHtml, " ")
        if (t.contains('&')) t = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString()
        return t.replace(' ', ' ').replace('￼', ' ')
            .replace(Regex("""\s+"""), " ").trim()
    }
}
