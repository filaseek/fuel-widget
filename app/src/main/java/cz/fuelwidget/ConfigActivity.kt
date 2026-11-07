package cz.fuelwidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Lists every Tank ONO station with today's prices.
 * - Opened while adding the widget: tap a station to pin it to that widget.
 * - Opened from the app icon: tap a station to see its prices.
 */
class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val forWidget = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID

        // If the user backs out, the widget isn't added.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        title = if (forWidget) "Choose your Tank ONO" else "Tank ONO prices"

        val pad = (16 * resources.displayMetrics.density).toInt()
        val status = TextView(this).apply {
            text = "Loading stations…"
            textSize = 15f
            setPadding(pad, pad, pad, pad)
        }
        val list = ListView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(status)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        Thread {
            val result = runCatching { PriceFetcher.fetch() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { prices ->
                    Prefs.saveCache(this, prices)
                    status.text = buildString {
                        prices.validFrom?.let { append("Ceník platný od $it\n") }
                        append(if (forWidget) "Tap your station:" else "Long-press your home screen → Widgets → Fuel Widget to add it.")
                    }
                    val rows = prices.stations.map {
                        "${it.displayName}\n   N95 ${it.n95 ?: "–"}  ·  Diesel ${it.diesel ?: "–"} Kč"
                    }
                    list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
                    list.setOnItemClickListener { _, _, pos, _ ->
                        val st = prices.stations[pos]
                        if (forWidget) {
                            Prefs.setStation(this, widgetId, st.name)
                            FuelWidget.render(this, widgetId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle(st.displayName)
                                .setMessage("Natural 95: ${st.n95 ?: "–"} Kč\nDiesel: ${st.diesel ?: "–"} Kč")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }.onFailure {
                    status.text = "Couldn't load the price list (${it.message}).\nCheck your connection and open this again."
                }
            }
        }.start()
    }
}
