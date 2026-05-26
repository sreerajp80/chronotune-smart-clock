package `in`.sreerajp.chronotune_smart_clock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import `in`.sreerajp.chronotune_smart_clock.MainActivity
import `in`.sreerajp.chronotune_smart_clock.R

class AnalogClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> renderWidget(context, appWidgetManager, id, null) }
        scheduleNextTick(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        renderWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            renderAll(context)
            scheduleNextTick(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelTick(context)
    }

    companion object {
        const val ACTION_TICK = "in.sreerajp.chronotune_smart_clock.widget.ANALOG_TICK"

        fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, AnalogClockWidgetProvider::class.java)
            )
            ids.forEach { id -> renderWidget(context, mgr, id, null) }
        }

        private fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle?
        ) {
            val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
            val sizePx = pickBitmapSize(context, opts)
            val dark = isSystemInDarkMode(context)

            val bmp = AnalogClockFaceRenderer.render(
                sizePx = sizePx,
                timestampMs = System.currentTimeMillis(),
                dark = dark,
                faceAlpha = WidgetPrefs.getAnalogBgAlpha(context)
            )

            val views = RemoteViews(context.packageName, R.layout.widget_analog_clock)
            views.setImageViewBitmap(R.id.widget_analog_face, bmp)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_analog_root, pi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun pickBitmapSize(context: Context, opts: Bundle): Int {
            // Honor user-resized dimensions if available; fall back to a reasonable default.
            val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val sideDp = minOf(
                if (minWidthDp > 0) minWidthDp else 160,
                if (minHeightDp > 0) minHeightDp else 160
            )
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                sideDp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
            return px.coerceIn(192, 720)
        }

        private fun isSystemInDarkMode(context: Context): Boolean {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }

        /**
         * AppWidgetProviderInfo.updatePeriodMillis is clamped to a 30-min minimum,
         * which is useless for a clock with a moving seconds hand. We instead
         * schedule an AlarmManager tick at the next wall-clock second boundary
         * and reschedule on each fire.
         *
         * ELAPSED_REALTIME (non-wakeup) means the alarm only fires while the
         * device is already awake — so the seconds hand sweeps when the user
         * is looking at the home screen but doesn't drain battery in Doze.
         */
        fun scheduleNextTick(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val now = System.currentTimeMillis()
            val msIntoSecond = now % 1_000L
            val delay = 1_000L - msIntoSecond
            val triggerAt = SystemClock.elapsedRealtime() + delay

            // setExact (not set) — AlarmManager.set batches inexact alarms into
            // ~5-second windows since API 19, which makes the seconds hand
            // visibly stutter. Non-wakeup ELAPSED_REALTIME doesn't require
            // SCHEDULE_EXACT_ALARM, so this is free.
            am.setExact(
                AlarmManager.ELAPSED_REALTIME,
                triggerAt,
                tickPendingIntent(context)
            )
        }

        private fun cancelTick(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(tickPendingIntent(context))
        }

        private fun tickPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AnalogClockWidgetProvider::class.java).apply {
                action = ACTION_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
