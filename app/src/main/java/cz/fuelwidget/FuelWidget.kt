package cz.fuelwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FuelWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx, ids)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) refresh(ctx, intArrayOf(id))
        }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) = ids.forEach { Prefs.remove(ctx, it) }

    private fun refresh(ctx: Context, ids: IntArray) {
        ids.forEach { render(ctx, it, status = Status.LOADING) }
        val pending = goAsync()
        Thread {
            try {
                val result = runCatching { PriceFetcher.fetch() }
                result.onSuccess { Prefs.saveCache(ctx, it) }
                val status = if (result.isSuccess) Status.OK else Status.OFFLINE
                ids.forEach { render(ctx, it, status) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    enum class Status { OK, LOADING, OFFLINE }

    companion object {
        const val ACTION_REFRESH = "cz.fuelwidget.REFRESH"

        fun render(ctx: Context, widgetId: Int, status: Status = Status.OK) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_fuel)
            val name = Prefs.station(ctx, widgetId)
            val st = name?.let { Prefs.cachedStation(ctx, it) }

            views.setTextViewText(R.id.station, st?.displayName ?: name?.removePrefix("ČS")?.trim() ?: "Tank ONO")
            views.setTextViewText(R.id.priceN95, st?.n95 ?: "–")
            views.setTextViewText(R.id.priceDiesel, st?.diesel ?: "–")

            val time = Prefs.cacheTime(ctx).takeIf { it > 0 }
                ?.let { SimpleDateFormat("d.M. HH:mm", Locale.getDefault()).format(Date(it)) }
            val valid = Prefs.cachedValidFrom(ctx)?.let { "ceník od ${it.substringBeforeLast('.')}." } // 14.10.2026 -> ceník od 14.10.
            views.setTextViewText(
                R.id.status,
                when (status) {
                    Status.LOADING -> "Updating…"
                    Status.OFFLINE -> if (time != null) "Offline · $time" else "No connection · tap to retry"
                    Status.OK -> listOfNotNull(valid, time).joinToString(" · ")
                }
            )

            val tap = Intent(ctx, FuelWidget::class.java)
                .setAction(ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            views.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getBroadcast(ctx, widgetId, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            AppWidgetManager.getInstance(ctx).updateAppWidget(widgetId, views)
        }
    }
}
