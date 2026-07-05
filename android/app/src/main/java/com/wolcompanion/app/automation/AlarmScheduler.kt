package com.wolcompanion.app.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wolcompanion.app.data.Schedule
import com.wolcompanion.app.service.ScheduleReceiver
import java.util.Calendar

/**
 * Schedules time-based automations with AlarmManager (Doze-safe, no extra deps).
 * Each enabled [Schedule] gets one exact alarm at its next occurrence; the receiver
 * reschedules the following one after it fires.
 */
object AlarmScheduler {

    const val EXTRA_ID = "schedule_id"
    private const val ACTION = "com.wolcompanion.app.SCHEDULE_FIRE"

    fun rescheduleAll(context: Context, schedules: List<Schedule>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        schedules.forEach { cancel(context, am, it) }
        schedules.filter { it.enabled && it.days.isNotEmpty() }.forEach { schedule(context, am, it) }
    }

    fun scheduleOne(context: Context, schedule: Schedule) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, am, schedule)
        if (schedule.enabled) schedule(context, am, schedule)
    }

    private fun schedule(context: Context, am: AlarmManager, s: Schedule) {
        val next = nextTrigger(s)
        if (next <= 0) return
        val pi = pendingIntent(context, s)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    private fun cancel(context: Context, am: AlarmManager, s: Schedule) {
        am.cancel(pendingIntent(context, s))
    }

    private fun pendingIntent(context: Context, s: Schedule): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_ID, s.id)
        }
        return PendingIntent.getBroadcast(
            context, s.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Next epoch-millis this schedule should fire, or -1 if none in the next week. */
    fun nextTrigger(s: Schedule): Long {
        val now = Calendar.getInstance()
        for (i in 0..7) {
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, s.hour)
                set(Calendar.MINUTE, s.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val ourDay = calendarToOurDay(c.get(Calendar.DAY_OF_WEEK))
            if (s.days.contains(ourDay) && c.timeInMillis > now.timeInMillis) return c.timeInMillis
        }
        return -1
    }

    /** Calendar day (1=Sun..7=Sat) -> our day (1=Mon..7=Sun). */
    private fun calendarToOurDay(cd: Int): Int = if (cd == Calendar.SUNDAY) 7 else cd - 1
}
