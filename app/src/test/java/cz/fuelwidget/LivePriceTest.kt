package cz.fuelwidget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs on the build server: downloads the real Tank ONO price list and checks the parser reads
 * sensible N95 and Diesel prices. Writes a short report that goes into the release notes.
 */
class LivePriceTest {

    @Test
    fun liveCenikParsesSensiblePrices() {
        val list = PriceFetcher.fetch()
        val st = list.stations

        val report = buildString {
            appendLine("Price list valid from: ${list.validFrom}")
            appendLine("Stations read: ${st.size}")
            appendLine()
            st.take(6).forEach { appendLine("- ${it.displayName}: N95 ${it.n95 ?: "–"}, Diesel ${it.diesel ?: "–"}") }
        }
        println(report)
        System.getenv("PARSE_REPORT")?.let { File(it).writeText(report) }

        fun num(s: String?) = s?.replace(',', '.')?.toDoubleOrNull()
        assertTrue("Too few stations: ${st.size}", st.size >= 20)
        // Fuel costs roughly 25–80 Kč/l; AdBlue (~13) or car wash (~99) mean a wrong column.
        val n95ok = st.count { num(it.n95)?.let { v -> v in 25.0..80.0 } == true }
        val dieselOk = st.count { num(it.diesel)?.let { v -> v in 25.0..80.0 } == true }
        assertTrue("N95 looks wrong at most stations\n$report", n95ok >= st.size * 0.8)
        assertTrue("Diesel looks wrong at most stations\n$report", dieselOk >= st.size * 0.8)
    }
}
