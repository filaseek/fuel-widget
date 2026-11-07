package cz.fuelwidget

import android.content.Context

/** Which station each widget shows, plus the last price list so the widget works offline. */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("fuel_widget", Context.MODE_PRIVATE)

    fun setStation(ctx: Context, widgetId: Int, name: String) =
        sp(ctx).edit().putString("station_$widgetId", name).apply()

    fun station(ctx: Context, widgetId: Int): String? = sp(ctx).getString("station_$widgetId", null)

    fun remove(ctx: Context, widgetId: Int) = sp(ctx).edit().remove("station_$widgetId").apply()

    fun saveCache(ctx: Context, list: PriceList) {
        val rows = list.stations.joinToString("\n") { "${it.name}\t${it.n95 ?: ""}\t${it.diesel ?: ""}" }
        sp(ctx).edit()
            .putString("cache", rows)
            .putString("cache_valid", list.validFrom)
            .putLong("cache_time", System.currentTimeMillis())
            .apply()
    }

    fun cachedStation(ctx: Context, name: String): Station? =
        sp(ctx).getString("cache", null)?.lineSequence()
            ?.map { it.split('\t') }
            ?.firstOrNull { it.size == 3 && it[0] == name }
            ?.let { Station(it[0], it[1].ifEmpty { null }, it[2].ifEmpty { null }) }

    fun cachedValidFrom(ctx: Context): String? = sp(ctx).getString("cache_valid", null)

    fun cacheTime(ctx: Context): Long = sp(ctx).getLong("cache_time", 0L)
}
