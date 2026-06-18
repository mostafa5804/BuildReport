package com.example.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.Calendar

class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        // Handle Action buttons
        if (action == ACTION_DONE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("last_reported_date", getTodayPersianDate()).apply()
            return
        }
        
        if (action == ACTION_SNOOZE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            
            scheduleSnoozeReminder(context)
            return
        }

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule the alarm if it was enabled
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("daily_reminder_enabled", true)
            if (isEnabled) {
                scheduleDailyReminder(context)
            }
        } else if (action == ACTION_DAILY_REMINDER) {
            // Double check if reminder is still enabled inside SharedPreferences
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("daily_reminder_enabled", true)
            if (isEnabled) {
                // If they have already done it today, skip showing the reminder!
                val lastReportedDate = prefs.getString("last_reported_date", "") ?: ""
                val todayPersian = getTodayPersianDate()
                if (lastReportedDate != todayPersian) {
                    showNotification(context)
                }
                // Schedule for the next day to ensure it keeps working perfectly
                scheduleDailyReminder(context)
            }
        }
    }

    private fun showNotification(context: Context) {
        val channelId = "daily_reminder_channel"
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "یادآور ثبت گزارش روزانه",
                NotificationManager.IMPORTANCE_HIGH // HIGH IMPORTANCE FOR PLAYING SOUND
            ).apply {
                description = "یادآوری ساعت 20 جهت تکمیل و ثبت گزارش مهندسی و کارگاهی روزانه"
                enableLights(true)
                enableVibration(true)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Setup Action button intents
        val doneIntent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_DONE
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            2002,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = "برنامه گزارش‌یار 📝"
        val content = "آیا گزارش امروز کارگاه خود را ثبت کرده‌اید؟ لطفا پیش از پایان روز اطلاعات کارگاهی را تکمیل کنید."
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // PRIORITY_HIGH for heads-up and sound
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI) // AUDIBLE SOUND
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "انجام شد ✅", donePendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "یادآوری ۳۰ دقیقه بعد ⏰", snoozePendingIntent)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getTodayPersianDate(): String {
        val calendar = Calendar.getInstance()
        val gy = calendar.get(Calendar.YEAR)
        val gm = calendar.get(Calendar.MONTH) + 1
        val gd = calendar.get(Calendar.DAY_OF_MONTH)
        
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gDayNo = 365 * (gy - 1867) + (gy - 1867) / 4 - (gy - 1867) / 100 + (gy - 1867) / 400
        for (i in 1 until gm) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm > 2 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd - 1
        var jDayNo = gDayNo - 737242
        val jNP = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNP + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (jDayNo < 186) {
            jm = 1 + jDayNo / 31
            jd = 1 + jDayNo % 31
        } else {
            jm = 7 + (jDayNo - 186) / 30
            jd = 1 + (jDayNo - 186) % 30
        }
        
        fun toPersianDigits(num: Int): String {
            val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
            val str = num.toString()
            return str.map { c -> if (c.isDigit()) persianDigits[c - '0'] else c }.joinToString("")
        }
        
        return "${toPersianDigits(jy)}/${toPersianDigits(jm)}/${toPersianDigits(jd)}"
    }

    companion object {
        const val ACTION_DAILY_REMINDER = "com.example.ACTION_DAILY_REMINDER"
        const val ACTION_DONE = "com.example.ACTION_DONE"
        const val ACTION_SNOOZE = "com.example.ACTION_SNOOZE"
        const val NOTIFICATION_ID = 2026

        fun scheduleDailyReminder(context: Context) {
            cancelDailyReminder(context) // Cancel any existing same alarm first

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailyReminderReceiver::class.java).apply {
                action = ACTION_DAILY_REMINDER
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Target Calendar at 20:00 (8:00 PM)
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                // If 20:00 is already in the past for today, schedule for tomorrow
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }

        fun scheduleSnoozeReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailyReminderReceiver::class.java).apply {
                action = ACTION_DAILY_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1002, // Different request code from main daily alarm (1001)
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = System.currentTimeMillis() + 30 * 60 * 1000 // 30 minutes in future
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }

        fun cancelDailyReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailyReminderReceiver::class.java).apply {
                action = ACTION_DAILY_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            
            // Also cancel any active snooze timer
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                1002,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (snoozePendingIntent != null) {
                alarmManager.cancel(snoozePendingIntent)
                snoozePendingIntent.cancel()
            }
        }
    }
}
