package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.model.*
import com.example.ui.viewmodel.ReportViewModel
import kotlinx.coroutines.launch

// --- PERSISTENT UTILITIES ---
val BorderColor: Color
    @Composable
    get() = if (androidx.compose.material3.MaterialTheme.colorScheme.background == com.example.ui.theme.BackgroundDark) Color(0xFF4B5563) else Color(0xFFCBD5E1)

sealed class ActiveScreen {
    object ReportList : ActiveScreen()
    object ReportEditor : ActiveScreen()
}

data class SignatureLine(val points: List<Offset>)

fun base64ToBitmap(base64Str: String): android.graphics.Bitmap? {
    return try {
        val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } ?: return null
        val outputStream = java.io.ByteArrayOutputStream()
        val maxDim = 600
        val resized = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = kotlin.math.min(maxDim.toDouble() / bitmap.width, maxDim.toDouble() / bitmap.height)
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, outputStream)
        val byteArray = outputStream.toByteArray()
        android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

fun saveDrawingToBitmap(lines: List<SignatureLine>, width: Int, height: Int): String? {
    if (lines.isEmpty()) return null
    try {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            strokeWidth = 8f
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        lines.forEach { line ->
            if (line.points.size > 1) {
                val path = android.graphics.Path()
                path.moveTo(line.points[0].x, line.points[0].y)
                for (i in 1 until line.points.size) {
                    path.lineTo(line.points[i].x, line.points[i].y)
                }
                canvas.drawPath(path, paint)
            }
        }
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        return null
    }
}

fun persianDigitsToEnglish(input: String): String {
    val persianToEnglishMap = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    return input.map { persianToEnglishMap[it] ?: it }.joinToString("")
}

// --- REDESIGNED SCREEN 1: DASHBOARD ---
fun toPersianDigits(input: String): String {
    val englishToPersianMap = mapOf(
        '0' to '۰', '1' to '۱', '2' to '۲', '3' to '۳', '4' to '۴',
        '5' to '۵', '6' to '۶', '7' to '۷', '8' to '۸', '9' to '۹'
    )
    return input.map { englishToPersianMap[it] ?: it }.joinToString("")
}

fun toPersianDigits(input: Int): String = toPersianDigits(input.toString())

@Composable
fun ActivityItemCard(
    unit: String,
    project: String,
    description: String,
    date: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFE0F2F1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF00695C),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = unit,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF00695C)
                    )
                    Text(
                        text = toPersianDigits(date),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Text(
                    text = project,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// Dashboard screen moved to dashboard/DashboardScreen.kt for better feature modularity.

// --- REDESIGNED SCREEN 2: SETTINGS (with Expandable Group Cards) ---
@Composable
fun RequiredFieldLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = "*", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Red)
    }
}

