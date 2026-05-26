package `in`.sreerajp.chronotune_smart_clock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import `in`.sreerajp.chronotune_smart_clock.MainActivity
import `in`.sreerajp.chronotune_smart_clock.R

class DigitalClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> renderWidget(context, appWidgetManager, id) }
    }

    companion object {
        fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, DigitalClockWidgetProvider::class.java)
            )
            ids.forEach { id -> renderWidget(context, mgr, id) }
        }

        private fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)

            // RemoteViews can't tint a shape drawable at runtime, so we swap
            // in one of five pre-baked rounded-rect drawables instead.
            val bgRes = WidgetPrefs.digitalBackgroundRes(
                WidgetPrefs.getDigitalBgAlpha(context)
            )
            views.setInt(R.id.widget_digital_root, "setBackgroundResource", bgRes)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_digital_root, pi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
