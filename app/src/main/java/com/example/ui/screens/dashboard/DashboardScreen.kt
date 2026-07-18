package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DailyReport
import com.example.ui.viewmodel.ReportViewModel

// --- REDESIGNED DASHBOARD TAB ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboardTab(
    reportsCount: Int,
    reports: List<DailyReport>,
    viewModel: ReportViewModel,
    onCreateReport: (String) -> Unit,
    onViewReportsTab: () -> Unit
) {
    val context = LocalContext.current
    val backupRestored by viewModel.backupRestoredEvent.collectAsState()
    val defaultPreparedBy = remember(backupRestored) { viewModel.sharedPreferences.getString("default_prepared_by", "") ?: "" }
    val welcomeName = defaultPreparedBy.ifEmpty { "مهندس" }
    val defaultReportType = remember(backupRestored) { viewModel.sharedPreferences.getString("default_report_type", "ASK") ?: "ASK" }
    val weatherEnabled = remember(backupRestored) { viewModel.sharedPreferences.getBoolean("weather_enabled", true) }
    val cityName = remember(backupRestored) { viewModel.sharedPreferences.getString("weather_city_name", "") ?: "" }
    val customUnitTitle = remember(backupRestored) { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }

    val currentWeather by viewModel.currentWeather.collectAsStateWithLifecycle()
    val dailyForecast by viewModel.dailyForecast.collectAsStateWithLifecycle()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsStateWithLifecycle()

    var showCreateReportDialog by remember { mutableStateOf(false) }
    var showForecastDialog by remember { mutableStateOf(false) }

    val completedReports = reports.count { it.tasks.isNotEmpty() || it.materials.isNotEmpty() || it.photos.isNotEmpty() }
    val openReports = (reportsCount - completedReports).coerceAtLeast(0)
    val latestReport = reports.maxByOrNull { it.date }

    LaunchedEffect(weatherEnabled, backupRestored) {
        val autoUpdate = viewModel.sharedPreferences.getBoolean("weather_auto_update", true)
        if (weatherEnabled && autoUpdate) viewModel.fetchWeatherIfNeeded(forceRefresh = false)
    }

    fun createReport(type: String) {
        onCreateReport(type)
        Toast.makeText(context, "گزارش روزانه جدید ایجاد شد 📝", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color(0xFF004D40), Color(0xFF00796B), Color(0xFF10B981))
                                )
                            )
                            .padding(22.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(54.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Engineering, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("مرکز کنترل گزارش کارگاه", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                                    Text("سلام $welcomeName؛ وضعیت امروز پروژه آماده بررسی است.", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                DashboardHeroMetric("کل گزارش‌ها", toPersianDigits(reportsCount), Icons.Default.Assignment, Modifier.weight(1f))
                                DashboardHeroMetric("دارای داده", toPersianDigits(completedReports), Icons.Default.Verified, Modifier.weight(1f))
                                DashboardHeroMetric("نیازمند تکمیل", toPersianDigits(openReports), Icons.Default.PendingActions, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (weatherEnabled && cityName.isNotEmpty()) {
                item {
                    WeatherSummaryCard(
                        cityName = cityName,
                        currentWeather = currentWeather,
                        dailyForecast = dailyForecast,
                        isLoading = isWeatherLoading,
                        onClick = { showForecastDialog = true }
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DashboardKpiCard("آخرین گزارش", latestReport?.date?.let(::toPersianDigits) ?: "—", Icons.Default.Today, Color(0xFF3B82F6), Modifier.weight(1f))
                    DashboardKpiCard("واحدهای فعال", toPersianDigits(reports.map { it.reportType.ifEmpty { "EXECUTION" } }.distinct().size), Icons.Default.Business, Color(0xFFF59E0B), Modifier.weight(1f))
                }
            }

            item { ReportCategoryChart(reports = reports, customUnitTitle = customUnitTitle) }

            item {
                SectionHeader(title = "اقدام سریع", subtitle = "نوع گزارش را انتخاب کنید و فرم را ادامه دهید")
                QuickReportTypeGrid(
                    customUnitTitle = customUnitTitle,
                    onCreateReport = { type ->
                        if (type == "ASK") showCreateReportDialog = true else createReport(type)
                    }
                )
            }

            item {
                SectionHeader(title = "گزارش‌های اخیر", subtitle = "آخرین موارد ثبت‌شده برای ادامه یا بازبینی")
                if (reports.isEmpty()) {
                    EmptyDashboardState(onCreate = { if (defaultReportType == "ASK") showCreateReportDialog = true else createReport(defaultReportType) })
                }
            }

            itemsIndexed(reports.take(5)) { _, report ->
                ActivityItemCard(
                    unit = getReportTypeInfo(report.reportType, customUnitTitle).first,
                    project = report.project.ifEmpty { "پروژه بدون عنوان" },
                    description = report.tasks.firstOrNull()?.description ?: report.obstacles.ifEmpty { "برای مشاهده جزئیات به آرشیو گزارش‌ها بروید." },
                    date = report.date
                )
            }

            item {
                OutlinedButton(
                    onClick = onViewReportsTab,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.ListAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("مشاهده و مدیریت همه گزارش‌ها", fontWeight = FontWeight.Bold)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { if (defaultReportType == "ASK" || defaultReportType.isEmpty()) showCreateReportDialog = true else createReport(defaultReportType) },
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            containerColor = Color(0xFF0F766E),
            contentColor = Color.White,
            shape = RoundedCornerShape(22.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("گزارش جدید", fontWeight = FontWeight.Bold) }
        )
    }

    if (showCreateReportDialog) {
        ReportTypeDialog(
            customUnitTitle = customUnitTitle,
            onDismiss = { showCreateReportDialog = false },
            onCreate = { type -> showCreateReportDialog = false; createReport(type) }
        )
    }

    if (showForecastDialog && dailyForecast != null) {
        ForecastDialog(dailyForecast = dailyForecast!!, onDismiss = { showForecastDialog = false })
    }
}

@Composable
private fun DashboardHeroMetric(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.16f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(title, color = Color.White.copy(alpha = 0.76f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeatherSummaryCard(cityName: String, currentWeather: com.example.weather.CurrentWeather?, dailyForecast: com.example.weather.DailyWeather?, isLoading: Boolean, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text("آب و هوای کارگاه", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(cityName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isLoading && currentWeather == null) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
            } else if (currentWeather != null) {
                val code = dailyForecast?.weatherCode?.firstOrNull() ?: currentWeather.weatherCode
                val (icon, desc) = com.example.weather.WeatherRepository.getWeatherCodeInfo(code, currentWeather.isDay == 1)
                Text(icon, fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(desc, fontWeight = FontWeight.Bold)
                    Text("${currentWeather.temperature.toInt()}° | باد ${currentWeather.windSpeed.toInt()} km/h", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Text("برای جزئیات لمس کنید", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QuickReportTypeGrid(customUnitTitle: String, onCreateReport: (String) -> Unit) {
    val reportTypes = listOf(
        Triple("EXECUTION", "اجرایی", Icons.Default.Construction),
        Triple("WAREHOUSE", "انبار", Icons.Default.Inventory2),
        Triple("TECHNICAL", "دفتر فنی", Icons.Default.Architecture),
        Triple("HSE", "ایمنی", Icons.Default.HealthAndSafety),
        Triple("SURVEY", "نقشه‌برداری", Icons.Default.Explore),
        Triple("LEGAL", "حقوقی", Icons.Default.Gavel),
        Triple("CUSTOM", customUnitTitle, Icons.Default.Extension)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        reportTypes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (type, title, icon) ->
                    ElevatedCard(onClick = { onCreateReport(type) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyDashboardState(onCreate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
            Text("هنوز گزارشی ثبت نشده است", fontWeight = FontWeight.Bold)
            Text("اولین گزارش روزانه را بسازید تا داشبورد با داده‌های واقعی پر شود.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Button(onClick = onCreate, shape = RoundedCornerShape(16.dp)) { Text("شروع ثبت گزارش") }
        }
    }
}

@Composable
private fun ReportTypeDialog(customUnitTitle: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val reportTypes = listOf(
        "EXECUTION" to "فعالیت‌های اجرایی 🔨",
        "WAREHOUSE" to "انباردار کارگاه 📦",
        "TECHNICAL" to "دفتر فنی کارگاه 📐",
        "HSE" to "ایمنی HSE 🛡️",
        "SURVEY" to "نقشه‌برداری کارگاه 📐",
        "LEGAL" to "حقوقی و تملک اراضی ⚖️",
        "CUSTOM" to customUnitTitle
    )
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ثبت گزارش جدید روزانه", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { reportTypes.forEach { (type, label) -> Button(onClick = { onCreate(type) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(label, fontWeight = FontWeight.Bold) } } }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
private fun ForecastDialog(dailyForecast: com.example.weather.DailyWeather, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("پیش‌بینی ۷ روزه", fontWeight = FontWeight.Bold) }, text = {
        LazyColumn { items(dailyForecast.time.size) { i ->
            val (icon, desc) = com.example.weather.WeatherRepository.getWeatherCodeInfo(dailyForecast.weatherCode[i], true)
            ListItem(headlineContent = { Text(formatGregorianToJalali(dailyForecast.time[i]), fontWeight = FontWeight.Bold) }, supportingContent = { Text(desc) }, leadingContent = { Text(icon, fontSize = 22.sp) }, trailingContent = { Text("${dailyForecast.minTemp[i].toInt()}° / ${dailyForecast.maxTemp[i].toInt()}°") })
            if (i < dailyForecast.time.lastIndex) HorizontalDivider()
        } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } })
}

// KPI Dashboard Card
@Composable
fun DashboardKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    colorAccent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colorAccent.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = colorAccent, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// Canvas report distribution chart
@Composable
fun ReportCategoryChart(reports: List<DailyReport>, customUnitTitle: String) {
    val categories = listOf(
        Triple("EXECUTION", "اجرا", Color(0xFF00695C)),
        Triple("WAREHOUSE", "انبار", Color(0xFF00A884)),
        Triple("TECHNICAL", "دفتر فنی", Color(0xFF3B82F6)),
        Triple("LEGAL", "حقوقی", Color(0xFF8B5CF6)),
        Triple("SURVEY", "نقشه", Color(0xFFEC4899)),
        Triple("HSE", "ایمنی", Color(0xFFEF4444)),
        Triple("CUSTOM", customUnitTitle.take(5), Color(0xFFF59E0B))
    )
    
    val counts = categories.map { (key, _, _) ->
        reports.count { it.reportType == key || (key == "EXECUTION" && it.reportType.isEmpty()) }
    }
    
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    text = "توزیع موضوعی گزارش‌های ثبت‌شده 📊",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                categories.forEachIndexed { index, (_, label, color) ->
                    val count = counts[index]
                    val heightRatio = count.toFloat() / maxCount
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = count.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(heightRatio.coerceAtLeast(0.08f))
                                .width(14.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(color, color.copy(alpha = 0.4f))
                                    ),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                        )
                        
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