@Composable
fun WeatherCitySearchDialog(
    onDismiss: () -> Unit,
    onCitySelected: (com.example.weather.GeocodingResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.example.weather.GeocodingResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جستجوی شهر", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        coroutineScope.launch {
                            if (it.length > 2) {
                                isLoading = true
                                results = com.example.weather.WeatherRepository.searchCity(it)
                                isLoading = false
                            } else {
                                results = emptyList()
                            }
                        }
                    },
                    label = { Text("نام شهر (فارسی یا انگلیسی)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        itemsIndexed(results) { _, city ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCitySelected(city) }
                                    .padding(8.dp)
                            ) {
                                Text("${city.name}", fontWeight = FontWeight.Bold)
                                Text("${city.admin1 ?: ""} - ${city.country ?: ""}", fontSize = 12.sp, color = Color.Gray)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

// --- REDESIGNED SCREEN 2: SETTINGS ---
@Composable
fun ProjectSettingsTab(
    viewModel: ReportViewModel,
    importLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    exportLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val backupRestored by viewModel.backupRestoredEvent.collectAsState()
    
    var defaultProject by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("default_project", "") ?: "") }
    var defaultSection by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("default_section", "") ?: "") }
    var defaultPreparedBy by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("default_prepared_by", "") ?: "") }
    var customUnitTitle by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("custom_unit_title", "") ?: "") }
    var defaultWeather by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("default_weather", "") ?: "") }
    var defaultReportType by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("default_report_type", "") ?: "") }
    var defaultAppTheme by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("app_theme", "LIGHT") ?: "LIGHT") }

    var userSignatureBase64 by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("user_signature", "") ?: "") }
    var showSignaturePadDialog by remember { mutableStateOf(false) }

    var isBasicInfoExpanded by remember { mutableStateOf(false) }
    var isWeatherExpanded by remember { mutableStateOf(false) }
    var isThemeExpanded by remember { mutableStateOf(false) }
    var isReportExpanded by remember { mutableStateOf(false) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    // Weather states
    var weatherCityName by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getString("weather_city_name", "بابل") ?: "بابل") }
    var weatherLatitude by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getFloat("weather_latitude", 36.5513f)) }
    var weatherLongitude by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getFloat("weather_longitude", 52.6789f)) }
    var weatherEnabled by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getBoolean("weather_enabled", true)) }
    var weatherForecastEnabled by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getBoolean("weather_forecast_enabled", true)) }
    var weatherAutoUpdate by remember(backupRestored) { mutableStateOf(viewModel.sharedPreferences.getBoolean("weather_auto_update", true)) }
    
    val lastUpdate by viewModel.weatherLastUpdate.collectAsStateWithLifecycle()

    @Composable
    fun SettingsTextFieldWithIcon(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isRequired: Boolean = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { 
                    if (isRequired) RequiredFieldLabel(label) else Text(label, fontSize = 12.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.LightGray,
                    unfocusedLabelColor = Color.Gray
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Colored Header Bar with statusBarsPadding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تنظیمات پیش‌فرض کارگاه",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 96.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                
                // Screen Subtitle/Info Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Transparent, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF00695C), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            text = "تنظیمات سامانه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "برای ثبت گزارش جدید، اطلاعات زیر را وارد کنید.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: مشخصات پایه کارگاه
                    ExpandableSettingsGroup(
                        title = "مشخصات پایه کارگاه",
                        icon = Icons.Default.Domain,
                        isExpanded = isBasicInfoExpanded,
                        onToggle = { isBasicInfoExpanded = !isBasicInfoExpanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Project name
                            SettingsTextFieldWithIcon(
                                value = defaultProject,
                                onValueChange = { defaultProject = it },
                                label = "نام پروژه",
                                icon = Icons.Default.Article,
                                isRequired = true
                            )

                            // Unit/branch
                            SettingsTextFieldWithIcon(
                                value = defaultSection,
                                onValueChange = { defaultSection = it },
                                label = "بخش/واحد کاری",
                                icon = Icons.Default.Description,
                                isRequired = true
                            )

                            // Reporting organization
                            SettingsTextFieldWithIcon(
                                value = defaultPreparedBy,
                                onValueChange = { defaultPreparedBy = it },
                                label = "تنظیم کننده گزارش",
                                icon = Icons.Default.Person,
                                isRequired = true
                            )

                            // All sites/workplaces: "سایر واحدها" with people icon
                            SettingsTextFieldWithIcon(
                                value = customUnitTitle,
                                onValueChange = { customUnitTitle = it },
                                label = "سایر واحدها (گزارش نوع پنجم)",
                                icon = Icons.Default.People
                            )
                        }
                    }

                    // Section: آب و هوا (Open-Meteo)
                    ExpandableSettingsGroup(
                        title = "آب و هوا",
                        icon = Icons.Default.Cloud,
                        isExpanded = isWeatherExpanded,
                        onToggle = { isWeatherExpanded = !isWeatherExpanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نمایش آب و هوا در داشبورد", fontSize = 14.sp)
                                Switch(checked = weatherEnabled, onCheckedChange = { weatherEnabled = it })
                            }
                            
                            AnimatedVisibility(visible = weatherEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    var searchQuery by remember { mutableStateOf(weatherCityName) }
                                    var searchResults by remember { mutableStateOf<List<com.example.weather.GeocodingResult>>(emptyList()) }
                                    var isSearching by remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()
                                    
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { 
                                            searchQuery = it
                                            coroutineScope.launch {
                                                if (it.length > 2) {
                                                    isSearching = true
                                                    try {
                                                        val results = com.example.weather.WeatherRepository.searchCity(it)
                                                        searchResults = results ?: emptyList()
                                                    } catch (e: Exception) {
                                                        searchResults = emptyList()
                                                    } finally {
                                                        isSearching = false
                                                    }
                                                } else {
                                                    searchResults = emptyList()
                                                }
                                            }
                                        },
                                        label = { Text("نام شهر (فارسی یا انگلیسی)", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        trailingIcon = {
                                            if (isSearching) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            } else if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن")
                                                }
                                            } else {
                                                Icon(Icons.Default.Search, contentDescription = "جستجو")
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = Color.LightGray,
                                            unfocusedLabelColor = Color.Gray
                                        )
                                    )
                                    
                                    AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            LazyColumn {
                                                items(searchResults.size) { index ->
                                                    val city = searchResults[index]
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                weatherCityName = city.name ?: ""
                                                                searchQuery = weatherCityName
                                                                weatherLatitude = city.latitude?.toFloat() ?: 0f
                                                                weatherLongitude = city.longitude?.toFloat() ?: 0f
                                                                searchResults = emptyList()
                                                            }
                                                            .padding(12.dp)
                                                    ) {
                                                        Text(city.name ?: "", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text("${city.admin1 ?: ""} - ${city.country ?: ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    if (index < searchResults.size - 1) {
                                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("پیش‌بینی ۷ روزه", fontSize = 14.sp)
                                        Switch(checked = weatherForecastEnabled, onCheckedChange = { weatherForecastEnabled = it })
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("بروزرسانی خودکار در شروع", fontSize = 14.sp)
                                        Switch(checked = weatherAutoUpdate, onCheckedChange = { weatherAutoUpdate = it })
                                    }
                                    
                                    if (lastUpdate.isNotEmpty()) {
                                        Text(
                                            text = "آخرین بروزرسانی: $lastUpdate",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: تم و ظاهر برنامه
                    ExpandableSettingsGroup(
                        title = "تم و ظاهر برنامه",
                        icon = Icons.Default.Palette,
                        isExpanded = isThemeExpanded,
                        onToggle = { isThemeExpanded = !isThemeExpanded }
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                val themes = listOf(
                                    Triple("LIGHT", "روشن", Icons.Default.WbSunny),
                                    Triple("DARK", "تیره", Icons.Default.DarkMode),
                                    Triple("SYSTEM", "سیستم", Icons.Default.SettingsSystemDaydream)
                                )
                                
                                themes.forEachIndexed { index, (key, label, icon) ->
                                    val isSelected = defaultAppTheme == key
                                    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(backgroundColor, if (isSelected) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp))
                                            .border(if (isSelected) 1.dp else 0.dp, borderColor, RoundedCornerShape(16.dp))
                                            .clickable { defaultAppTheme = key }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(icon, null, modifier = Modifier.size(16.dp), tint = contentColor)
                                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: دسته‌بندی و گزارش‌نویسی پیشنهادی
                    ExpandableSettingsGroup(
                        title = "دسته‌بندی و گزارش‌نویسی پیشنهادی",
                        icon = Icons.Default.Category,
                        isExpanded = isReportExpanded,
                        onToggle = { isReportExpanded = !isReportExpanded }
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val categories = listOf(
                                Triple("ASK", "همیشه بپرس", Icons.Default.Home),
                                Triple("EXECUTION", "اجرا", Icons.Default.Domain),
                                Triple("SURVEY", "نقشه‌برداری", Icons.Default.Map),
                                Triple("TECHNICAL", "دفتر فنی", Icons.Default.Description),
                                Triple("HSE", "ایمنی HSE", Icons.Default.Security),
                                Triple("LEGAL", "حقوقی", Icons.Default.Gavel),
                                Triple("WAREHOUSE", "انبار", Icons.Default.Store),
                                Triple("CUSTOM", "سایر واحدها", Icons.Default.Build)
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                categories.chunked(3).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { (key, label, icon) ->
                                            val isSelected = defaultReportType == key
                                            val borderStroke = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                            val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { defaultReportType = key }
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                                border = borderStroke
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = contentColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = label,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 11.sp,
                                                        color = contentColor
                                                    )
                                                }
                                            }
                                        }
                                        // Fill empty spaces if a row has less than 3 items
                                        if (rowItems.size < 3) {
                                            repeat(3 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                }
                            }
                        }
                    }
                }

                // Section 4: گزینه‌های پیشرفته (امضا و بکاپ)
                ExpandableSettingsGroup(
                    title = "امضا و گزینه‌های پیشرفته",
                    icon = Icons.Default.Palette,
                    isExpanded = isAdvancedExpanded,
                    onToggle = { isAdvancedExpanded = !isAdvancedExpanded }
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Digital Signature section
                        Text("امضای دیجیتال تنظیم‌کننده گزارش:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (userSignatureBase64.isNotEmpty()) {
                            val bitmap = remember(userSignatureBase64) { base64ToBitmap(userSignatureBase64) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "امضای دیجیتال",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showSignaturePadDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ترسیم امضا دیجیتال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (userSignatureBase64.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        userSignatureBase64 = ""
                                        viewModel.sharedPreferences.edit().remove("user_signature").apply()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("حذف امضا", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))

                        // Backup and restore
                        Text("نسخه‌ پشتیبان و بازیابی:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        exportLauncher.launch("DailyReportsBackup.json")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطا در ذخیره فایل", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("پشتیبان‌گیری (JSON)", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    try {
                                        importLauncher.launch("application/json")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطا در بارگذاری فایل", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("بازیابی اطلاعات", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Full-width save button
                Button(
                    onClick = {
                        viewModel.sharedPreferences.edit()
                            .putString("default_project", defaultProject)
                            .putString("default_section", defaultSection)
                            .putString("default_prepared_by", defaultPreparedBy)
                            .putString("custom_unit_title", customUnitTitle)
                            .putString("default_weather", defaultWeather)
                            .putString("default_report_type", defaultReportType)
                            .putString("app_theme", defaultAppTheme)
                            .putString("weather_city_name", weatherCityName)
                            .putFloat("weather_latitude", weatherLatitude)
                            .putFloat("weather_longitude", weatherLongitude)
                            .putBoolean("weather_enabled", weatherEnabled)
                            .putBoolean("weather_forecast_enabled", weatherForecastEnabled)
                            .putBoolean("weather_auto_update", weatherAutoUpdate)
                            .apply()

                        Toast.makeText(context, "تنظیمات با موفقیت ذخیره و نهایی شد ✅", Toast.LENGTH_SHORT).show()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, shape = RoundedCornerShape(16.dp))
                        .testTag("save_settings_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ذخیره و نهایی تنظیمات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    }



    // Signature drawing board
    if (showSignaturePadDialog) {
        val lines = remember { mutableStateListOf<SignatureLine>() }
        val currentPoints = remember { mutableStateListOf<Offset>() }
        var canvasWidth by remember { mutableStateOf(500) }
        var canvasHeight by remember { mutableStateOf(250) }
        
        AlertDialog(
            onDismissRequest = { showSignaturePadDialog = false },
            title = {
                Text(
                    "ترسیم امضای الکترونیکی جدید",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "با انگشت خود در کادر سفید زیر امضا کنید:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.5.dp, BorderColor, RoundedCornerShape(8.dp))
                            .onSizeChanged { size ->
                                canvasWidth = size.width
                                canvasHeight = size.height
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        currentPoints.clear()
                                        currentPoints.add(startOffset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints.add(change.position)
                                    },
                                    onDragEnd = {
                                        if (currentPoints.isNotEmpty()) {
                                            lines.add(SignatureLine(currentPoints.toList()))
                                            currentPoints.clear()
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            lines.forEach { line ->
                                for (i in 0 until line.points.size - 1) {
                                    drawLine(
                                        color = Color.Black,
                                        start = line.points[i],
                                        end = line.points[i + 1],
                                        strokeWidth = 6f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            if (currentPoints.size > 1) {
                                for (i in 0 until currentPoints.size - 1) {
                                    drawLine(
                                        color = Color.Black,
                                        start = currentPoints[i],
                                        end = currentPoints[i + 1],
                                        strokeWidth = 6f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                lines.clear()
                                currentPoints.clear()
                            }
                        ) {
                            Text("پاک کردن صفحه", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        
                        Text(
                            text = "${lines.size} خط رسم شد",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (lines.isNotEmpty()) {
                            val base64 = saveDrawingToBitmap(lines, canvasWidth, canvasHeight)
                            if (base64 != null) {
                                userSignatureBase64 = base64
                                viewModel.sharedPreferences.edit().putString("user_signature", base64).apply()
                                Toast.makeText(context, "امضای الکترونیکی جدید ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showSignaturePadDialog = false
                    },
                    enabled = lines.isNotEmpty()
                ) {
                    Text("تایید و ذخیره", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignaturePadDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
}

// Expandable Section Card Helper
@Composable
fun ExpandableSettingsGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    content()
                }
            }
        }
    }
}


// --- REDESIGNED SCREEN 3: ABOUT (Premium wave design and overlapping card) ---
class WaveShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - 40f)
            quadraticBezierTo(
                size.width / 2f, size.height + 40f,
                0f, size.height - 40f
            )
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
fun AboutAppTab(viewModel: ReportViewModel) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Teal gradient wave background at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    ),
                    shape = WaveShape()
                )
        )
        
        // Overlapping scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header Content (App Icon, App Name, Version badge, Developer info)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // App icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .shadow(4.dp, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Engineering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                }
                
                // App Name
                Text(
                    text = "سامانه گزارشیار کارگاه",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                
                // Version Badge
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "نسخه ۳.۰.۸",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
                
                // Developer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "سازنده برنامه:",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "مصطفی عرفانی",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            // Overlapping card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(4.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Paragraph 1
                    Text(
                        text = "برنامه گزارشیار ابزار هوشمند مهندسی برای جمع‌آوری، ثبت، پیگیری و مدیریت گزارش‌های روزانه و مستندات کارگاهی است.",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Justify
                    )
                    
                    // Paragraph 2
                    Text(
                        text = "با کمک این سیستم، می‌توانید گزارش‌های خود را ثبت کرده و خروجی‌های PDF دقیق و استاندارد تولید کنید.",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Justify
                    )
                    
                    Text(
                        text = "✨ قابلیت‌های جدید (نسخه ۳.۰.۸):\n- رفع اشکال عدم نمایش لیست استعلامات در خروجی PDF.\n- قابلیت افزودن، ویرایش و حذف استعلامات در بخش حقوقی.\n- پشتیبان‌گیری استاندارد فایل JSON به جای متن قابل کپی.\n- اصلاح آیکون اصلی در نوار داشبورد.\n- دریافت و نمایش خودکار وضعیت آب‌وهوا با امکان به‌روزرسانی.\n- امکان پیوست تا ۴ تصویر با کیفیت بالا برای هر گزارش.",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Justify
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Primary button
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "message/rfc822"
                                    putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("MOSTAFA5804@GMAIL.COM"))
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "ارتباط با سازنده سامانه گزارش یار")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "با سلام و احترام،\n\n")
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "ارتباط با سازنده"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "سیستم ارسال ایمیل یافت نشد ✉️", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = "ارتباط با سازنده برنامه",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    // Secondary button
                    OutlinedButton(
                        onClick = {
                            try {
                                val backupJson = viewModel.exportBackup()
                                val backupFile = java.io.File(context.cacheDir, "daily_report_backup.json")
                                backupFile.writeText(backupJson)
                                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "com.aistudio.civilsync.fileprovider",
                                    backupFile
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "message/rfc822"
                                    putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("MOSTAFA5804@GMAIL.COM"))
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "پشتیبان داده‌های کارگاه - نسخه ۳.۰.۸")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "با سلام،\nپشتیبان اطلاعات پروژه‌های من در فایل پیوست قرار دارد.")
                                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "ارسال پشتیبان اطلاعات"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "سیستم ارسال ایمیل یافت نشد ✉️", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = "ارسال مستقیم فایل پشتیبانی (JSON)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

// --- REDESIGNED SCREEN 3: REPORTS LIST SCREEN ---

enum class ReportViewMode { LIST, COMPACT, MONTHLY }

@Composable
fun ReportListScreen(
    viewModel: ReportViewModel,
    onEditReport: (DailyReport) -> Unit
) {
    val context = LocalContext.current
    val reports by viewModel.filteredReports.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    
    var viewMode by remember { mutableStateOf(ReportViewMode.LIST) }
    val customUnitTitle = remember { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
    var showCreateDialog by remember { mutableStateOf(false) }

    val displayedReports = reports

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // Top App Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            Toast.makeText(context, "بخش تنظیمات کارگاه ⚙️", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "تنظیمات کارگاه", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "بایگانی گزارش‌های کارگاه",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            floatingActionButton = {
                val backupRestored by viewModel.backupRestoredEvent.collectAsState()
                val defaultReportType = remember(backupRestored) { viewModel.sharedPreferences.getString("default_report_type", "ASK") ?: "ASK" }
                FloatingActionButton(
                    onClick = {
                        if (defaultReportType == "ASK" || defaultReportType.isEmpty()) {
                            showCreateDialog = true
                        } else {
                            viewModel.startNewReport(defaultReportType)
                            Toast.makeText(context, "گزارش روزانه جدید ایجاد شد 📝", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("add_report_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "گزارش جدید")
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                // Screen title & subtitle: "گزارشها" & "همه گزارشهای ثبت شده"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "گزارشها",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "همه گزارشهای ثبت شده",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Search Bar Row (search icon right, filter icon left)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("جستجو در گزارشها...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_reports_input"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        singleLine = true
                    )
                    
                    IconButton(
                        onClick = {
                            viewMode = when (viewMode) {
                                ReportViewMode.LIST -> ReportViewMode.COMPACT
                                ReportViewMode.COMPACT -> ReportViewMode.MONTHLY
                                ReportViewMode.MONTHLY -> ReportViewMode.LIST
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ) {
                        val viewIcon = when (viewMode) {
                            ReportViewMode.LIST -> Icons.Default.ViewList
                            ReportViewMode.COMPACT -> Icons.Default.ViewAgenda
                            ReportViewMode.MONTHLY -> Icons.Default.CalendarMonth
                        }
                        Icon(
                            imageVector = viewIcon,
                            contentDescription = "تغییر حالت نمایش",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "فیلتر و مرتب‌سازی فعال است 🔍", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "فیلتر",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reports Card List
                if (displayedReports.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                            }
                            Text(
                                text = "موردی یافت نشد",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "عبارت دیگری را امتحان کنید.",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                } else {
                    val expandedMonths = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (viewMode == ReportViewMode.MONTHLY) {
                            val grouped = displayedReports.groupBy { 
                                val parts = it.date.split("/")
                                if (parts.size >= 2) "${parts[0]}/${parts[1]}" else "نامشخص" 
                            }
                            grouped.forEach { (month, monthReports) ->
                                item {
                                    val isExpanded = expandedMonths[month] ?: true
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedMonths[month] = !isExpanded }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "گزارش‌های ماه: $month",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "بسط دادن"
                                        )
                                    }
                                }
                                if (expandedMonths[month] != false) {
                                    items(count = monthReports.size) { index ->
                                        val report = monthReports[index]
                                        ReportCardItem(report, viewMode, customUnitTitle, context, onEditReport, viewModel)
                                    }
                                }
                            }
                        } else {
                            items(count = displayedReports.size) { index ->
                                val report = displayedReports[index]
                                ReportCardItem(report, viewMode, customUnitTitle, context, onEditReport, viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Create Report Type Picker Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("انتخاب واحد و نوع گزارش روزانه", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val types = listOf(
                            "EXECUTION" to Pair("گزارش اجرایی کارگاه", "🔨"),
                            "WAREHOUSE" to Pair("گزارش ورود و خروج انبار", "📦"),
                            "TECHNICAL" to Pair("گزارش دفتر فنی", "📐"),
                            "HSE" to Pair("گزارش ایمنی HSE", "🛡️"),
                            "SURVEY" to Pair("گزارش نقشه‌برداری", "🗺️"),
                            "LEGAL" to Pair("گزارش حقوقی و تحصیل اراضی", "⚖️"),
                            "CUSTOM" to Pair("سایر واحدها (سفارشی)", "⚙️")
                        )
                        types.forEach { (type, data) ->
                            Button(
                                onClick = {
                                    viewModel.startNewReport(type)
                                    showCreateDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(data.second, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(data.first, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("انصراف", color = MaterialTheme.colorScheme.error)
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

// Helper to get report type info
fun getReportTypeInfo(type: String, customUnitTitle: String): Pair<String, Color> {
    return when (type) {
        "WAREHOUSE" -> "انبارداری 📦" to Color(0xFFD97706)
        "LEGAL" -> "حقوقی و تملک ⚖️" to Color(0xFF9333EA)
        "SURVEY" -> "نقشه‌برداری 📐" to Color(0xFF0D9488)
        "TECHNICAL" -> "دفتر فنی 📐" to Color(0xFF2563EB)
        "HSE" -> "ایمنی HSE 🛡️" to Color(0xFFDC2626)
        "CUSTOM" -> customUnitTitle to Color(0xFF4B5563)
        else -> "اجرایی 🔨" to Color(0xFF059669)
    }
}

// --- REDESIGNED SCREEN 4: REPORT EDITOR SCREEN ---
@Composable
fun ReportEditorScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit
) {
    val report by viewModel.currentReport.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val currentReport = report ?: return

    val customUnitTitle = remember { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
    val currentUnitTitle = when (currentReport.reportType) {
        "WAREHOUSE" -> "انبار"
        "LEGAL" -> "حقوقی"
        "SURVEY" -> "نقشه‌برداری"
        "TECHNICAL" -> "دفتر فنی"
        "HSE" -> "ایمنی"
        "CUSTOM" -> customUnitTitle
        else -> "اجرایی"
    }

    var selectedTab by rememberSaveable(currentReport.reportType) { mutableStateOf(0) }
    val tabs = when (currentReport.reportType) {
        "LEGAL" -> listOf(
            "مشخصات پایه" to Icons.Default.Info,
            "تحصیل اراضی" to Icons.Default.Gavel,
            "مجوز های قانونی" to Icons.Default.Assignment,
            "ماشین‌آلات" to Icons.Default.DirectionsCar,
            "پرسنل حقوقی" to Icons.Default.People,
            "گالری تصاویر" to Icons.Default.PhotoCamera,
            "یادداشت‌ها" to Icons.Default.Edit
        )
        "SURVEY" -> listOf(
            "مشخصات پایه" to Icons.Default.Info,
            "کارهای برداشت" to Icons.Default.Map,
            "تجهیزات نقشه‌برداری" to Icons.Default.Agriculture,
            "پرسنل نقشه‌برداری" to Icons.Default.People,
            "گالری تصاویر" to Icons.Default.PhotoCamera,
            "یادداشت‌ها" to Icons.Default.Edit
        )
        "TECHNICAL" -> listOf(
            "مشخصات پایه" to Icons.Default.Info,
            "فعالیت $currentUnitTitle" to Icons.Default.Description,
            "مصالح $currentUnitTitle" to Icons.Default.Widgets,
            "ماشین‌آلات" to Icons.Default.DirectionsCar,
            "پرسنل $currentUnitTitle" to Icons.Default.People,
            "گالری تصاویر" to Icons.Default.PhotoCamera,
            "یادداشت‌ها" to Icons.Default.Edit
        )
        "WAREHOUSE" -> listOf(
            "مشخصات پایه" to Icons.Default.Info,
            "مصالح ورودی" to Icons.Default.ListAlt,
            "مصالح خروجی" to Icons.Default.LocalShipping,
            "ماشین‌آلات" to Icons.Default.DirectionsCar,
            "پرسنل انبار" to Icons.Default.People,
            "گالری تصاویر" to Icons.Default.PhotoCamera,
            "یادداشت‌ها" to Icons.Default.Edit
        )
        else -> listOf(
            "مشخصات پایه" to Icons.Default.Info,
            "فعالیت $currentUnitTitle" to Icons.Default.Build,
            "مصالح $currentUnitTitle" to Icons.Default.Inventory,
            "ماشین‌آلات" to Icons.Default.DirectionsCar,
            "پرسنل $currentUnitTitle" to Icons.Default.People,
            "گالری تصاویر" to Icons.Default.PhotoCamera,
            "یادداشت‌ها" to Icons.Default.Edit
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, "بازگشت", tint = MaterialTheme.colorScheme.primary)
                                }
                                Column {
                                    Text(
                                        text = "ویرایش گزارش $currentUnitTitle 📝",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row {
                                // Direct Share (Telegram, etc.)
                                IconButton(
                                    onClick = {
                                        viewModel.generateAndSharePdfFileDirectly(context, currentReport) { success ->
                                            if (!success) {
                                                Toast.makeText(context, "خطا در اشتراک‌گذاری مستقیم گزارش ❌", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Share, "اشتراک‌گذاری مستقیم", tint = MaterialTheme.colorScheme.primary)
                                }

                                // Print/Save local PDF
                                IconButton(
                                    onClick = {
                                        viewModel.generateAndSharePdf(context, currentReport) { success ->
                                            if (!success) {
                                                Toast.makeText(context, "خطا در خروجی PDF ❌", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Print, "چاپ / خروجی PDF", tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.saveCurrentReportImmediately()
                                        Toast.makeText(context, "گزارش با موفقیت در دیتابیس ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Check, "ذخیره نهایی", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp,
                            shadowElevation = 0.dp
                        ) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedTab,
                                edgePadding = 12.dp,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary,
                                divider = {},
                                indicator = { tabPositions ->
                                    if (selectedTab < tabPositions.size) {
                                        TabRowDefaults.Indicator(
                                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = MaterialTheme.colorScheme.primary,
                                            height = 4.dp
                                        )
                                    }
                                }
                            ) {
                                tabs.forEachIndexed { index, (title, icon) ->
                                    val selected = selectedTab == index
                                    Tab(
                                        selected = selected,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            ) {
                                                Icon(
                                                    icon,
                                                    contentDescription = title,
                                                    modifier = Modifier.padding(6.dp).size(18.dp)
                                                )
                                            }
                                        },
                                        text = { Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        selectedContentColor = MaterialTheme.colorScheme.primary,
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .align(Alignment.TopCenter)
                ) {
                    if (currentReport.reportType == "SURVEY") {
                        when (selectedTab) {
                            0 -> BaseInfoTabRedesigned(viewModel, currentReport, onNext = { selectedTab = 1 })
                            1 -> TasksTabRedesigned(viewModel, currentReport)
                            2 -> MachineryTabRedesigned(viewModel, currentReport)
                            3 -> ManpowerTabRedesigned(viewModel, currentReport)
                            4 -> PhotoGalleryTabRedesigned(viewModel, currentReport)
                            5 -> NotesTabRedesigned(viewModel, currentReport, onBack)
                        }
                    } else if (currentReport.reportType == "LEGAL") {
                        when (selectedTab) {
                            0 -> BaseInfoTabRedesigned(viewModel, currentReport, onNext = { selectedTab = 1 })
                            1 -> TasksTabRedesigned(viewModel, currentReport)
                            2 -> LegalPermitsTabRedesigned(viewModel, currentReport)
                            3 -> MachineryTabRedesigned(viewModel, currentReport)
                            4 -> ManpowerTabRedesigned(viewModel, currentReport)
                            5 -> PhotoGalleryTabRedesigned(viewModel, currentReport)
                            6 -> NotesTabRedesigned(viewModel, currentReport, onBack)
                        }
                    } else {
                        when (selectedTab) {
                            0 -> BaseInfoTabRedesigned(viewModel, currentReport, onNext = { selectedTab = 1 })
                            1 -> TasksTabRedesigned(viewModel, currentReport)
                            2 -> MaterialsTabRedesigned(viewModel, currentReport)
                            3 -> MachineryTabRedesigned(viewModel, currentReport)
                            4 -> ManpowerTabRedesigned(viewModel, currentReport)
                            5 -> PhotoGalleryTabRedesigned(viewModel, currentReport)
                            6 -> NotesTabRedesigned(viewModel, currentReport, onBack)
                        }
                    }
                }
            }
        }
    }
}

// --- REDESIGNED SCREEN 4-1: BASE INFO TAB ---
@Composable
fun BaseInfoTab(viewModel: ReportViewModel, report: DailyReport) {
    val context = LocalContext.current
    var showWeatherPicker by remember { mutableStateOf(false) }

    val formatKmHelper = { input: String ->
        val cleaned = input.filter { it.isDigit() }
        if (cleaned.isEmpty()) "" else {
            val num = cleaned.toLong()
            val km = num / 1000
            val m = num % 1000
            "$km+${String.format("%03d", m)}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("اطلاعات عمومی و شرایط کاری", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = report.date,
                    onValueChange = { newValue -> viewModel.updateCurrentReport { it.copy(date = newValue) } },
                    label = { Text("تاریخ گزارش") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Weather Row Selection Button
                Button(
                    onClick = { showWeatherPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f), contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.WbSunny, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("آب و هوا: ${report.weather}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("موانع، مشکلات و برنامه‌های آتی", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = report.obstacles,
                    onValueChange = { newValue -> viewModel.updateCurrentReport { it.copy(obstacles = newValue) } },
                    label = { Text("موانع و مشکلات پیش رو ⚠️") },
                    placeholder = { Text("کمبود لودر، فرسودگی بازوها یا عدم تملک زمین...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = report.tomorrowPlan,
                    onValueChange = { newValue -> viewModel.updateCurrentReport { it.copy(tomorrowPlan = newValue) } },
                    label = { Text("برنامه کاری روز آینده 🗓️") },
                    placeholder = { Text("ادامه بتن‌ریزی فونداسیون پایه ۲ و ترانشه‌برداری...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }

    if (showWeatherPicker) {
        val options = listOf("آفتابی ☀️", "ابری ☁️", "نیمه ابری ⛅", "بارانی 🌧️", "برفی ❄️", "طوفانی 💨", "غبارآلود 🌫️")
        AlertDialog(
            onDismissRequest = { showWeatherPicker = false },
            title = { Text("انتخاب وضعیت جوی کارگاه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { opt ->
                        TextButton(
                            onClick = {
                                viewModel.updateCurrentReport { it.copy(weather = opt) }
                                showWeatherPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(opt, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// --- REDESIGNED SCREEN 4-2: TASKS TAB ---
@Composable
fun TasksTab(viewModel: ReportViewModel, report: DailyReport) {
    val tasks = report.tasks

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                viewModel.updateCurrentReport { it.copy(tasks = it.tasks + TaskEntry()) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("افزودن فعالیت اجرایی جدید", fontWeight = FontWeight.Bold)
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("فعالیتی ثبت نشده است. دکمه بالا را برای ثبت لمس کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            tasks.forEachIndexed { index, task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("فعالیت #${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.5.sp)
                            IconButton(
                                onClick = {
                                    val updated = tasks.filterIndexed { i, _ -> i != index }
                                    viewModel.updateCurrentReport { it.copy(tasks = updated) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }

                        OutlinedTextField(
                            value = task.description,
                            onValueChange = { v ->
                                val updated = tasks.toMutableList().apply { this[index] = this[index].copy(description = v) }
                                viewModel.updateCurrentReport { it.copy(tasks = updated) }
                            },
                            label = { Text("شرح عملیات کاری") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = task.location,
                                onValueChange = { v ->
                                    val updated = tasks.toMutableList().apply { this[index] = this[index].copy(location = v) }
                                    viewModel.updateCurrentReport { it.copy(tasks = updated) }
                                },
                                label = { Text("محل دقیق") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = task.quantity,
                                onValueChange = { v ->
                                    val updated = tasks.toMutableList().apply { this[index] = this[index].copy(quantity = v) }
                                    viewModel.updateCurrentReport { it.copy(tasks = updated) }
                                },
                                label = { Text("مقدار") },
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = task.unit,
                                onValueChange = { v ->
                                    val updated = tasks.toMutableList().apply { this[index] = this[index].copy(unit = v) }
                                    viewModel.updateCurrentReport { it.copy(tasks = updated) }
                                },
                                label = { Text("واحد") },
                                modifier = Modifier.weight(0.6f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- REDESIGNED SCREEN 4-3: MANPOWER TAB ---
@Composable
fun ManpowerTab(viewModel: ReportViewModel, report: DailyReport) {
    val manpower = report.manpower

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                viewModel.updateCurrentReport { it.copy(manpower = it.manpower + ManpowerEntry()) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("افزودن نیروی انسانی جدید", fontWeight = FontWeight.Bold)
        }

        if (manpower.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("آمار نیرویی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            manpower.forEachIndexed { index, person ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("پرسنل #${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.5.sp)
                            IconButton(
                                onClick = {
                                    val updated = manpower.filterIndexed { i, _ -> i != index }
                                    viewModel.updateCurrentReport { it.copy(manpower = updated) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }

                        OutlinedTextField(
                            value = person.name,
                            onValueChange = { v ->
                                val updated = manpower.toMutableList().apply { this[index] = this[index].copy(name = v) }
                                viewModel.updateCurrentReport { it.copy(manpower = updated) }
                            },
                            label = { Text("نام یا اکیپ") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = person.role,
                                onValueChange = { v ->
                                    val updated = manpower.toMutableList().apply { this[index] = this[index].copy(role = v) }
                                    viewModel.updateCurrentReport { it.copy(manpower = updated) }
                                },
                                label = { Text("سمت / تخصص") },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            // Count with + / -
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        if (person.count > 1) {
                                            val updated = manpower.toMutableList().apply { this[index] = this[index].copy(count = this[index].count - 1) }
                                            viewModel.updateCurrentReport { it.copy(manpower = updated) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(text = person.count.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                IconButton(
                                    onClick = {
                                        val updated = manpower.toMutableList().apply { this[index] = this[index].copy(count = this[index].count + 1) }
                                        viewModel.updateCurrentReport { it.copy(manpower = updated) }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = person.employmentType == "COMPANY",
                                    onCheckedChange = { company ->
                                        val type = if (company) "COMPANY" else "SUBCONTRACTOR"
                                        val updated = manpower.toMutableList().apply { this[index] = this[index].copy(employmentType = type) }
                                        viewModel.updateCurrentReport { it.copy(manpower = updated) }
                                    }
                                )
                                Text("نیروی شرکتی (امانی)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = person.isOnLeave,
                                    onCheckedChange = { leave ->
                                        val updated = manpower.toMutableList().apply { this[index] = this[index].copy(isOnLeave = leave) }
                                        viewModel.updateCurrentReport { it.copy(manpower = updated) }
                                    }
                                )
                                Text("در مرخصی", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- REDESIGNED SCREEN 4-4: MACHINERY TAB ---
@Composable
fun MachineryTab(viewModel: ReportViewModel, report: DailyReport) {
    val machinery = report.machinery

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                viewModel.updateCurrentReport { it.copy(machinery = it.machinery + MachineryEntry()) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("افزودن ماشین‌آلات کارگاهی جدید", fontWeight = FontWeight.Bold)
        }

        if (machinery.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("هیچ دستگاه ماشین‌آلاتی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            machinery.forEachIndexed { index, m ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("دستگاه #${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.5.sp)
                            IconButton(
                                onClick = {
                                    val updated = machinery.filterIndexed { i, _ -> i != index }
                                    viewModel.updateCurrentReport { it.copy(machinery = updated) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }

                        OutlinedTextField(
                            value = m.type,
                            onValueChange = { v ->
                                val updated = machinery.toMutableList().apply { this[index] = this[index].copy(type = v) }
                                viewModel.updateCurrentReport { it.copy(machinery = updated) }
                            },
                            label = { Text("نوع و مدل تجهیزات (مثلا لودر کوماتسو)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Active Count
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("فعال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (m.activeCount > 0) {
                                            val updated = machinery.toMutableList().apply { this[index] = this[index].copy(activeCount = this[index].activeCount - 1) }
                                            viewModel.updateCurrentReport { it.copy(machinery = updated) }
                                        }
                                    }) { Icon(Icons.Default.Remove, null) }
                                    Text(m.activeCount.toString(), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val updated = machinery.toMutableList().apply { this[index] = this[index].copy(activeCount = this[index].activeCount + 1) }
                                        viewModel.updateCurrentReport { it.copy(machinery = updated) }
                                    }) { Icon(Icons.Default.Add, null) }
                                }
                            }

                            // Inactive Count
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("خراب/غیرفعال", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (m.inactiveCount > 0) {
                                            val updated = machinery.toMutableList().apply { this[index] = this[index].copy(inactiveCount = this[index].inactiveCount - 1) }
                                            viewModel.updateCurrentReport { it.copy(machinery = updated) }
                                        }
                                    }) { Icon(Icons.Default.Remove, null) }
                                    Text(m.inactiveCount.toString(), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val updated = machinery.toMutableList().apply { this[index] = this[index].copy(inactiveCount = this[index].inactiveCount + 1) }
                                        viewModel.updateCurrentReport { it.copy(machinery = updated) }
                                    }) { Icon(Icons.Default.Add, null) }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = m.workingHours,
                                onValueChange = { v ->
                                    val updated = machinery.toMutableList().apply { this[index] = this[index].copy(workingHours = v) }
                                    viewModel.updateCurrentReport { it.copy(machinery = updated) }
                                },
                                label = { Text("ساعت کارکرد") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = m.comments,
                                onValueChange = { v ->
                                    val updated = machinery.toMutableList().apply { this[index] = this[index].copy(comments = v) }
                                    viewModel.updateCurrentReport { it.copy(machinery = updated) }
                                },
                                label = { Text("توضیحات وضعیت") },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- REDESIGNED SCREEN 4-5: MATERIALS TAB ---
@Composable
fun MaterialsTab(viewModel: ReportViewModel, report: DailyReport) {
    val materials = report.materials

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                viewModel.updateCurrentReport { it.copy(materials = it.materials + MaterialEntry()) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("افزودن مصالح وارده جدید", fontWeight = FontWeight.Bold)
        }

        if (materials.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("آمار ورود مصالح ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            materials.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("محموله مصالح #${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.5.sp)
                            IconButton(
                                onClick = {
                                    val updated = materials.filterIndexed { i, _ -> i != index }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }

                        OutlinedTextField(
                            value = item.type,
                            onValueChange = { v ->
                                val updated = materials.toMutableList().apply { this[index] = this[index].copy(type = v) }
                                viewModel.updateCurrentReport { it.copy(materials = updated) }
                            },
                            label = { Text("نوع مصالح (مثلا سیمان تیپ ۲)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = item.count,
                                onValueChange = { v ->
                                    val updated = materials.toMutableList().apply { this[index] = this[index].copy(count = v) }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                },
                                label = { Text("مقدار / تعداد") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = item.unit,
                                onValueChange = { v ->
                                    val updated = materials.toMutableList().apply { this[index] = this[index].copy(unit = v) }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                },
                                label = { Text("واحد") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = item.unloadingLocation,
                                onValueChange = { v ->
                                    val updated = materials.toMutableList().apply { this[index] = this[index].copy(unloadingLocation = v) }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                },
                                label = { Text("محل تخلیه کارگاه") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = item.unloadingTime,
                                onValueChange = { v ->
                                    val updated = materials.toMutableList().apply { this[index] = this[index].copy(unloadingTime = v) }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                },
                                label = { Text("ساعت ورود") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- REDESIGNED SCREEN 4-6: WAREHOUSE MATERIALS TAB ---
@Composable
fun WarehouseMaterialsTab(viewModel: ReportViewModel, report: DailyReport) {
    val materials = report.materials

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                viewModel.updateCurrentReport { it.copy(materials = it.materials + MaterialEntry()) }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("افزودن سند جدید ورود/خروج انبار 📦", fontWeight = FontWeight.Bold)
        }

        if (materials.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("موجودی یا ثبت انبارداری برای این روز ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            materials.forEachIndexed { index, ledgerItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("کالا/کد انبار #${index + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 12.5.sp)
                            IconButton(
                                onClick = {
                                    val updated = materials.filterIndexed { i, _ -> i != index }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }

                        OutlinedTextField(
                            value = ledgerItem.type,
                            onValueChange = { v ->
                                val updated = materials.toMutableList().apply { this[index] = this[index].copy(type = v) }
                                viewModel.updateCurrentReport { it.copy(materials = updated) }
                            },
                            label = { Text("عنوان کالا یا مصالح") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ledgerItem.count,
                                onValueChange = { v ->
                                    val updated = materials.toMutableList().apply { this[index] = this[index].copy(count = v) }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                },
                                label = { Text("تعداد / مقدار کالا") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = ledgerItem.unit,
                                onValueChange = { v ->
                                    val updated = materials.toMutableList().apply { this[index] = this[index].copy(unit = v) }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                },
                                label = { Text("واحد اندازه‌گیری") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = ledgerItem.isExit,
                                    onCheckedChange = { exit ->
                                        val updated = materials.toMutableList().apply { this[index] = this[index].copy(isExit = exit) }
                                        viewModel.updateCurrentReport { it.copy(materials = updated) }
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (ledgerItem.isExit) "سند خروجی کالا (حواله انبار)" else "سند ورودی کالا (رسید انبار)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ledgerItem.isExit) MaterialTheme.colorScheme.error else Color(0xFF059669)
                                )
                            }
                        }

                        if (ledgerItem.isExit) {
                            OutlinedTextField(
                                value = ledgerItem.receiver,
                                onValueChange = { v ->
                                    val updated = materials.toMutableList().apply { this[index] = this[index].copy(receiver = v) }
                                    viewModel.updateCurrentReport { it.copy(materials = updated) }
                                },
                                label = { Text("نام اکیپ دریافت‌کننده حواله") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- REDESIGNED SCREEN 5: MAIN APP SCREEN SHELL ---
@Composable
fun MainAppScreen(
    viewModel: ReportViewModel,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val reports by viewModel.filteredReports.collectAsStateWithLifecycle()
    val currentReport by viewModel.currentReport.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Navigation Tab state (0: Dashboard, 1: Reports Archive, 2: Settings, 3: About)
    var selectedTab by remember { mutableStateOf(0) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                if (json != null) {
                    viewModel.importBackup(
                        json,
                        onSuccess = { count ->
                            Toast.makeText(context, "$count گزارش با موفقیت بازیابی شد ✅", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, "خطا در بازیابی: $err", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطا در بازیابی فایل بکاپ ❌", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val exported = viewModel.exportBackup()
                context.contentResolver.openOutputStream(uri)?.use { 
                    it.write(exported.toByteArray())
                }
                Toast.makeText(context, "نسخه پشتیبان با موفقیت ذخیره شد ✅", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "خطا در ذخیره فایل بکاپ ❌", Toast.LENGTH_SHORT).show()
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (currentReport != null) {
            ReportEditorScreen(
                viewModel = viewModel,
                onBack = { viewModel.clearCurrentReport() }
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = "داشبورد") },
                            label = { Text("داشبورد", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("nav_dashboard")
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.List, contentDescription = "گزارش‌ها") },
                            label = { Text("گزارش‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.testTag("nav_reports")
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") },
                            label = { Text("تنظیمات", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.testTag("nav_settings")
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Info, contentDescription = "درباره") },
                            label = { Text("درباره", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            modifier = Modifier.testTag("nav_about")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .imePadding()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (selectedTab) {
                        0 -> ProjectDashboardTab(
                            reportsCount = reports.size,
                            reports = reports,
                            viewModel = viewModel,
                            onCreateReport = { type ->
                                viewModel.startNewReport(type)
                            },
                            onViewReportsTab = {
                                selectedTab = 1
                            }
                        )
                        1 -> ReportListScreen(
                            viewModel = viewModel,
                            onEditReport = { report ->
                                viewModel.selectReport(report.id)
                            }
                        )
                        2 -> ProjectSettingsTab(
                            viewModel = viewModel,
                            importLauncher = importLauncher,
                            exportLauncher = exportLauncher
                        )
                        3 -> AboutAppTab(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

// Helper to format YYYY-MM-DD to Jalali String
fun formatGregorianToJalali(dateString: String): String {
    try {
        val parts = dateString.split("-")
        if (parts.size != 3) return dateString
        val gy = parts[0].toInt()
        val gm = parts[1].toInt()
        val gd = parts[2].toInt()
        
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        
        val gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1
        
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd2
        
        var jDayNo = gDayNo - 79
        val jNP = jDayNo / 12053
        jDayNo %= 12053
        
        var jy = 979 + 33 * jNP + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        
        var jm = 0
        while (jm < 12 && jDayNo >= jDaysInMonth[jm]) {
            if (jm == 11) {
                val isLeap = (jy == 1403 || jy == 1407 || jy == 1411 || jy == 1399 || (jy - 1399) % 4 == 0)
                val esfandDays = if (isLeap) 30 else 29
                if (jDayNo >= esfandDays) {
                    jDayNo -= esfandDays
                    jm++
                }
            } else {
                jDayNo -= jDaysInMonth[jm]
                jm++
            }
        }
        
        val jd = jDayNo + 1
        return String.format("%04d/%02d/%02d", jy, jm + 1, jd)
    } catch (e: Exception) {
        return dateString
    }
}

@Composable
fun ReportCardItem(
    report: DailyReport,
    viewMode: ReportViewMode,
    customUnitTitle: String,
    context: android.content.Context,
    onEditReport: (DailyReport) -> Unit,
    viewModel: ReportViewModel
) {
    val isSample = report.id < 0
    val (typeLabel, typeColor) = getReportTypeInfo(report.reportType, customUnitTitle)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSample) {
                    Toast.makeText(context, "این یک گزارش نمونه است ✨", Toast.LENGTH_SHORT).show()
                } else {
                    onEditReport(report)
                }
            }
            .shadow(2.dp, shape = RoundedCornerShape(16.dp))
            .testTag("report_card_${report.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (viewMode == ReportViewMode.COMPACT) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Teal document icon in rounded square container on far right (RTL: appears right)
            Box(
                modifier = Modifier
                    .size(if (viewMode == ReportViewMode.COMPACT) 36.dp else 48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(if (viewMode == ReportViewMode.COMPACT) 18.dp else 24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(if (viewMode == ReportViewMode.COMPACT) 8.dp else 12.dp))
            
            // 2. Report Details inside column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = report.project.ifEmpty { "خط نهم سراسری عجب شیر-تبریز" },
                    fontWeight = FontWeight.Bold,
                    fontSize = if (viewMode == ReportViewMode.COMPACT) 13.sp else 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = toPersianDigits(report.date.ifEmpty { "۱۴۰۳/۰۶/۰۶" }),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = report.section.ifEmpty { "دفتر فنی" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 3. Three-dot menu icon on far left (RTL: appears left)
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "گزینه‌ها",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("ویرایش گزارش") },
                        onClick = {
                            showMenu = false
                            if (isSample) {
                                Toast.makeText(context, "این یک گزارش نمونه است ✨", Toast.LENGTH_SHORT).show()
                            } else {
                                onEditReport(report)
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { Text("تکثیر گزارش") },
                        onClick = {
                            showMenu = false
                            if (isSample) {
                                Toast.makeText(context, "تکثیر برای گزارش نمونه غیرفعال است", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.duplicateReport(report)
                                Toast.makeText(context, "گزارش با موفقیت تکثیر شد 📋", Toast.LENGTH_SHORT).show()
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Color(0xFFFF9800)) }
                    )
                    DropdownMenuItem(
                        text = { Text("اشتراک‌گذاری PDF") },
                        onClick = {
                            showMenu = false
                            if (isSample) {
                                Toast.makeText(context, "اشتراک‌گذاری برای گزارش نمونه غیرفعال است", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.generateAndSharePdfFileDirectly(context, report) { success ->
                                    if (!success) {
                                        Toast.makeText(context, "خطا در اشتراک‌گذاری مستقیم گزارش ❌", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف گزارش") },
                        onClick = {
                            showMenu = false
                            if (isSample) {
                                Toast.makeText(context, "حذف برای گزارش نمونه غیرفعال است", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.deleteReport(report)
                                Toast.makeText(context, "گزارش با موفقیت حذف شد 🗑️", Toast.LENGTH_SHORT).show()
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}
