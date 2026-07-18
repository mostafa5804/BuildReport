package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.ReportRepository
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReportViewModel
import com.example.ui.viewmodel.ReportViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Room persistence framework components
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ReportRepository(database.dailyReportDao())
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        
        // Schedule / initialize daily reminder alarm at 20:00 if enabled (defaults to true)
        val dailyReminderEnabled = sharedPrefs.getBoolean("daily_reminder_enabled", true)
        if (dailyReminderEnabled) {
            com.example.receiver.DailyReminderReceiver.scheduleDailyReminder(this)
        }
        
        // Initialize state viewmodels via custom factory
        val viewModel: ReportViewModel by viewModels {
            ReportViewModelFactory(repository, sharedPrefs)
        }

        setContent {
            val sharedPrefs = androidx.compose.runtime.remember { getSharedPreferences("app_settings", MODE_PRIVATE) }
            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            val themeState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(sharedPrefs.getString("app_theme", "LIGHT") ?: "LIGHT")
            }

            androidx.compose.runtime.DisposableEffect(sharedPrefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "app_theme") {
                        themeState.value = sharedPrefs.getString("app_theme", "LIGHT") ?: "LIGHT"
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val useDark = when (themeState.value) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemInDark
            }

            MyApplicationTheme(darkTheme = useDark) {
                MainAppScreen(
                    viewModel = viewModel,
                    isDarkMode = useDark,
                    onToggleTheme = {
                        val next = if (useDark) "LIGHT" else "DARK"
                        sharedPrefs.edit().putString("app_theme", next).apply()
                        themeState.value = next
                    }
                )
            }
        }
    }
}
