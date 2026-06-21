package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.example.data.model.*
import com.example.ui.viewmodel.ReportViewModel

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
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        val outputStream = java.io.ByteArrayOutputStream()
        val maxDim = 600
        val resized = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = kotlin.math.min(maxDim.toDouble() / bitmap.width, maxDim.toDouble() / bitmap.height)
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        resized.compress(android.graphics.Bitmap.CompressFormat.PNG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: ReportViewModel,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    // Force RTL direction for Persian layouts and text alignment
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        var currentScreen by remember { mutableStateOf<ActiveScreen>(ActiveScreen.ReportList) }
        val currentReport by viewModel.currentReport.collectAsStateWithLifecycle()
        val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
        val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()

        // State to control dialogs
        var showCreateTypeDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }

        var selectedBottomTab by remember { mutableStateOf(1) } // Default is 1 (Reports)

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val contentResolver = context.contentResolver
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val jsonText = inputStream.bufferedReader().use { it.readText() }
                        viewModel.importBackup(
                            jsonString = jsonText,
                            onSuccess = { count ->
                                Toast.makeText(context, "$count گزارش با موفقیت بازیابی شد ✅", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "خطا در بازیابی: $err ❌", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطا در باز کردن فایل: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Scaffold(
            bottomBar = {
                if (currentScreen is ActiveScreen.ReportList) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedBottomTab == 0,
                            onClick = { selectedBottomTab = 0 },
                            label = { Text("داشبورد", fontSize = 11.sp, fontWeight = if (selectedBottomTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Home, contentDescription = "داشبورد", modifier = Modifier.size(24.dp)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.testTag("nav_dashboard")
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 1,
                            onClick = { selectedBottomTab = 1 },
                            label = { Text("گزارش‌ها", fontSize = 11.sp, fontWeight = if (selectedBottomTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.List, contentDescription = "گزارش‌ها", modifier = Modifier.size(24.dp)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.testTag("nav_reports")
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 2,
                            onClick = { selectedBottomTab = 2 },
                            label = { Text("تنظیمات", fontSize = 11.sp, fontWeight = if (selectedBottomTab == 2) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "تنظیمات", modifier = Modifier.size(24.dp)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.testTag("nav_settings")
                        )
                        NavigationBarItem(
                            selected = selectedBottomTab == 3,
                            onClick = { selectedBottomTab = 3 },
                            label = { Text("درباره", fontSize = 11.sp, fontWeight = if (selectedBottomTab == 3) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Info, contentDescription = "درباره", modifier = Modifier.size(24.dp)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.testTag("nav_about")
                        )
                    }
                }
            },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                val topBarIcon = if (currentScreen is ActiveScreen.ReportEditor) {
                                    when (currentReport?.reportType) {
                                        "WAREHOUSE" -> Icons.Default.Warehouse
                                        "LEGAL" -> Icons.Default.Gavel
                                        "SURVEY" -> Icons.Default.Map
                                        "TECHNICAL" -> Icons.Default.Description
                                        "HSE" -> Icons.Default.Warning
                                        "CUSTOM" -> Icons.Default.Construction
                                        else -> Icons.Default.Engineering
                                    }
                                } else {
                                    Icons.Default.Engineering
                                }
                                Icon(
                                    imageVector = topBarIcon,
                                    contentDescription = "آیکون مهندسی کارگاه",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            val customUnitTitle = remember { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
                            Text(
                                text = if (currentScreen is ActiveScreen.ReportEditor) {
                                    when (currentReport?.reportType) {
                                        "WAREHOUSE" -> "ویرایش گزارش انبار"
                                        "LEGAL" -> "ویرایش گزارش حقوقی"
                                        "SURVEY" -> "ویرایش گزارش نقشه‌برداری"
                                        "TECHNICAL" -> "ویرایش گزارش دفتر فنی"
                                        "HSE" -> "ویرایش گزارش ایمنی HSE"
                                        "CUSTOM" -> "ویرایش گزارش $customUnitTitle"
                                        else -> "ویرایش گزارش اجرا"
                                    }
                                } else {
                                    "سامانه گزارش یار کارگاه"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentScreen is ActiveScreen.ReportEditor) {
                            IconButton(
                                onClick = {
                                    viewModel.saveCurrentReportImmediately()
                                    viewModel.clearCurrentReport()
                                    currentScreen = ActiveScreen.ReportList
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("back_to_list")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward, // RTL back arrow
                                    contentDescription = "بازگشت به آرشیو"
                                )
                            }
                        }
                    },
                    actions = {
                        // Global Action buttons
                        if (currentScreen is ActiveScreen.ReportEditor && currentReport != null) {
                            
                            // 1. Manually Trigger Save Button
                            IconButton(
                                onClick = {
                                    viewModel.saveCurrentReportImmediately()
                                    Toast.makeText(context, "گزارش با موفقیت ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("manual_save_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "ذخیره دستی گزارش"
                                )
                            }

                            // 2. Direct Share PDF File Button
                            IconButton(
                                onClick = {
                                    viewModel.generateAndSharePdfFileDirectly(context, currentReport!!) { success ->
                                        if (success) {
                                            Toast.makeText(context, "فایل PDF آماده اشتراک‌گذاری مستقیم شد", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "خطا در اشتراک‌گذاری مستقیم PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("share_pdf_direct_btn"),
                                enabled = !isExporting
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "اشتراک‌گذاری مستقیم فایل PDF"
                                    )
                                }
                            }

                            // 3. Export and Print PDF Button
                            IconButton(
                                onClick = {
                                    viewModel.generateAndSharePdf(context, currentReport!!) { success ->
                                        if (success) {
                                            Toast.makeText(context, "نسخه چاپی PDF آماده ارسال شد", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "خطا در ساخت نسخه چاپی PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("share_pdf_btn"),
                                enabled = !isExporting
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "پیش‌نمایش و چاپگر عمومی PDF"
                                )
                            }
                        } else if (currentScreen is ActiveScreen.ReportList) {
                            // Theme toggling button visible in List
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    tint = if (isDarkMode) Color.Yellow else Color.White,
                                    contentDescription = "تغییر تم برنامه"
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Crossfade(
                targetState = currentScreen,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    ActiveScreen.ReportList -> {
                        val allReportsRaw by viewModel.allReports.collectAsStateWithLifecycle()
                        when (selectedBottomTab) {
                            1 -> {
                                ReportListScreen(
                                    viewModel = viewModel,
                                    onEditReport = { reportId ->
                                        viewModel.selectReport(reportId)
                                        currentScreen = ActiveScreen.ReportEditor
                                    },
                                    onRequestCreate = {
                                        val defType = viewModel.sharedPreferences.getString("default_report_type", "ASK") ?: "ASK"
                                        when (defType) {
                                            "EXECUTION" -> {
                                                viewModel.startNewReport("EXECUTION")
                                                currentScreen = ActiveScreen.ReportEditor
                                                Toast.makeText(context, "گزارش اجرایی جدید ساخته شد 📝", Toast.LENGTH_SHORT).show()
                                            }
                                            "WAREHOUSE" -> {
                                                viewModel.startNewReport("WAREHOUSE")
                                                currentScreen = ActiveScreen.ReportEditor
                                                Toast.makeText(context, "گزارش انبار جدید ساخته شد 📦", Toast.LENGTH_SHORT).show()
                                            }
                                            "LEGAL" -> {
                                                viewModel.startNewReport("LEGAL")
                                                currentScreen = ActiveScreen.ReportEditor
                                                Toast.makeText(context, "گزارش حقوقی و تملک جدید ساخته شد ⚖️", Toast.LENGTH_SHORT).show()
                                            }
                                            "SURVEY" -> {
                                                viewModel.startNewReport("SURVEY")
                                                currentScreen = ActiveScreen.ReportEditor
                                                Toast.makeText(context, "گزارش نقشه‌برداری جدید ساخته شد 🧭", Toast.LENGTH_SHORT).show()
                                            }
                                            "TECHNICAL" -> {
                                                viewModel.startNewReport("TECHNICAL")
                                                currentScreen = ActiveScreen.ReportEditor
                                                Toast.makeText(context, "گزارش دفتر فنی جدید ساخته شد 📂", Toast.LENGTH_SHORT).show()
                                            }
                                            "HSE" -> {
                                                viewModel.startNewReport("HSE")
                                                currentScreen = ActiveScreen.ReportEditor
                                                Toast.makeText(context, "گزارش ایمنی HSE جدید ساخته شد ⚠️", Toast.LENGTH_SHORT).show()
                                            }
                                            "CUSTOM" -> {
                                                viewModel.startNewReport("CUSTOM")
                                                currentScreen = ActiveScreen.ReportEditor
                                                val customTitle = viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها"
                                                Toast.makeText(context, "گزارش $customTitle جدید ساخته شد 📂", Toast.LENGTH_SHORT).show()
                                            }
                                            else -> {
                                                showCreateTypeDialog = true
                                            }
                                        }
                                    }
                                )
                            }
                            0 -> ProjectDashboardTab(allReportsRaw.size, allReportsRaw, viewModel)
                            2 -> ProjectSettingsTab(viewModel = viewModel, importLauncher = importLauncher)
                            3 -> AboutAppTab(viewModel = viewModel)
                        }
                    }
                    ActiveScreen.ReportEditor -> {
                        if (currentReport != null) {
                            ReportEditorScreen(
                                report = currentReport!!,
                                onUpdateReport = { updated ->
                                    viewModel.updateCurrentReport { updated }
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        // Choice Dialog for new Daily Report Type
        if (showCreateTypeDialog) {
            val customTitle = remember { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
            val reportTypesList = listOf(
                Triple("TECHNICAL", "دفتر فنی", Icons.Default.Description),
                Triple("EXECUTION", "اجرا", Icons.Default.Engineering),
                Triple("WAREHOUSE", "انبارداری", Icons.Default.Warehouse),
                Triple("SURVEY", "نقشه‌برداری", Icons.Default.Map),
                Triple("LEGAL", "امور حقوقی", Icons.Default.Gavel),
                Triple("HSE", "ایمنی HSE", Icons.Default.Warning),
                Triple("CUSTOM", customTitle, Icons.Default.Construction)
            )

            AlertDialog(
                onDismissRequest = { showCreateTypeDialog = false },
                title = {
                    Text(
                        text = "ایجاد گزارش روزانه جدید",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "بخش مورد نظر جهت درج گزارش روزانه را انتخاب کنید:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        val chunks = reportTypesList.chunked(2)
                        chunks.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { item ->
                                    Card(
                                        onClick = {
                                            viewModel.startNewReport(item.first)
                                            currentScreen = ActiveScreen.ReportEditor
                                            showCreateTypeDialog = false
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(58.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when (item.first) {
                                                "TECHNICAL" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                                "EXECUTION" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                                "WAREHOUSE" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                                                "HSE" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            when (item.first) {
                                                "TECHNICAL" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                "EXECUTION" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                                "WAREHOUSE" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                                "HSE" -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        when (item.first) {
                                                            "TECHNICAL" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                            "EXECUTION" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                                            "WAREHOUSE" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                                            "HSE" -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.third,
                                                    contentDescription = item.second,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = when (item.first) {
                                                        "TECHNICAL" -> MaterialTheme.colorScheme.primary
                                                        "EXECUTION" -> MaterialTheme.colorScheme.secondary
                                                        "WAREHOUSE" -> MaterialTheme.colorScheme.tertiary
                                                        "HSE" -> MaterialTheme.colorScheme.error
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                            Text(
                                                text = item.second,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showCreateTypeDialog = false },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("انصراف", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            )
        }

        if (showSettingsDialog) {
            var defaultProject by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_project", "") ?: "") }
            var defaultSection by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_section", "") ?: "") }
            var defaultPreparedBy by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_prepared_by", "") ?: "") }
            var defaultWeather by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_weather", "آفتابی ☀️") ?: "آفتابی ☀️") }
            var customUnitTitle by remember { mutableStateOf(viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها") }
            var defaultStartKm by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_start_km", "") ?: "") }
            var defaultEndKm by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_end_km", "") ?: "") }
            var defaultReportType by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_report_type", "ASK") ?: "ASK") }
            var showWeatherPickerForSettings by remember { mutableStateOf(false) }

            var userSignatureBase64 by remember { mutableStateOf(viewModel.sharedPreferences.getString("user_signature", "") ?: "") }
            var showSignaturePadDialog by remember { mutableStateOf(false) }

            var dailyReminderEnabled by remember { mutableStateOf(viewModel.sharedPreferences.getBoolean("daily_reminder_enabled", true)) }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    Toast.makeText(context, "مجوز اعلان تایید شد و یادآور روزانه ساعت ۲۰ فعال گردید ✅", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "توجه: برای دریافت هشدارها باید دسترسی اعلان فعال باشد ⚠️", Toast.LENGTH_LONG).show()
                    dailyReminderEnabled = false
                }
            }

            val signatureImageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                if (uri != null) {
                    val base64 = uriToBase64(context, uri)
                    if (base64 != null) {
                        userSignatureBase64 = base64
                        viewModel.sharedPreferences.edit().putString("user_signature", base64).apply()
                        Toast.makeText(context, "تصویر امضا با موفقیت بارگذاری و ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "خطا در پردازش تصویر امضا", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            var defaultAppTheme by remember { mutableStateOf(viewModel.sharedPreferences.getString("app_theme", "LIGHT") ?: "LIGHT") }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Text(
                        text = "تنظیمات پیشرفته سیستم",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "تنظیمات پیش‌فرض برای هوشمندسازی و سرعت‌بخشی به ثبت گزارش‌های فیلد ساختمانی شما.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        // CARD 1: Basic Information Defaults
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "مشخصات پایه کارگاه",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedTextField(
                                    value = defaultProject,
                                    onValueChange = { defaultProject = it },
                                    label = { Text("نام پروژه پیش‌فرض") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp)
                                )

                                OutlinedTextField(
                                    value = defaultSection,
                                    onValueChange = { defaultSection = it },
                                    label = { Text("بخش/واحد کارگاهی پیش‌فرض") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp)
                                )

                                OutlinedTextField(
                                    value = defaultPreparedBy,
                                    onValueChange = { defaultPreparedBy = it },
                                    label = { Text("نام تنظیم‌کننده پیش‌فرض") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp)
                                )

                                OutlinedTextField(
                                    value = customUnitTitle,
                                    onValueChange = { customUnitTitle = it },
                                    label = { Text("عنوان سفارشی سایر واحدها (گزارش نوع پنجم)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp)
                                )
                            }
                        }

                        // CARD 2: Theme Selection Panel
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "تم کاربری و ظاهر نرم‌افزار",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        Triple("LIGHT", "روشن ☀️", Color(0xFFF59E0B)),
                                        Triple("DARK", "تیره 🌙", Color(0xFF3B82F6)),
                                        Triple("SYSTEM", "سیستم ⚙️", Color(0xFF10B981))
                                    ).forEach { (themeKey, label, activeClr) ->
                                        val isSelected = defaultAppTheme == themeKey
                                        Button(
                                            onClick = { defaultAppTheme = themeKey },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // CARD 3: Custom Default Configurations (Categorization & Weather)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "دسته‌بندی و گزارش‌نویسی پیش‌فرض",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Segmented Outline buttons row for EXECUTION, WAREHOUSE, ASK
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        Triple("ASK", "همیشه بپرس", Icons.Default.Help),
                                        Triple("EXECUTION", "اجرا", Icons.Default.Engineering),
                                        Triple("WAREHOUSE", "انبارداری", Icons.Default.Warehouse)
                                    ).forEach { (typeKey, typeLabel, typeIcon) ->
                                        val isSelected = defaultReportType == typeKey
                                        Button(
                                            onClick = { defaultReportType = typeKey },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(imageVector = typeIcon, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(typeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Row 2 for default settings modal: LEGAL, SURVEY, TECHNICAL
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        Triple("LEGAL", "امور حقوقی", Icons.Default.Gavel),
                                        Triple("SURVEY", "نقشه‌برداری", Icons.Default.Map),
                                        Triple("TECHNICAL", "دفتر فنی", Icons.Default.Description)
                                    ).forEach { (typeKey, typeLabel, typeIcon) ->
                                        val isSelected = defaultReportType == typeKey
                                        Button(
                                            onClick = { defaultReportType = typeKey },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(imageVector = typeIcon, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(typeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Row 3 for default settings modal: HSE, CUSTOM
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        Triple("HSE", "ایمنی HSE", Icons.Default.Warning),
                                        Triple("CUSTOM", customUnitTitle, Icons.Default.Construction)
                                    ).forEach { (typeKey, typeLabel, typeIcon) ->
                                        val isSelected = defaultReportType == typeKey
                                        Button(
                                            onClick = { defaultReportType = typeKey },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(imageVector = typeIcon, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(typeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showWeatherPickerForSettings = true }
                                ) {
                                    OutlinedTextField(
                                        value = defaultWeather,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("آب و هوای پیش‌فرض") },
                                        trailingIcon = {
                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "انتخاب")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.Transparent)
                                            .clickable { showWeatherPickerForSettings = true }
                                    )
                                }
                            }
                        }

                        // Signature Section
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant, 
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            text = "امضای الکترونیکی تنظیم‌کننده گزارش:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val sigBitmap = remember(userSignatureBase64) {
                                    if (userSignatureBase64.isNotEmpty()) base64ToBitmap(userSignatureBase64) else null
                                }
                                
                                if (sigBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(Color.White, RoundedCornerShape(6.dp))
                                            .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Image(
                                            bitmap = sigBitmap.asImageBitmap(),
                                            contentDescription = "پیش‌نمایش امضا",
                                            modifier = Modifier.fillMaxHeight().padding(4.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                            .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "امضا ثبت نشده است (فیلد امضا در PDF خالی خواهد ماند)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = { showSignaturePadDialog = true },
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(imageVector = Icons.Default.Create, contentDescription = "ترسیم", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("ترسیم امضا", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Button(
                                        onClick = { signatureImageLauncher.launch("image/*") },
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "بارگذاری", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("آپلود عکس", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    if (userSignatureBase64.isNotEmpty()) {
                                        Button(
                                            onClick = {
                                                userSignatureBase64 = ""
                                                viewModel.sharedPreferences.edit().remove("user_signature").apply()
                                                Toast.makeText(context, "امضا با موفقیت حذف شد 🗑️", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(0.8f).heightIn(min = 36.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("حذف", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

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
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        ) {
                                            Canvas(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .onSizeChanged { size ->
                                                        if (size.width > 0 && size.height > 0) {
                                                            canvasWidth = size.width
                                                            canvasHeight = size.height
                                                        }
                                                    }
                                                    .pointerInput(Unit) {
                                                        detectDragGestures(
                                                            onDragStart = { startOffset ->
                                                                currentPoints.add(startOffset)
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                              change.consume()
                                                              currentPoints.add(change.position)
                                                            },
                                                            onDragEnd = {
                                                                lines.add(SignatureLine(currentPoints.toList()))
                                                                currentPoints.clear()
                                                            }
                                                        )
                                                    }
                                            ) {
                                                lines.forEach { line ->
                                                    if (line.points.size > 1) {
                                                        for (i in 0 until line.points.size - 1) {
                                                            drawLine(
                                                                color = Color.Black,
                                                                start = line.points[i],
                                                                end = line.points[i + 1],
                                                                strokeWidth = 3.dp.toPx(),
                                                                cap = StrokeCap.Round
                                                            )
                                                        }
                                                    }
                                                }
                                                if (currentPoints.size > 1) {
                                                    for (i in 0 until currentPoints.size - 1) {
                                                        drawLine(
                                                            color = Color.Black,
                                                            start = currentPoints[i],
                                                            end = currentPoints[i + 1],
                                                            strokeWidth = 3.dp.toPx(),
                                                            cap = StrokeCap.Round
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { lines.clear() },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("پاک کردن", fontWeight = FontWeight.Bold)
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    val base64 = saveDrawingToBitmap(lines.toList(), canvasWidth, canvasHeight)
                                                    if (base64 != null) {
                                                        userSignatureBase64 = base64
                                                        viewModel.sharedPreferences.edit().putString("user_signature", base64).apply()
                                                        Toast.makeText(context, "امضا با موفقیت ترسیم و ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                                                        showSignaturePadDialog = false
                                                    } else {
                                                        Toast.makeText(context, "لطفا ابتدا امضا را روی کادر ترسیم کنید", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1.5f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("تایید و ذخیره", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { showSignaturePadDialog = false }) {
                                        Text("انصراف")
                                    }
                                }
                            )
                        }

                        // Daily Reminder Notification Section
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant, 
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            text = "یادآور روزانه کارگاه:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "یادآوری هر شب ساعت ۲۰:۰۰ 🔔",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "در صورتی که گزارش امروز کارگاه خود را هنوز ثبت نکرده باشید، یک هشدار جهت یادآوری دریافت خواهید کرد.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                                Switch(
                                    checked = dailyReminderEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                                permissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                                            }
                                        }
                                        dailyReminderEnabled = isChecked
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        // Backup and Restore Section
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant, 
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            text = "پشتیبان‌گیری و بازیابی اطلاعات:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Button 1: Export (پشتیبان‌گیری)
                            Button(
                                onClick = {
                                    val backupJson = viewModel.exportBackup()
                                    try {
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TITLE, "گزاش‌های کارگاهی")
                                            putExtra(android.content.Intent.EXTRA_TEXT, backupJson)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "ارسال فایل پشتیبان"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطا در ارسال: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "پشتیبان‌گیری", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("پشتیبان‌گیری (ارسال)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Button 2: Import (بازیابی)
                            Button(
                                onClick = {
                                    try {
                                        importLauncher.launch("*/*")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطا در انتخاب اطلاعات پشتیبان: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "بازیابی", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("بازیابی بکاپ (ورود)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveDefaultConfig(
                                defaultProject,
                                defaultSection,
                                defaultPreparedBy,
                                defaultReportType,
                                defaultWeather,
                                "", // defaultStartKm (removed)
                                ""  // defaultEndKm (removed)
                            )
                            viewModel.sharedPreferences.edit().putString("custom_unit_title", customUnitTitle).apply()
                            // Persist app theme preference
                            viewModel.sharedPreferences.edit().putString("app_theme", defaultAppTheme).apply()
                            // Persist the daily reminder configuration preference
                            viewModel.sharedPreferences.edit().putBoolean("daily_reminder_enabled", dailyReminderEnabled).apply()
                            if (dailyReminderEnabled) {
                                com.example.receiver.DailyReminderReceiver.scheduleDailyReminder(context)
                            } else {
                                com.example.receiver.DailyReminderReceiver.cancelDailyReminder(context)
                            }
                            
                            Toast.makeText(context, "تنظیمات پیش‌فرض با موفقیت ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                            showSettingsDialog = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("ذخیره تنظیمات", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSettingsDialog = false },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("انصراف")
                    }
                }
            )

            // Nested Weather Picker Dialog for Settings default weather
            if (showWeatherPickerForSettings) {
                AlertDialog(
                    onDismissRequest = { showWeatherPickerForSettings = false },
                    title = {
                        Text(
                            text = "انتخاب وضعیت جوی پیش‌فرض",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Pair("آفتابی ☀️", Color(0xFFF59E0B)),
                                Pair("نیمه‌ابری ⛅", Color(0xFF60A5FA)),
                                Pair("ابری ☁️", Color(0xFF5B7181)),
                                Pair("بارانی 🌧️", Color(0xFF2563EB)),
                                Pair("برفی ❄️", Color(0xFF0EA5E9)),
                                Pair("طوفانی و باد شدید 💨", Color(0xFF475569)),
                                Pair("غبارآلود و مه پیشفرض 🌫️", Color(0xFF94A3B8))
                            ).forEach { (weatherText, color) ->
                                Card(
                                    onClick = {
                                        defaultWeather = weatherText
                                        showWeatherPickerForSettings = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (defaultWeather == weatherText) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(color)
                                        )
                                        Text(
                                            text = weatherText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (defaultWeather == weatherText) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showWeatherPickerForSettings = false }) {
                            Text("انصراف")
                        }
                    }
                )
            }
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "درباره برنامه",
                        tint = Color(0xFF0F766E), // Construction Teal primary
                        modifier = Modifier.size(40.dp)
                    )
                },
                title = {
                    Text(
                        text = "درباره سامانه گزارش‌یار",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F766E),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Version Badge (v2.4.0) with clean accent
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFFFEF3C7)) // Amber tint background
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "نسخه جدید v3.0.4",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706) // Dark Amber text
                            )
                        }

                        Text(
                            text = "گزارش‌یار کارگاه ساختمانی",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Divider(color = BorderColor, modifier = Modifier.padding(vertical = 2.dp))

                        Text(
                            text = "این برنامه ابزار هوشمند ثبت، بایگانی و سازماندهی گزارش کارهای روزانه کارگاه‌های عمرانی، ساختمانی و انبارداری است. به کمک این پلتفرم، تمامی اطلاعات عملیاتی، ماشین‌آلات، پرسنل کارگاهی و کالاها مستندسازی می‌شود.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Justify,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Divider(color = BorderColor, modifier = Modifier.padding(vertical = 2.dp))

                        Button(
                            onClick = {
                                try {
                                    val backupJson = viewModel.exportBackup()
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("support@constructionapp.com"))
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "پشتیبان آفلاین داده‌ها - نسخه جدید v3.0.4")
                                        putExtra(android.content.Intent.EXTRA_TEXT, "با سلام و احترام،\nپشتیبان داده‌های کارگاهی پیوست شده است:\n\n$backupJson")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "ارسال اطلاعات پشتیبان"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "سیستم ارسال ایمیل پیش‌فرض یافت نشد", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F766E), // Modern Construction Teal
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp), // Large rounded button
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("send_backup_email_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "ارسال ایمیل")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ارسال ایمیل پشتیبانی (JSON)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showAboutDialog = false }
                    ) {
                        Text("بستن", fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                    }
                }
            )
        }
    }
}

@Composable
fun ReportListScreen(
    viewModel: ReportViewModel,
    onEditReport: (Int) -> Unit,
    onRequestCreate: () -> Unit
) {
    val reports by viewModel.filteredReports.collectAsStateWithLifecycle()
    val allReportsRaw by viewModel.allReports.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (allReportsRaw.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Beautifully rendered, modern Material Design 3 icon of a construction tablet and pencil
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Construction tablet container
                    Card(
                        modifier = Modifier
                            .size(76.dp, 94.dp)
                            .align(Alignment.Center),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tablet camera/header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp, 4.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            // Blueprint grid lines
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            )
                        }
                    }

                    // Floating Amber Pencil
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-8).dp, y = (-4).dp)
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "هیچ گزارش روزانه‌ای ثبت نشده است",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "برای کارگاه ساختمانی یا بخش انبار خود اولین گزارش را شروع کنید. مشخصات پروژه در مراجعه‌های بعدی خودکار تکمیل می‌شود.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Extended FAB at the bottom right
            ExtendedFloatingActionButton(
                onClick = onRequestCreate,
                containerColor = Color(0xFFFEF3C7), // Light amber background
                contentColor = Color(0xFF0F766E), // Teal text & icon
                shape = CircleShape, // Fully rounded stadium edges
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 24.dp)
                    .testTag("create_first_report_efab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+ تهیه گزارش روزانه جدید",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "نکته راهنما",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "برای کپی کردن سریع گزارش‌های قبلی با تاریخ امروز، دکمه کپی 📋 را فشار دهید.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Advanced real-time Search / Filter bar by Date or Project
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("جستجو در تاریخ روز (مثلاً: ۰۳/۲۱)، نام پروژه یا ناظر...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "جستجو",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "پاک کردن فیلتر",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Text(
                    text = if (searchQuery.isEmpty()) "آرشیو گزارش‌های ثبت‌شده کارگاه" else "نتایج جستجو (${reports.size} مورد)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                if (reports.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "یافت نشد",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "گزارشی با مشخصات مورد نظر یافت نشد!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "مجدداً فیلتر یا عبارت جستجو خود را بررسی کنید.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        itemsIndexed(reports) { _, report ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .clickable { onEditReport(report.id) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val rType = report.reportType
                                    val customUnitTitle = remember { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
                                    val badgeIcon = when (rType) {
                                        "WAREHOUSE" -> Icons.Default.Warehouse
                                        "LEGAL" -> Icons.Default.Gavel
                                        "SURVEY" -> Icons.Default.Map
                                        "TECHNICAL" -> Icons.Default.Description
                                        "HSE" -> Icons.Default.Warning
                                        "CUSTOM" -> Icons.Default.Construction
                                        else -> Icons.Default.Engineering
                                    }
                                    val badgeBg = when (rType) {
                                        "WAREHOUSE" -> MaterialTheme.colorScheme.secondaryContainer
                                        "LEGAL" -> MaterialTheme.colorScheme.tertiaryContainer
                                        "SURVEY" -> MaterialTheme.colorScheme.surfaceVariant
                                        "TECHNICAL" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        "HSE" -> MaterialTheme.colorScheme.errorContainer
                                        "CUSTOM" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                    val badgeTint = when (rType) {
                                        "WAREHOUSE" -> MaterialTheme.colorScheme.secondary
                                        "LEGAL" -> MaterialTheme.colorScheme.tertiary
                                        "SURVEY" -> MaterialTheme.colorScheme.onSurfaceVariant
                                        "TECHNICAL" -> MaterialTheme.colorScheme.primary
                                        "HSE" -> MaterialTheme.colorScheme.error
                                        "CUSTOM" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    val badgeLabel = when (rType) {
                                        "WAREHOUSE" -> "انبارداری"
                                        "LEGAL" -> "حقوقی و تملک"
                                        "SURVEY" -> "نقشه‌برداری"
                                        "TECHNICAL" -> "دفتر فنی"
                                        "HSE" -> "ایمنی HSE"
                                        "CUSTOM" -> customUnitTitle
                                        else -> "اجرا"
                                    }

                                    // Senior UX/UI Vertical Profile Accent Ribbon
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(badgeTint)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(badgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = badgeIcon,
                                            contentDescription = "نوع گزارش",
                                            tint = badgeTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = report.project.ifEmpty { "پروژه عمومی کارگاهی" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "تاریخ",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = report.date,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            
                                            // Visual Badge
                                            Surface(
                                                color = badgeBg.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = badgeLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeTint,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Duplicate report button
                                    IconButton(
                                        onClick = {
                                            viewModel.duplicateReport(report)
                                            Toast.makeText(context, "گزارش جدید با داده‌های کپی شده آماده شد 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "همانندسازی گزارش",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
    
                                    // Large delete target
                                    IconButton(
                                        onClick = { viewModel.deleteReport(report) },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف گزارش",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            
        // Large Float Action Button optimized with minimum 56dp for easy tap
        if (reports.isNotEmpty()) {
            FloatingActionButton(
                onClick = onRequestCreate,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .size(56.dp)
                    .testTag("floating_add_report_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add, 
                    contentDescription = "افزودن گزارش روزانه کارگاه",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun ReportEditorScreen(
    report: DailyReport,
    onUpdateReport: (DailyReport) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    LaunchedEffect(report.id, report.reportType) {
        selectedTab = 0
    }
    
    val rType = report.reportType
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val customUnitTitle = remember(sharedPreferences) { sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
    
    // Choose tabs based on report categorization - Now exactly 6 tabs for all profiles, highly uniform and stable!
    val tabs = when (rType) {
        "WAREHOUSE" -> listOf("مشخصات انبار", "ورود مصالح", "خروج مصالح", "ماشین‌آلات انبار", "پرسنل انبار", "یادداشت‌ها")
        "LEGAL" -> listOf("مشخصات پایه", "تحصیل اراضی", "مجوزهای قانونی", "ماشین‌آلات", "کارشناسان پیگیری", "یادداشت‌ها")
        "SURVEY" -> listOf("مشخصات پایه", "کارهای برداشت", "پیش‌بینی فردا", "تجهیزات نقشه‌برداری", "پرسنل نقشه‌برداری", "یادداشت‌ها")
        "TECHNICAL" -> listOf("مشخصات پایه", "فعالیت دفتر فنی", "مصالح تخصصی", "ماشین‌آلات", "پرسنل فنی", "یادداشت‌ها")
        "HSE" -> listOf("مشخصات پایه", "اقدامات ایمنی", "مصالح و اقلام ایمنی", "تجهیزات ایمنی", "پرسنل ایمنی", "یادداشت‌ها")
        "CUSTOM" -> listOf("مشخصات پایه", "فعالیت‌های $customUnitTitle", "مصالح و اقلام اختصاصی", "تجهیزات و ابزار", "پرسنل واحد $customUnitTitle", "یادداشت‌ها")
        else -> listOf("مشخصات پایه", "فعالیت‌های اجرا", "ماشین‌آلات", "نیروها", "مصالح وارده", "یادداشت‌ها")
    }

    val tabIcons = when (rType) {
        "WAREHOUSE" -> listOf(
            Icons.Default.Info,
            Icons.Default.ArrowCircleDown,
            Icons.Default.ArrowCircleUp,
            Icons.Default.LocalShipping,
            Icons.Default.Group,
            Icons.Default.EditNote
        )
        "LEGAL" -> listOf(
            Icons.Default.Info,
            Icons.Default.Landscape,
            Icons.Default.Verified,
            Icons.Default.LocalShipping,
            Icons.Default.Group,
            Icons.Default.EditNote
        )
        "SURVEY" -> listOf(
            Icons.Default.Info,
            Icons.Default.LocationOn,
            Icons.Default.Event,
            Icons.Default.Agriculture,
            Icons.Default.Group,
            Icons.Default.EditNote
        )
        "TECHNICAL" -> listOf(
            Icons.Default.Info,
            Icons.Default.Description,
            Icons.Default.ShoppingCart,
            Icons.Default.LocalShipping,
            Icons.Default.Group,
            Icons.Default.EditNote
        )
        "HSE" -> listOf(
            Icons.Default.Info,
            Icons.Default.Warning,
            Icons.Default.ShoppingCart,
            Icons.Default.LocalShipping,
            Icons.Default.Group,
            Icons.Default.EditNote
        )
        "CUSTOM" -> listOf(
            Icons.Default.Info,
            Icons.Default.Construction,
            Icons.Default.ShoppingCart,
            Icons.Default.LocalShipping,
            Icons.Default.Group,
            Icons.Default.EditNote
        )
        else -> listOf(
            Icons.Default.Info,
            Icons.Default.Construction,
            Icons.Default.LocalShipping,
            Icons.Default.Group,
            Icons.Default.ShoppingCart,
            Icons.Default.EditNote
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.padding(vertical = 4.dp),
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    icon = {
                        val iconVec = tabIcons.getOrNull(index) ?: Icons.Default.Info
                        Icon(
                            imageVector = iconVec,
                            contentDescription = title,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        Divider(color = BorderColor)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (rType) {
                "WAREHOUSE" -> {
                    when (selectedTab) {
                        0 -> BaseInfoTab(report, onUpdateReport)
                        1 -> WarehouseMaterialsTab(report, isExit = false, onUpdateReport) // ورود مصالح
                        2 -> WarehouseMaterialsTab(report, isExit = true, onUpdateReport)  // خروج مصالح
                        3 -> MachineryTab(report, onUpdateReport)
                        4 -> ManpowerTab(report, onUpdateReport)
                        5 -> NotesTab(report, onUpdateReport)
                    }
                }
                "LEGAL" -> {
                    when (selectedTab) {
                        0 -> BaseInfoTab(report, onUpdateReport)
                        1 -> TasksTab(report, onUpdateReport) // تحصیل اراضی (customized inside TasksTab dynamically)
                        2 -> WarehouseMaterialsTab(report, isExit = true, onUpdateReport)  // مجوزهای قانونی (materials customized)
                        3 -> MachineryTab(report, onUpdateReport)
                        4 -> ManpowerTab(report, onUpdateReport)
                        5 -> NotesTab(report, onUpdateReport)
                    }
                }
                "SURVEY" -> {
                    when (selectedTab) {
                        0 -> BaseInfoTab(report, onUpdateReport)
                        1 -> TasksTab(report, onUpdateReport) // کارهای برداشت (customized inside TasksTab dynamically)
                        2 -> WarehouseMaterialsTab(report, isExit = false, onUpdateReport) // پیش‌بینی فردا (materials customized)
                        3 -> MachineryTab(report, onUpdateReport)
                        4 -> ManpowerTab(report, onUpdateReport)
                        5 -> NotesTab(report, onUpdateReport)
                    }
                }
                "TECHNICAL" -> {
                    when (selectedTab) {
                        0 -> BaseInfoTab(report, onUpdateReport)
                        1 -> TasksTab(report, onUpdateReport) // خلاصه شرح کار دفتر فنی
                        2 -> WarehouseMaterialsTab(report, isExit = false, onUpdateReport) // مصالح تخصصی وارده
                        3 -> MachineryTab(report, onUpdateReport)
                        4 -> ManpowerTab(report, onUpdateReport)
                        5 -> NotesTab(report, onUpdateReport)
                    }
                }
                "HSE" -> {
                    when (selectedTab) {
                        0 -> BaseInfoTab(report, onUpdateReport)
                        1 -> TasksTab(report, onUpdateReport) // اقدامات ایمنی
                        2 -> WarehouseMaterialsTab(report, isExit = false, onUpdateReport) // مصالح و تجهیزات ایمنی وارده
                        3 -> MachineryTab(report, onUpdateReport) // تجهیزات ایمنی
                        4 -> ManpowerTab(report, onUpdateReport) // پرسنل هماهنگی و افسران ایمنی
                        5 -> NotesTab(report, onUpdateReport)
                    }
                }
                "CUSTOM" -> {
                    when (selectedTab) {
                        0 -> BaseInfoTab(report, onUpdateReport)
                        1 -> TasksTab(report, onUpdateReport) // فعالیت‌های واحد سفارشی تکمیلی
                        2 -> WarehouseMaterialsTab(report, isExit = false, onUpdateReport) // ورود اقلام اختصاصی واحد
                        3 -> MachineryTab(report, onUpdateReport) // ماشین‌آلات فعال این بخش
                        4 -> ManpowerTab(report, onUpdateReport) // پرسنل واحد مربوطه
                        5 -> NotesTab(report, onUpdateReport)
                    }
                }
                else -> {
                    when (selectedTab) {
                        0 -> BaseInfoTab(report, onUpdateReport)
                        1 -> TasksTab(report, onUpdateReport)
                        2 -> MachineryTab(report, onUpdateReport)
                        3 -> ManpowerTab(report, onUpdateReport)
                        4 -> MaterialsTab(report, onUpdateReport)
                        5 -> NotesTab(report, onUpdateReport)
                    }
                }
            }
        }
    }
}

private fun getTodayShamsi(): Triple<Int, Int, Int> {
    val calendar = java.util.GregorianCalendar(java.util.Locale.US)
    val gy = calendar.get(java.util.Calendar.YEAR)
    val gm = calendar.get(java.util.Calendar.MONTH) + 1
    val gd = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    
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
    return Triple(jy, jm, jd)
}

private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): java.util.Calendar {
    val jy2 = jy + 1595
    var days = -350278 + 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
    for (i in 0 until jm - 1) {
        if (i < 6) {
            days += 31
        } else {
            days += 30
        }
    }
    days += jd
    
    var gYear = 100 + 4 * (days / 146097)
    var gDays = days % 146097
    if (gDays >= 36525) {
        gDays--
        gYear += 100 * (gDays / 36524)
        gDays %= 36524
        if (gDays >= 365) {
            gDays++
        }
    }
    gYear += 4 * (gDays / 1461)
    gDays %= 1461
    if (gDays >= 366) {
        gDays--
        gYear += gDays / 365
        gDays %= 365
    }
    val calendar = java.util.GregorianCalendar(java.util.Locale.US)
    calendar.set(java.util.Calendar.YEAR, gYear)
    val leap = (gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0)
    val gDaysInMonth = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var m = 0
    var d = gDays + 1
    while (m < 12 && d > gDaysInMonth[m]) {
        d -= gDaysInMonth[m]
        m++
    }
    calendar.set(java.util.Calendar.MONTH, m)
    calendar.set(java.util.Calendar.DAY_OF_MONTH, d)
    return calendar
}

private fun getPersianWeekdayIndex(calendar: java.util.Calendar): Int {
    val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
    return when (dayOfWeek) {
        java.util.Calendar.SATURDAY -> 0
        java.util.Calendar.SUNDAY -> 1
        java.util.Calendar.MONDAY -> 2
        java.util.Calendar.TUESDAY -> 3
        java.util.Calendar.WEDNESDAY -> 4
        java.util.Calendar.THURSDAY -> 5
        java.util.Calendar.FRIDAY -> 6
        else -> 0
    }
}

private fun getMaxDays(year: Int, month: Int): Int {
    return when {
        month <= 6 -> 31
        month <= 11 -> 30
        else -> {
            if (year == 1403 || year == 1407 || year == 1411 || year == 1399) 30 else 29
        }
    }
}

private fun toPersianNum(num: Int): String {
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return num.toString().map { c -> if (c.isDigit()) persianDigits[c - '0'] else c }.joinToString("")
}

private data class CalendarCell(
    val day: Int,
    val isCurrentMonth: Boolean,
    val dateForCell: Triple<Int, Int, Int>
)

@Composable
fun BaseInfoTab(report: DailyReport, onUpdateReport: (DailyReport) -> Unit) {
    var showWeatherPickerForActiveReport by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val today = remember { getTodayShamsi() }
        var selectedYear by remember { mutableStateOf(today.first) }
        var selectedMonth by remember { mutableStateOf(today.second) }
        var selectedDay by remember { mutableStateOf(today.third) }

        // Try to parse the existing Shamsi date to populate selectors
        LaunchedEffect(report.date) {
            try {
                val digitsEng = report.date
                    .replace('۰', '0')
                    .replace('۱', '1')
                    .replace('۲', '2')
                    .replace('۳', '3')
                    .replace('۴', '4')
                    .replace('۵', '5')
                    .replace('۶', '6')
                    .replace('۷', '7')
                    .replace('۸', '8')
                    .replace('۹', '9')
                val parts = digitsEng.split("/")
                if (parts.size == 3) {
                    parts[0].trim().toIntOrNull()?.let { selectedYear = it }
                    parts[1].trim().toIntOrNull()?.let { selectedMonth = it }
                    parts[2].trim().toIntOrNull()?.let { selectedDay = it }
                }
            } catch (e: Exception) {
                // Keep default or fallback
            }
        }

        val monthsPersian = listOf(
            "فروردین (۰۱)", "اردیبهشت (۰۲)", "خرداد (۰۳)", "تیر (۰۴)", "مرداد (۰۵)", "شهریور (۰۶)",
            "مهر (۰۷)", "آبان (۰۸)", "آذر (۰۹)", "دی (۱۰)", "بهمن (۱۱)", "اسفند (۱۲)"
        )

        val cells = remember(selectedYear, selectedMonth) {
            val list = mutableListOf<CalendarCell>()
            val prevMonth = if (selectedMonth == 1) 12 else selectedMonth - 1
            val prevYear = if (selectedMonth == 1) selectedYear - 1 else selectedYear
            val prevMonthMaxDays = getMaxDays(prevYear, prevMonth)
            
            val firstDayCal = jalaliToGregorian(selectedYear, selectedMonth, 1)
            val startDayOfWeek = getPersianWeekdayIndex(firstDayCal)
            val currentMaxDays = getMaxDays(selectedYear, selectedMonth)
            
            for (i in (prevMonthMaxDays - startDayOfWeek + 1)..prevMonthMaxDays) {
                list.add(CalendarCell(i, false, Triple(prevYear, prevMonth, i)))
            }
            for (i in 1..currentMaxDays) {
                list.add(CalendarCell(i, true, Triple(selectedYear, selectedMonth, i)))
            }
            val totalCellsSoFar = list.size
            val remaining = if (totalCellsSoFar <= 35) 35 - totalCellsSoFar else 42 - totalCellsSoFar
            val nextMonth = if (selectedMonth == 12) 1 else selectedMonth + 1
            val nextYear = if (selectedMonth == 12) selectedYear + 1 else selectedYear
            for (i in 1..remaining) {
                list.add(CalendarCell(i, false, Triple(nextYear, nextMonth, i)))
            }
            list
        }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = {
                Text(
                    text = "انتخاب تاریخ شمسی 📅",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "سال، ماه و روز مورد نظر خود را از تقویم زیر مشخص کنید:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Year Selector
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text("سال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))
                            var expandedYear by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { expandedYear = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(toPersianNum(selectedYear), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                DropdownMenu(
                                    expanded = expandedYear,
                                    onDismissRequest = { expandedYear = false }
                                ) {
                                    listOf(1402, 1403, 1404, 1405, 1406, 1407, 1408).forEach { yr ->
                                        DropdownMenuItem(
                                            text = { Text(toPersianNum(yr), fontSize = 12.sp) },
                                            onClick = {
                                                selectedYear = yr
                                                expandedYear = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Month Selector
                        Column(modifier = Modifier.weight(1.8f)) {
                            Text("ماه", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))
                            var expandedMonth by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { expandedMonth = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = monthsPersian.getOrNull(selectedMonth - 1) ?: selectedMonth.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedMonth,
                                    onDismissRequest = { expandedMonth = false }
                                ) {
                                    monthsPersian.forEachIndexed { idx, name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontSize = 12.sp) },
                                            onClick = {
                                                selectedMonth = idx + 1
                                                expandedMonth = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Today Button
                        Column(modifier = Modifier.weight(1.2f)) {
                            OutlinedButton(
                                onClick = {
                                    val t = getTodayShamsi()
                                    selectedYear = t.first
                                    selectedMonth = t.second
                                    selectedDay = t.third
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Today,
                                        contentDescription = "امروز",
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("امروز", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Calendar Grid Display in Persian RTL Format
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            // Day Headers
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { wd ->
                                    Text(
                                        text = wd,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (wd == "ج") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Calendar Days Cells
                            cells.chunked(7).forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    week.forEach { cell ->
                                        val isSelected = cell.isCurrentMonth && cell.day == selectedDay
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else Color.Transparent
                                                )
                                                .clickable(enabled = cell.isCurrentMonth) {
                                                    selectedDay = cell.day
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = toPersianNum(cell.day),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val yearPersian = selectedYear.toString()
                        val monthPersian = if (selectedMonth < 10) "0$selectedMonth" else selectedMonth.toString()
                        val dayPersian = if (selectedDay < 10) "0$selectedDay" else selectedDay.toString()
                        
                        fun engToPersianDigits(input: String): String {
                            val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
                            return input.map { c ->
                                if (c.isDigit()) persianDigits[c - '0'] else c
                            }.joinToString("")
                        }
                        
                        val formattedDate = "$yearPersian/$monthPersian/$dayPersian"
                        onUpdateReport(report.copy(date = engToPersianDigits(formattedDate)))
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("ثبت تاریخ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("انصراف")
                }
            }
        )
    }

    if (showWeatherPickerForActiveReport) {
        AlertDialog(
            onDismissRequest = { showWeatherPickerForActiveReport = false },
            title = {
                Text(
                    text = "انتخاب وضعیت جوی کارگاه",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Pair("آفتابی ☀️", Color(0xFFF59E0B)),
                        Pair("نیمه‌ابری ⛅", Color(0xFF60A5FA)),
                        Pair("ابری ☁️", Color(0xFF5B7181)),
                        Pair("بارانی 🌧️", Color(0xFF2563EB)),
                        Pair("برفی ❄️", Color(0xFF0EA5E9)),
                        Pair("طوفانی و باد شدید 💨", Color(0xFF475569)),
                        Pair("غبارآلود و مه پیشفرض 🌫️", Color(0xFF94A3B8))
                    ).forEach { (weatherText, color) ->
                        Card(
                            onClick = {
                                onUpdateReport(report.copy(weather = weatherText))
                                showWeatherPickerForActiveReport = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (report.weather == weatherText) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(color)
                                )
                                Text(
                                    text = weatherText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.weather == weatherText) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherPickerForActiveReport = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "مشخصات پایه و شرایط آب و هوایی",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = report.date,
                            onValueChange = { onUpdateReport(report.copy(date = it)) },
                            label = { Text("تاریخ روز") },
                            placeholder = { Text("مثال: ۱۴۰۵/۰۳/۱۵") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "انتخاب تاریخ",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showWeatherPickerForActiveReport = true }
                        ) {
                            OutlinedTextField(
                                value = report.weather,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("وضعیت هوا") },
                                trailingIcon = {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "انتخاب")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            // Transparent overlay to capture click
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Transparent)
                                    .clickable { showWeatherPickerForActiveReport = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksTab(report: DailyReport, onUpdateReport: (DailyReport) -> Unit) {
    var descInput by remember { mutableStateOf("") }
    var locInput by remember { mutableStateOf("") }
    var qtyInput by remember { mutableStateOf("") }
    var unitInput by remember { mutableStateOf("مترمکعب") }
    var accInput by remember { mutableStateOf("") }
    var startKmInput by remember { mutableStateOf("") }
    var endKmInput by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    fun formatStation(input: String): String {
        val converted = persianDigitsToEnglish(input).trim().replace(" ", "")
        if (converted.isEmpty()) return ""
        if (converted.contains("+")) {
            val parts = converted.split("+")
            if (parts.size == 2) {
                val kms = parts[0].trimStart('0').ifEmpty { "0" }
                val meters = parts[1].padStart(3, '0')
                return "$kms+$meters"
            }
            return converted
        }
        if (converted.all { it.isDigit() }) {
            val padded = converted.padStart(5, '0')
            val meters = padded.takeLast(3)
            val kms = padded.substring(0, padded.length - 3)
            val cleanKMs = kms.trimStart('0').ifEmpty { "0" }
            return "$cleanKMs+$meters"
        }
        return converted
    }

    fun calculateKmDifference(start: String, end: String): Double? {
        try {
            fun toMeters(s: String): Double {
                val converted = persianDigitsToEnglish(s)
                val trimmed = converted.trim().replace(" ", "")
                if (!trimmed.contains("+")) {
                    return trimmed.toDoubleOrNull() ?: 0.0
                }
                val parts = trimmed.split("+")
                if (parts.size == 2) {
                    val kms = parts[0].replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                    val meters = parts[1].replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                    return kms * 1000.0 + meters
                }
                return trimmed.toDoubleOrNull() ?: 0.0
            }
            val m1 = toMeters(start)
            val m2 = toMeters(end)
            return kotlin.math.abs(m2 - m1)
        } catch (e: Exception) {
            return null
        }
    }

    LaunchedEffect(startKmInput, endKmInput) {
        val sClean = persianDigitsToEnglish(startKmInput).trim().replace(" ", "")
        val eClean = persianDigitsToEnglish(endKmInput).trim().replace(" ", "")
        if (sClean.isNotEmpty() && eClean.isNotEmpty()) {
            val diff = calculateKmDifference(startKmInput, endKmInput)
            if (diff != null && diff > 0) {
                qtyInput = if (diff % 1.0 == 0.0) diff.toInt().toString() else String.format("%.1f", diff)
                unitInput = "متر"
            }
        }
    }

    val isLegal = report.reportType == "LEGAL"
    val isSurvey = report.reportType == "SURVEY"
    val isTechnical = report.reportType == "TECHNICAL"
    val isHse = report.reportType == "HSE"
    val isCustom = report.reportType == "CUSTOM"
    val hideMetrics = isTechnical || isHse || isCustom

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val customUnitTitle = remember(sharedPreferences) { sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }

    val popularUnits = when {
        isLegal -> listOf("مترمربع", "دهانه", "پلاک ثبتی", "باب مغازه", "فقره")
        isSurvey -> listOf("نقطه", "شیت نقشه", "نیم‌رخ", "برداشت مقطع", "بنچمارک")
        else -> listOf("مترمکعب", "مترمربع", "کیلوگرم", "تن", "عدد", "متر طول")
    }

    LaunchedEffect(report.reportType) {
        unitInput = when {
            isLegal -> "مترمربع"
            isSurvey -> "نقطه"
            isTechnical || isHse || isCustom -> ""
            else -> "مترمکعب"
        }
    }

    val titleText = when {
        isLegal -> "ثبت روند تحصیل اراضی و رفع معارضین ملکی"
        isSurvey -> "ثبت آمار شیت‌ها و پیاده‌سازی نقشه‌برداری امروز"
        isTechnical -> "ثبت شرح فعالیت‌های دفتر فنی"
        isHse -> "ثبت اقدامات و کنترل‌های ایمنی روزانه (HSE)"
        isCustom -> "ثبت شرح فعالیت‌های واحد $customUnitTitle"
        else -> "افزودن فعالیت اجرایی انجام شده در کارگاه"
    }

    val descLabel = when {
        isLegal -> "شرح ملک معارض، پلاک ثبتی یا نام مالک زمین"
        isSurvey -> "شرح دقیق عملیات (برداشت باند، شات‌کریت، پیاده‌سازی)"
        isTechnical -> "شرح کامل فعالیت واحد فنی"
        isHse -> "شرح اقدام یا کنترل ایمنی (مانند: گشت، آموزش)"
        isCustom -> "شرح کامل فعالیت واحد $customUnitTitle"
        else -> "شرح کامل فعالیت (مثلا: آرماتوربندی سقف دوم)"
    }

    val locLabel = when {
        isLegal -> "محدوده حریم یا موقعیت کیلومتر مسیر"
        isSurvey -> "ایستگاه پایه مستقر یا پوینت بنچمارک"
        else -> "محل حدودی"
    }

    val qtyLabel = when {
        isLegal -> "مقدار واگذار شده یا متراژ ملک معارض آزاد شده"
        isSurvey -> "میزان کارکرد دقیق امروز"
        else -> "مقدار امروز"
    }

    val accLabel = when {
        isLegal -> "شرح آخرین وضعیت حقوقی یا ملاحظات ترخیص"
        isSurvey -> "ملاحظات تحویل موقت یا پین خطا"
        isHse -> "توضیحات، دستورکار حفاظتی یا وضعیت رفع خظر"
        isCustom -> "توضیحات، اقدامات ترویجی یا ملاحظات واحد"
        else -> "توضیحات"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (editingIndex != null) "ویرایش فعالیت ردیف ${editingIndex!! + 1} ✏️" else titleText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (editingIndex != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = descInput,
                    onValueChange = { descInput = it },
                    label = { Text(descLabel) },
                    placeholder = { 
                        Text(
                            if (isHse) "مثال: کنترل لایف‌لاین جبهه کاری شمالی، گشت ایمنی..." 
                            else if (isTechnical) "مثال: تهیه صورت‌جلسه کارگاهی، متره و برآورد..." 
                            else "مثال: آرماتوربندی سقف دوم"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("task_desc_input"),
                    shape = RoundedCornerShape(24.dp)
                )

                if (!hideMetrics) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startKmInput,
                            onValueChange = { startKmInput = persianDigitsToEnglish(it) },
                            label = { Text("کیلومتر ابتدا") },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        startKmInput = formatStation(startKmInput)
                                    }
                                }
                                .testTag("task_start_km_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = endKmInput,
                            onValueChange = { endKmInput = persianDigitsToEnglish(it) },
                            label = { Text("کیلومتر انتها") },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        endKmInput = formatStation(endKmInput)
                                    }
                                }
                                .testTag("task_end_km_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = locInput,
                            onValueChange = { locInput = it },
                            label = { Text(locLabel) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("task_location_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = persianDigitsToEnglish(it) },
                            label = { Text("مقدار امروز") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("task_qty_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = unitInput,
                                onValueChange = { unitInput = it },
                                label = { Text("واحد کار") },
                                trailingIcon = {
                                    IconButton(onClick = { expanded = !expanded }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "انتخاب واحد")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("task_unit_input"),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                popularUnits.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit) },
                                        onClick = {
                                            unitInput = unit
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = accInput,
                            onValueChange = { accInput = it },
                            label = { Text("توضیحات") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("task_notes_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            )
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = accInput,
                        onValueChange = { accInput = it },
                        label = { Text(accLabel) },
                        placeholder = {
                            Text(
                                if (isHse) "نکات کلیدی، دستور کارهای ایمنی صادر شده یا وضعیت رفع خطر..."
                                else if (isCustom) "توضیحات و جزئیات تکمیلی مربوط به اقدام واحد..."
                                else "ملاحظات صورت‌جلسه، شماره شیت، مترور یا محاسب مربوطه..."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("task_notes_input"),
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (descInput.isNotBlank()) {
                            val formattedStart = formatStation(startKmInput)
                            val formattedEnd = formatStation(endKmInput)
                            val updatedTask = TaskEntry(
                                description = descInput,
                                location = locInput,
                                quantity = qtyInput,
                                unit = unitInput,
                                accumulativeQuantity = "",
                                comments = accInput,
                                startKm = formattedStart,
                                endKm = formattedEnd
                            )
                            val newTasks = report.tasks.toMutableList().apply {
                                val idx = editingIndex
                                if (idx != null && idx < size) {
                                    set(idx, updatedTask)
                                } else {
                                    add(updatedTask)
                                }
                            }
                            onUpdateReport(report.copy(tasks = newTasks))
                            // Reset inputs
                            descInput = ""
                            locInput = ""
                            qtyInput = ""
                            accInput = ""
                            startKmInput = ""
                            endKmInput = ""
                            editingIndex = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("insert_task_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD97706), // Natural Amber background
                        contentColor = Color(0xFF0F766E)    // Teal text color
                    ),
                    shape = CircleShape // Stadium-shaped button
                ) {
                    Icon(
                        imageVector = if (editingIndex != null) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = "ثبت"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (editingIndex != null) "به‌روزرسانی ردیف ${editingIndex!! + 1}" else "درج در جدول فعالیت‌های کارگاهی +",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (editingIndex != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            descInput = ""
                            locInput = ""
                            qtyInput = ""
                            accInput = ""
                            startKmInput = ""
                            endKmInput = ""
                            editingIndex = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "انصراف")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("انصراف از ویرایش", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(report.tasks) { index, task ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (editingIndex == index)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (editingIndex == index) 2.dp else 1.dp,
                        color = if (editingIndex == index) MaterialTheme.colorScheme.primary else BorderColor
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = task.description,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    descInput = task.description
                                    locInput = task.location
                                    qtyInput = task.quantity
                                    unitInput = task.unit
                                    accInput = task.comments.ifEmpty { task.accumulativeQuantity }
                                    startKmInput = task.startKm
                                    endKmInput = task.endKm
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    val newTasks = report.tasks.toMutableList().apply { removeAt(index) }
                                    onUpdateReport(report.copy(tasks = newTasks))
                                    if (editingIndex == index) {
                                        editingIndex = null
                                        descInput = ""
                                        locInput = ""
                                        qtyInput = ""
                                        accInput = ""
                                        startKmInput = ""
                                        endKmInput = ""
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Divider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val kmStr = if (task.startKm.isNotEmpty() && task.endKm.isNotEmpty()) {
                                "کیلومتر: ${task.startKm} الی ${task.endKm}"
                            } else {
                                "محل: ${task.location.ifEmpty { "عمومی کارگاه" }}"
                            }
                            Text(kmStr, fontSize = 12.sp)
                            Text("کارکرد امروز: ${task.quantity} ${task.unit}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            Text("توضیحات: ${task.comments.ifEmpty { task.accumulativeQuantity.ifEmpty { "---" } }}", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MachineryTab(report: DailyReport, onUpdateReport: (DailyReport) -> Unit) {
    var typeInput by remember { mutableStateOf("") }
    var activeInput by remember { mutableStateOf(1) }
    var inactiveInput by remember { mutableStateOf(0) }
    var hoursInput by remember { mutableStateOf("") }
    var commentsInput by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var machineryHistory by remember {
        mutableStateOf(sharedPreferences.getStringSet("machinery_history", emptySet()) ?: emptySet())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (editingIndex != null) "ویرایش ماشین‌آلات ردیف ${editingIndex!! + 1} ✏️" else "ثبت اطلاعات ماشین‌آلات و تجهیزات کارگاه 🚜",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = typeInput,
                            onValueChange = { typeInput = it },
                            label = { Text("نام و مدل تجهیزات") },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "پیشنهادات", tint = Color(0xFF0F766E))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("machinery_type_input"),
                            shape = RoundedCornerShape(24.dp)
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            val historyList = machineryHistory.toList().sorted()
                            if (historyList.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("تاریخچه خالی است (نام جدید بنویسید)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp) },
                                    onClick = { expanded = false }
                                )
                            } else {
                                historyList.forEach { mac ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth().widthIn(min = 220.dp)
                                            ) {
                                                Text(mac, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                IconButton(
                                                    onClick = {
                                                        val updated = machineryHistory.toMutableSet().apply { remove(mac) }
                                                        machineryHistory = updated
                                                        sharedPreferences.edit().putStringSet("machinery_history", updated).apply()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "حذف رکورد",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            typeInput = mac
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("تعداد فعال", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceAround,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { if (activeInput > 0) activeInput-- },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color(0xFFFCE8E6),
                                        contentColor = Color(0xFFC5221F)
                                    )
                                ) {
                                    Text("-", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Text("$activeInput", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                IconButton(
                                    onClick = { activeInput++ },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color(0xFFE6F4EA),
                                        contentColor = Color(0xFF137333)
                                    )
                                ) {
                                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("خراب/متوقف", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC5221F))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceAround,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { if (inactiveInput > 0) inactiveInput-- },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color(0xFFFCE8E6),
                                        contentColor = Color(0xFFC5221F)
                                    )
                                ) {
                                    Text("-", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Text("$inactiveInput", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                IconButton(
                                    onClick = { inactiveInput++ },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color(0xFFE6F4EA),
                                        contentColor = Color(0xFF137333)
                                    )
                                ) {
                                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = hoursInput,
                            onValueChange = { hoursInput = persianDigitsToEnglish(it) },
                            label = { Text("ساعت کارکرد") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("machinery_hours_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = commentsInput,
                            onValueChange = { commentsInput = it },
                            label = { Text("توضیحات") },
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("machinery_fuel_input"),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (typeInput.isNotBlank()) {
                                val trimmed = typeInput.trim()
                                if (!machineryHistory.contains(trimmed)) {
                                    val newHistory = machineryHistory.toMutableSet().apply { add(trimmed) }
                                    sharedPreferences.edit().putStringSet("machinery_history", newHistory).apply()
                                    machineryHistory = newHistory
                                }

                                val updatedMac = MachineryEntry(
                                    type = trimmed,
                                    activeCount = activeInput,
                                    inactiveCount = inactiveInput,
                                    workingHours = hoursInput,
                                    comments = commentsInput
                                )

                                val newMachinery = report.machinery.toMutableList().apply {
                                    val idx = editingIndex
                                    if (idx != null && idx < size) {
                                        set(idx, updatedMac)
                                    } else {
                                        add(updatedMac)
                                    }
                                }
                                onUpdateReport(report.copy(machinery = newMachinery))
                                typeInput = ""
                                activeInput = 1
                                inactiveInput = 0
                                hoursInput = ""
                                commentsInput = ""
                                editingIndex = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("insert_machinery_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = if (editingIndex != null) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = "ثبت"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (editingIndex != null) "اصلاح ردیف شماره ${editingIndex!! + 1}" else "درج در جدول ماشین‌آلات کارگاه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (editingIndex != null) {
                        Button(
                            onClick = {
                                typeInput = ""
                                activeInput = 1
                                inactiveInput = 0
                                hoursInput = ""
                                commentsInput = ""
                                editingIndex = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "انصراف")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("انصراف از ویرایش ردیف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (report.machinery.isNotEmpty()) {
            item {
                Text(
                    text = "لیست ماشین‌آلات ثبت شده 👇",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F766E),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            itemsIndexed(report.machinery) { index, mac ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (editingIndex == index)
                            Color(0xFFFEF3C7).copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        width = if (editingIndex == index) 2.dp else 1.dp,
                        color = if (editingIndex == index) Color(0xFFD97706) else BorderColor
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE6F4F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = mac.type,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    typeInput = mac.type
                                    activeInput = mac.activeCount
                                    inactiveInput = mac.inactiveCount
                                    hoursInput = mac.workingHours
                                    commentsInput = mac.comments
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش",
                                    tint = Color(0xFF0F766E)
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    val newMachinery = report.machinery.toMutableList().apply { removeAt(index) }
                                    onUpdateReport(report.copy(machinery = newMachinery))
                                    if (editingIndex == index) {
                                        editingIndex = null
                                        typeInput = ""
                                        activeInput = 1
                                        inactiveInput = 0
                                        hoursInput = ""
                                        commentsInput = ""
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Divider(color = BorderColor, modifier = Modifier.padding(vertical = 6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🟢 فعال: ${mac.activeCount} دستگاه | 🔴 خراب: ${mac.inactiveCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "⏱️ ${mac.workingHours.ifEmpty { "۰" }} ساعت",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E)
                            )
                        }
                        
                        if (mac.comments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "📝 توضیحات و سوخت: { ${mac.comments} }",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManpowerTab(report: DailyReport, onUpdateReport: (DailyReport) -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    var roleInput by remember { mutableStateOf("") }
    var countInput by remember { mutableStateOf(1) } // Default to 1 always, as each row is an individual staff member
    var commentsInput by remember { mutableStateOf("") }
    var employmentTypeInput by remember { mutableStateOf("COMPANY") }
    var subcontractorNameInput by remember { mutableStateOf("") }
    var isOnLeaveInput by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    
    // Dynamic history for manpower roles
    var manpowerRolesHistory by remember {
        mutableStateOf(sharedPreferences.getStringSet("manpower_roles_history", 
            setOf("مهندس ناظر عمران", "سرپرست کارگاه", "کارگر ساده مقیم", "بنا و معمار کارآزموده", "راننده وسایل باری", "جوشکار صنعتی", "آرماتوربند")
        ) ?: emptySet())
    }

    var manpowerNamesHistory by remember {
        mutableStateOf(sharedPreferences.getStringSet("manpower_names_history", emptySet()) ?: emptySet())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (editingIndex != null) "ویرایش پرسنل ردیف ${editingIndex!! + 1} ✏️" else "ثبت اطلاعات نیروی انسانی شاغل کارگاه 👷‍♂️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    var nameExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("نام و نام خانوادگی") },
                            placeholder = { Text("مثال: علی رضایی") },
                            trailingIcon = {
                                IconButton(onClick = { nameExpanded = !nameExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "اسامی قبلی", tint = Color(0xFF0F766E))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manpower_title_input"),
                            shape = RoundedCornerShape(24.dp)
                        )
                        DropdownMenu(
                            expanded = nameExpanded,
                            onDismissRequest = { nameExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            val namesList = manpowerNamesHistory.toList().sorted()
                            if (namesList.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("تاریخچه خالی است (اسم جدید بنویسید)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp) },
                                    onClick = { nameExpanded = false }
                                )
                            } else {
                                namesList.forEach { name ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth().widthIn(min = 220.dp)
                                            ) {
                                                Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                IconButton(
                                                    onClick = {
                                                        val updated = manpowerNamesHistory.toMutableSet().apply { remove(name) }
                                                        manpowerNamesHistory = updated
                                                        sharedPreferences.edit().putStringSet("manpower_names_history", updated).apply()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "حذف رکورد",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            nameInput = name
                                            nameExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = roleInput,
                        onValueChange = { roleInput = it },
                        label = { Text("سمت") },
                        placeholder = { Text("مثال: آرماتوربند / کارگر ساده / راننده") },
                        trailingIcon = {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = "سمت‌ها", tint = Color(0xFF0F766E))
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    manpowerRolesHistory.toList().sorted().forEach { role ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth().widthIn(min = 220.dp)
                                                ) {
                                                    Text(role, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = {
                                                            val updated = manpowerRolesHistory.toMutableSet().apply { remove(role) }
                                                            manpowerRolesHistory = updated
                                                            sharedPreferences.edit().putStringSet("manpower_roles_history", updated).apply()
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "حذف رکورد",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                roleInput = role
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manpower_role_input"),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    // Segmented Selector for Employment / Association Type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isCompany = employmentTypeInput == "COMPANY"
                        
                        OutlinedButton(
                            onClick = { employmentTypeInput = "COMPANY" },
                            modifier = Modifier.weight(1f),
                            colors = if (isCompany) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF0F766E).copy(alpha = 0.12f),
                                    contentColor = Color(0xFF0F766E)
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isCompany) Color(0xFF0F766E) else BorderColor
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("شرکتی (امانی)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        
                        OutlinedButton(
                            onClick = { employmentTypeInput = "SUBCONTRACTOR" },
                            modifier = Modifier.weight(1f),
                            colors = if (!isCompany) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFFD97706).copy(alpha = 0.12f),
                                    contentColor = Color(0xFFD97706)
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            border = BorderStroke(
                                1.dp,
                                if (!isCompany) Color(0xFFD97706) else BorderColor
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("پیمانکار", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (employmentTypeInput == "SUBCONTRACTOR") {
                        OutlinedTextField(
                            value = subcontractorNameInput,
                            onValueChange = { subcontractorNameInput = it },
                            label = { Text("نام پیمانکار") },
                            placeholder = { Text("مثال: پیمانکاری احمدی") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = commentsInput,
                        onValueChange = { commentsInput = it },
                        label = { Text("توضیحات") },
                        placeholder = { Text("سایر توضیحات مربوط به پرسنل و فعالیت‌ها") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manpower_details_input"),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    // Compact Checkbox for Leave status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = isOnLeaveInput,
                            onCheckedChange = { isOnLeaveInput = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = Color(0xFFD97706)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "این فرد در مرخصی است ✈️ (بدون محاسبه در آمار فعالان)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnLeaveInput) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                val trimmedName = nameInput.trim()
                                val trimmedRole = roleInput.trim()
                                if (trimmedRole.isNotBlank() && !manpowerRolesHistory.contains(trimmedRole)) {
                                    val newHistory = manpowerRolesHistory.toMutableSet().apply { add(trimmedRole) }
                                    sharedPreferences.edit().putStringSet("manpower_roles_history", newHistory).apply()
                                    manpowerRolesHistory = newHistory
                                }
                                if (trimmedName.isNotBlank() && !manpowerNamesHistory.contains(trimmedName)) {
                                    val newNames = manpowerNamesHistory.toMutableSet().apply { add(trimmedName) }
                                    sharedPreferences.edit().putStringSet("manpower_names_history", newNames).apply()
                                    manpowerNamesHistory = newNames
                                }

                                val updatedPerson = ManpowerEntry(
                                    name = trimmedName,
                                    role = trimmedRole,
                                    count = 1,
                                    comments = commentsInput,
                                    employmentType = employmentTypeInput,
                                    subcontractorName = if (employmentTypeInput == "COMPANY") "" else subcontractorNameInput,
                                    isOnLeave = isOnLeaveInput
                                )

                                val newManpower = report.manpower.toMutableList().apply {
                                    val idx = editingIndex
                                    if (idx != null && idx < size) {
                                        set(idx, updatedPerson)
                                    } else {
                                        add(updatedPerson)
                                    }
                                }
                                onUpdateReport(report.copy(manpower = newManpower))
                                nameInput = ""
                                roleInput = ""
                                countInput = 1
                                commentsInput = ""
                                employmentTypeInput = "COMPANY"
                                subcontractorNameInput = ""
                                isOnLeaveInput = false
                                editingIndex = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("insert_manpower_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = if (editingIndex != null) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = "ثبت"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (editingIndex != null) "اصلاح ردیف شماره ${editingIndex!! + 1}" else "درج در جدول نیروهای انسانی کارگاه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (editingIndex != null) {
                        Button(
                            onClick = {
                                nameInput = ""
                                roleInput = ""
                                countInput = 1
                                commentsInput = ""
                                employmentTypeInput = "COMPANY"
                                subcontractorNameInput = ""
                                isOnLeaveInput = false
                                editingIndex = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "انصراف")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("انصراف از ویرایش ردیف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (report.manpower.isNotEmpty()) {
            item {
                Text(
                    text = "لیست نیروهای انسانی شاغل ثبت شده 👇",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F766E),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            itemsIndexed(report.manpower) { index, person ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (editingIndex == index)
                            Color(0xFFFEF3C7).copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        width = if (editingIndex == index) 2.dp else 1.dp,
                        color = if (editingIndex == index) Color(0xFFD97706) else BorderColor
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE6F4F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = person.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = if (person.isOnLeave) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (person.isOnLeave) {
                                         Spacer(modifier = Modifier.width(6.dp))
                                         Surface(
                                             shape = RoundedCornerShape(8.dp),
                                             color = Color(0xFFFEF2F2),
                                             contentColor = Color(0xFFEF4444)
                                         ) {
                                             Text(
                                                 text = "مرخصی ✈️",
                                                 fontSize = 10.sp,
                                                 fontWeight = FontWeight.Bold,
                                                 modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                             )
                                         }
                                    }
                                }
                                if (person.role.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "سمت: ${person.role}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (person.isOnLeave) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Badge indicating employment status
                                val isSub = person.employmentType == "SUBCONTRACTOR"
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSub) Color(0xFFFEF3C7).copy(alpha = 0.8f) else Color(0xFFE6F4F1),
                                    contentColor = if (isSub) Color(0xFFD97706) else Color(0xFF0F766E)
                                ) {
                                    Text(
                                        text = if (isSub) {
                                            if (person.subcontractorName.isNotEmpty()) "پیمانکار: ${person.subcontractorName}" else "پیمانکار"
                                        } else {
                                            "شرکتی (امانی)"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    nameInput = person.name
                                    roleInput = person.role
                                    countInput = person.count
                                    commentsInput = person.comments
                                    employmentTypeInput = person.employmentType.ifEmpty { "COMPANY" }
                                    subcontractorNameInput = person.subcontractorName
                                    isOnLeaveInput = person.isOnLeave
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش",
                                    tint = Color(0xFF0F766E)
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    val newManpower = report.manpower.toMutableList().apply { removeAt(index) }
                                    onUpdateReport(report.copy(manpower = newManpower))
                                    if (editingIndex == index) {
                                        editingIndex = null
                                        nameInput = ""
                                        roleInput = ""
                                        countInput = 1
                                        commentsInput = ""
                                        employmentTypeInput = "COMPANY"
                                        subcontractorNameInput = ""
                                        isOnLeaveInput = false
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (person.comments.isNotEmpty()) {
                            Divider(color = BorderColor, modifier = Modifier.padding(vertical = 6.dp))
                            Text(
                                text = "📝 توضیحات: { ${person.comments} }",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaterialsTab(report: DailyReport, onUpdateReport: (DailyReport) -> Unit) {
    var typeInput by remember { mutableStateOf("") }
    var countInput by remember { mutableStateOf("") }
    var unitInput by remember { mutableStateOf("عدد") }
    var qtyInput by remember { mutableStateOf("") }
    var loadInput by remember { mutableStateOf("") }
    var unloadInput by remember { mutableStateOf("") }
    var timeInput by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    
    // Dynamically saved / loaded autocomplete history for site materials (Task 9)
    var materialsHistory by remember {
        mutableStateOf(sharedPreferences.getStringSet("site_materials_history", 
            setOf("سیمان تیپ ۲ شیراز", "سرامیک پرسلان ۶۰*۱۲۰", "آرماتور سایز ۱۸", "شن و ماسه شسته شده", "سازه آلومینیومی", "فوم سقف پلی‌استایرن")
        ) ?: emptySet())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (editingIndex != null) "ویرایش ردیف مصالح ${editingIndex!! + 1} ✏️" else "ثبت مصالح و متریال وارده به کارگاه 👷‍♂️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E), // NaturalAmber or MutedTealGreen
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = typeInput,
                        onValueChange = { typeInput = it },
                        label = { Text("نوع مصالح وارده") },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(imageVector = Icons.Default.List, contentDescription = "تاریخچه مصالح", tint = Color(0xFF0F766E))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        materialsHistory.toList().sorted().forEach { mat ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().widthIn(min = 220.dp)
                                    ) {
                                        Text(mat, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                val updated = materialsHistory.toMutableSet().apply { remove(mat) }
                                                materialsHistory = updated
                                                sharedPreferences.edit().putStringSet("site_materials_history", updated).apply()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "حذف رکورد",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    typeInput = mat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = countInput,
                        onValueChange = { countInput = persianDigitsToEnglish(it) },
                        label = { Text("تعداد / جعبه") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = unitInput,
                        onValueChange = { unitInput = it },
                        label = { Text("واحد") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = persianDigitsToEnglish(it) },
                        label = { Text("وزن / مقدار") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = loadInput,
                        onValueChange = { loadInput = it },
                        label = { Text("مبدا بارگیری") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = unloadInput,
                        onValueChange = { unloadInput = it },
                        label = { Text("محل تخلیه") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text("ساعت") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Modern stadium-shaped filled/tonal button
                Button(
                    onClick = {
                        if (typeInput.isNotBlank()) {
                            val trimmed = typeInput.trim()
                            if (!materialsHistory.contains(trimmed)) {
                                val newHistory = materialsHistory.toMutableSet().apply { add(trimmed) }
                                sharedPreferences.edit().putStringSet("site_materials_history", newHistory).apply()
                                materialsHistory = newHistory
                            }

                            val updatedMat = MaterialEntry(
                                type = trimmed,
                                count = countInput,
                                unit = unitInput,
                                quantity = qtyInput,
                                loadingLocation = loadInput,
                                unloadingLocation = unloadInput,
                                unloadingTime = timeInput,
                                isExit = false
                            )

                            val newMaterials = report.materials.toMutableList().apply {
                                val idx = editingIndex
                                if (idx != null && idx < size) {
                                    set(idx, updatedMat)
                                } else {
                                    add(updatedMat)
                                }
                            }
                            onUpdateReport(report.copy(materials = newMaterials))
                            typeInput = ""
                            countInput = ""
                            qtyInput = ""
                            loadInput = ""
                            unloadInput = ""
                            timeInput = ""
                            editingIndex = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E), // natural amber or teal
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = if (editingIndex != null) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = "ثبت مصالح"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (editingIndex != null) "اصلاح ردیف شماره ${editingIndex!! + 1}" else "درج در جدول مصالح کارگاه",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (editingIndex != null) {
                    Button(
                        onClick = {
                            typeInput = ""
                            countInput = ""
                            qtyInput = ""
                            loadInput = ""
                            unloadInput = ""
                            timeInput = ""
                            editingIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "انصراف")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("انصراف از ویرایش ردیف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(report.materials) { index, material ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (editingIndex == index)
                            Color(0xFFFEF3C7).copy(alpha = 0.4f) // Soft amber tint when editing
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        width = if (editingIndex == index) 2.dp else 1.dp,
                        color = if (editingIndex == index) Color(0xFFD97706) else BorderColor
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Beautiful small badge indicator
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE6F4F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = material.type,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    typeInput = material.type
                                    countInput = material.count
                                    unitInput = material.unit
                                    qtyInput = material.quantity
                                    loadInput = material.loadingLocation
                                    unloadInput = material.unloadingLocation
                                    timeInput = material.unloadingTime
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش",
                                    tint = Color(0xFF0F766E)
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    val newMaterials = report.materials.toMutableList().apply { removeAt(index) }
                                    onUpdateReport(report.copy(materials = newMaterials))
                                    if (editingIndex == index) {
                                        editingIndex = null
                                        typeInput = ""
                                        countInput = ""
                                        qtyInput = ""
                                        loadInput = ""
                                        unloadInput = ""
                                        timeInput = ""
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Divider(color = BorderColor, modifier = Modifier.padding(vertical = 6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مقدار/تعداد: ${material.count} ${material.unit} (${material.quantity})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "مخزن/تخلیه: ${material.unloadingLocation.ifEmpty { "ناظر کارگاه" }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (material.loadingLocation.isNotEmpty() || material.unloadingTime.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (material.loadingLocation.isNotEmpty()) {
                                    Text(
                                        text = "📍 مبدا بارگیری: ${material.loadingLocation}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                                if (material.unloadingTime.isNotEmpty()) {
                                    Text(
                                        text = "⏳ ساعت تخلیه: ${material.unloadingTime}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F766E)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tailored module specifically for Warehouse entries and exits
@Composable
fun WarehouseMaterialsTab(
    report: DailyReport,
    isExit: Boolean,
    onUpdateReport: (DailyReport) -> Unit
) {
    var typeInput by remember { mutableStateOf("") }
    var qtyInput by remember { mutableStateOf("") }
    var unitInput by remember { mutableStateOf("پاکت") }
    var pointInput by remember { mutableStateOf("") } // Unloading Location for imports, Receiver for exports
    var commentsInput by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val isLegal = report.reportType == "LEGAL"
    val isSurvey = report.reportType == "SURVEY"
    val isTechnical = report.reportType == "TECHNICAL"

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    
    // Suggestion options dynamically from history per section (Task 9)
    val historyKey = when {
        isLegal -> "warehouse_legal_history"
        isSurvey -> "warehouse_survey_history"
        isTechnical -> "warehouse_technical_history"
        isExit -> "warehouse_exit_history"
        else -> "warehouse_import_history"
    }
    
    val presetDefaultOptions = when {
        isLegal -> setOf("استعلام حریم لوله گاز", "مجوز قطع اشجار شهرداری", "استعلام اداره کل راه و شهرسازی", "صورت‌جلسه توافق مالکین زمین", "مجوز حریم فشار قوی برق")
        isSurvey -> setOf("برداشت توپوگرافی باند حریم", "GPS دو فرکانسه ایستگاه جدید", "پیاده‌سازی آکس دیوار غربی", "کدگذاری ترانشه حفاری", "شیت شاقولی فونداسیون")
        isTechnical -> setOf("کابل فیبر نوری زره‌دار", "سنسور حرارتی بتن دیجیتال", "لوله مسی چاه تهویه اسپیلت", "الکترود جوشکاری دکمه‌ای ایساب", "پیچ و مهره گرید ۸.۸ سازه فلزی", "رزین اپوکسی اتصال بتن آب‌بند")
        else -> setOf("سیمان تیپ ۲ پاکتی", "گچ جبل الگری", "آجر لفتون سفالی", "یونولیت سقفی فشرده", "لوله پی‌وی‌سی سایز ۱۱۰", "شیرآلات صنعتی برنجی", "مفتول آرماتوربندی")
    }
    
    var dynamicOptions by remember {
        mutableStateOf(sharedPreferences.getStringSet(historyKey, presetDefaultOptions) ?: presetDefaultOptions)
    }

    val titleText = when {
        isLegal -> if (editingIndex != null) "ویرایش ردیف استعلام/مجوز ${editingIndex!! + 1} ✏️" else "ثبت روند اخذ مجوزات یا استعلامات مربوطه ⚖️"
        isSurvey -> if (editingIndex != null) "ویرایش ردیف پیش‌بینی ${editingIndex!! + 1} ✏️" else "پیش‌بینی و برنامه‌ریزی عملیات نقشه‌برداری فردا 🧭"
        isTechnical -> if (editingIndex != null) "ویرایش متریال خاص ردیف ${editingIndex!! + 1} ✏️" else "ثبت آمار مصالح تخصصی وارده به کارگاه 📦"
        isExit -> if (editingIndex != null) "ویرایش کالا صادره ردیف ${editingIndex!! + 1} ✏️" else "ثبت خروج مصالح و کالا از انبار (صادره) 📤"
        else -> if (editingIndex != null) "ویرایش ورود مصالح ردیف ${editingIndex!! + 1} ✏️" else "ثبت ورود مصالح به انبار کارگاه (وارده) 📥"
    }

    val typeLabel = when {
        isLegal -> "عنوان مجوز مورد نیاز یا مکاتبه استعلام"
        isSurvey -> "شرح کارهای پیش‌بینی شده فردا"
        isTechnical -> "نوع مصالح تخصص یا تکنولوژیک وارده"
        else -> "نوع کالا یا مصالح"
    }

    val destinationLabel = when {
        isLegal -> "سازمان پیگیری‌کننده / ارگان هدف"
        isSurvey -> "موقعییت فرضی فردا (کیلومتر ابتدا الی انتها یا ردیف)"
        isTechnical -> "محل استقرار یا مخزن تخلیه فنی"
        isExit -> "تحویل گیرنده (اکیپ / شخص مصرف‌کننده)"
        else -> "محل تخلیه / قفسه انبار"
    }

    val commentsLabel = when {
        isLegal -> "شرح آخرین وضعیت یا اقدامات حقوقی انجام شده"
        isSurvey -> "ملاحظات سنجش / تراز مبناء پیش‌نیاز"
        isTechnical -> "اطلاعات بارنامه / تاییدیه آزمایشگاه فنی ملحق"
        else -> "توضیحات تکمیلی یا کیفیت ظاهری"
    }

    val materialsFiltered = report.materials.filter { it.isExit == isExit }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isExit || isSurvey || editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E),
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = typeInput,
                        onValueChange = { typeInput = it },
                        label = { Text(typeLabel) },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(imageVector = Icons.Default.List, contentDescription = "تاریخچه انبار", tint = Color(0xFF0F766E))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        dynamicOptions.toList().sorted().forEach { mat ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().widthIn(min = 220.dp)
                                    ) {
                                        Text(mat, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                val updated = dynamicOptions.toMutableSet().apply { remove(mat) }
                                                dynamicOptions = updated
                                                sharedPreferences.edit().putStringSet(historyKey, updated).apply()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "حذف رکورد",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    typeInput = mat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Quantity fields - hidden for legal, because permissions don't have kilograms/meters
                if (!isLegal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = persianDigitsToEnglish(it) },
                            label = { Text(if (isSurvey) "حجم کار برآوردی" else "مقدار کالا") },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = unitInput,
                            onValueChange = { unitInput = it },
                            label = { Text("واحد اندازه‌گیری") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                    }
                }

                OutlinedTextField(
                    value = pointInput,
                    onValueChange = { pointInput = it },
                    label = { Text(destinationLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = commentsInput,
                    onValueChange = { commentsInput = it },
                    label = { Text(commentsLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (typeInput.isNotBlank() && (qtyInput.isNotBlank() || isLegal)) {
                            val trimmedType = typeInput.trim()
                            if (!dynamicOptions.contains(trimmedType)) {
                                val newOptions = dynamicOptions.toMutableSet().apply { add(trimmedType) }
                                sharedPreferences.edit().putStringSet(historyKey, newOptions).apply()
                                dynamicOptions = newOptions
                            }

                            val targetQty = if (isLegal) "۱" else qtyInput
                            val targetUnit = if (isLegal) "سند" else unitInput
                            val splittedLoc = if (isSurvey) {
                                val parts = pointInput.split("الی")
                                Pair(parts.firstOrNull()?.trim() ?: pointInput, parts.getOrNull(1)?.trim() ?: "")
                            } else {
                                Pair("", "")
                            }

                            val updatedItem = MaterialEntry(
                                type = trimmedType,
                                quantity = targetQty,
                                unit = targetUnit,
                                receiver = if (isExit || isLegal) pointInput else "",
                                unloadingLocation = if (isSurvey) splittedLoc.second else (if (!isExit && !isLegal) pointInput else ""),
                                loadingLocation = splittedLoc.first,
                                unloadingTime = "",
                                count = if (isSurvey) qtyInput else "",
                                comments = commentsInput,
                                isExit = isExit
                            )

                            val newMaterials = report.materials.toMutableList()
                            val idx = editingIndex
                            if (idx != null && idx < materialsFiltered.size) {
                                val originalItem = materialsFiltered[idx]
                                val originalIndexInMain = report.materials.indexOf(originalItem)
                                if (originalIndexInMain != -1) {
                                    newMaterials[originalIndexInMain] = updatedItem
                                } else {
                                    newMaterials.add(updatedItem)
                                }
                            } else {
                                newMaterials.add(updatedItem)
                            }

                            onUpdateReport(report.copy(materials = newMaterials))
                            typeInput = ""
                            qtyInput = ""
                            pointInput = ""
                            commentsInput = ""
                            editingIndex = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExit || isSurvey || editingIndex != null) Color(0xFFD97706) else Color(0xFF0F766E),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = if (editingIndex != null) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = "ثبت"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (editingIndex != null) "اصلاح ردیف شماره ${editingIndex!! + 1}" else when {
                            isLegal -> "افزودن به جدول مجوزها"
                            isSurvey -> "ثبت در جدول پیش‌بینی نقشه‌برداری"
                            isExit -> "درج در جدول خروجی‌های انبار"
                            else -> "درج در جدول ورودی‌های انبار"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (editingIndex != null) {
                    Button(
                        onClick = {
                            typeInput = ""
                            qtyInput = ""
                            pointInput = ""
                            commentsInput = ""
                            editingIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "انصراف")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("انصراف از ویرایش ردیف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(materialsFiltered) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (editingIndex == index)
                            Color(0xFFFEF3C7).copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(
                        width = if (editingIndex == index) 2.dp else 1.dp,
                        color = if (editingIndex == index) Color(0xFFD97706) else BorderColor
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE6F4F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = item.type,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    typeInput = item.type
                                    qtyInput = if (isLegal) "" else item.quantity
                                    unitInput = item.unit
                                    pointInput = when {
                                        isSurvey -> {
                                            if (item.loadingLocation.isNotEmpty() && item.unloadingLocation.isNotEmpty()) {
                                                "${item.loadingLocation} الی ${item.unloadingLocation}"
                                            } else {
                                                item.loadingLocation.ifEmpty { item.unloadingLocation }
                                            }
                                        }
                                        isExit || isLegal -> item.receiver
                                        else -> item.unloadingLocation
                                    }
                                    commentsInput = item.comments
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش ردیف",
                                    tint = Color(0xFF0F766E)
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    val indexInMain = report.materials.indexOf(item)
                                    if (indexInMain != -1) {
                                        val newMainList = report.materials.toMutableList().apply { removeAt(indexInMain) }
                                        onUpdateReport(report.copy(materials = newMainList))
                                    }
                                    if (editingIndex == index) {
                                        editingIndex = null
                                        typeInput = ""
                                        qtyInput = ""
                                        pointInput = ""
                                        commentsInput = ""
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, "حذف رکورد", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Divider(color = BorderColor, modifier = Modifier.padding(vertical = 6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLegal) {
                                Text(
                                    text = "🏢 مرجع هدف: ${item.receiver.ifEmpty { "---" }}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                            } else {
                                Text(
                                    text = "مقدار ثبت شده: ${item.quantity} ${item.unit}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isSurvey) {
                                    Text(
                                        text = "📍 بازه فرضی: ${item.loadingLocation.ifEmpty { "سراسر" }} الی ${item.unloadingLocation.ifEmpty { "کارگاه" }}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (isExit) {
                                    Text(
                                        text = "👤 تحویل کاربری: ${item.receiver.ifEmpty { "---" }}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "📦 انبارداری: ${item.unloadingLocation.ifEmpty { "انبار مرکزی" }}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        if (item.comments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "📝 توضیحات تکمیلی: { ${item.comments} }",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotesTab(report: DailyReport, onUpdateReport: (DailyReport) -> Unit) {
    val isWarehouse = report.reportType == "WAREHOUSE"
    val focusManager = LocalFocusManager.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Handy close-keyboard floating helper for maximum ergonomic comfort (Task 6)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { focusManager.clearFocus() },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF0F766E))
            ) {
                Icon(
                     imageVector = Icons.Default.Close,
                     contentDescription = "پنهان کردن کیبورد",
                     modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("بستن صفحه کلید", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "موانع و مشکلات",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWarehouse) "موانع، مشکلات، کسری‌های انبار 🛑" else "موانع، مشکلات و نواقص کارگاهی 🛑",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F766E)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isWarehouse) "مانند گزارش کسری‌های شدید اقلام، تاخیر در تخلیه وسایل باری، بارهای برگشت‌خورده و..."
                                   else "مانند: خرابی ماشین‌آلات کلیدی، عدم تامین متریال لازم، شرایط جوی سهمگین، تاخیرات ترابری و...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = report.obstacles,
                            onValueChange = { onUpdateReport(report.copy(obstacles = it)) },
                            placeholder = { 
                                if (isWarehouse) Text("مثال: عدم تحویل به موقع محموله سیمان فله یا رطوبت شدید انبار فرعی...")
                                else Text("مثال: خرابی گریدر خط آبرسانی در بخش کاتر، عدم دسترسی آسان به زون ۲ به دلیل گل سنگین...")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 5,
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.None
                            )
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "اصلاحات و پیش‌بینی",
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWarehouse) "پیش‌بینی نیاز انبار کارگاه برای فردا ✍️" else "پیش‌بینی برنامه فعالیت‌های روز آینده ✍️",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F766E)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isWarehouse) "ذکر لیست کالاها و مصالحی که فردا باید سفارش یا تامین شوند"
                                   else "فعالیت‌هایی که برای روال کار بهینه‌تر فردا مد نظر دارید را به صورت موردی ذکر کنید.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = report.tomorrowPlan,
                            onValueChange = { onUpdateReport(report.copy(tomorrowPlan = it)) },
                            placeholder = { 
                                if (isWarehouse) Text("مثال: پیگیری تخلیه بار سرامیک پرسلان فونداسیون...")
                                else Text("مثال: بتن‌ریزی رینگ لبه سقف دوم، بارگیری آرماتورهای ستونی کارهای تکمیلی...")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 5,
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.None
                            )
                        )
                    }
                }
            }
        }
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

@Composable
fun ProjectDashboardTab(reportsCount: Int, reports: List<com.example.data.model.DailyReport>, viewModel: com.example.ui.viewmodel.ReportViewModel) {
    val totalPersonnel = reports.fold(0) { sum, r -> sum + r.manpower.fold(0) { innerSum, p -> innerSum + (if (p.isOnLeave) 0 else p.count) } }
    val avgPersonnel = if (reports.isNotEmpty()) totalPersonnel / reports.size else 0
    val personnelVal = if (reports.isEmpty()) "۰ نفر (ثبت‌نشده)" else "$avgPersonnel نفر"

    val totalMachinery = reports.fold(0) { sum, r -> sum + r.machinery.fold(0) { innerSum, m -> innerSum + m.activeCount } }
    val avgMachinery = if (reports.isNotEmpty()) totalMachinery / reports.size else 0
    val machineryVal = if (reports.isEmpty()) "۰ دستگاه (ثبت‌نشده)" else "$avgMachinery دستگاه"

    var recentActivities by remember {
        mutableStateOf(viewModel.sharedPreferences.getString("dashboard_recent_activities", "") ?: "")
    }
    var tomorrowForecast by remember {
        mutableStateOf(viewModel.sharedPreferences.getString("dashboard_tomorrow_forecast", "") ?: "")
    }

    val generatedRecentActivities = remember(reports) {
        if (reports.isEmpty()) "" else {
            val sb = StringBuilder()
            val lastReports = reports.take(2)
            lastReports.forEachIndexed { idx, report ->
                val typeStr = if (report.reportType == "WAREHOUSE") "انبار" else "اجرا"
                sb.append("📋 گزارش $typeStr مورخ ${report.date}:\n")
                if (report.tasks.isEmpty() && report.materials.isEmpty()) {
                    sb.append("• موردی ثبت نشده است\n")
                } else {
                    if (report.reportType == "WAREHOUSE") {
                        val matExits = report.materials.filter { it.isExit }.take(3)
                        val matEntries = report.materials.filter { !it.isExit }.take(3)
                        if (matEntries.isNotEmpty()) {
                            sb.append("   📥 ورود کالا: ${matEntries.joinToString("، ") { "${it.type} (${it.count} ${it.unit})" }}\n")
                        }
                        if (matExits.isNotEmpty()) {
                            sb.append("   📤 خروج کالا: ${matExits.joinToString("، ") { "${it.type} (${it.count} ${it.unit})" }}\n")
                        }
                    } else {
                        report.tasks.take(4).forEach { task ->
                            val kmStr = if (task.startKm.isNotEmpty() || task.endKm.isNotEmpty()) {
                                " (کیلومتر ${task.startKm} الی ${task.endKm})"
                            } else ""
                            sb.append("   • ${task.description}$kmStr\n")
                        }
                    }
                }
                if (idx < lastReports.size - 1) sb.append("\n")
            }
            sb.toString().trim()
        }
    }

    val generatedTomorrowForecast = remember(reports) {
        val latestPlanReport = reports.firstOrNull { it.tomorrowPlan.trim().isNotEmpty() }
        if (latestPlanReport != null) {
            "🔮 برنامه فردا انتقال‌یافته از گزارش قبلی (${latestPlanReport.date}):\n${latestPlanReport.tomorrowPlan}"
        } else {
            ""
        }
    }

    val displayRecent = recentActivities.ifEmpty { generatedRecentActivities }
    val displayForecast = tomorrowForecast.ifEmpty { generatedTomorrowForecast }

    var showEditSummaryDialog by remember { mutableStateOf(false) }
    var tempSummaryText by remember { mutableStateOf("") }
    var summaryEditType by remember { mutableStateOf("RECENT") } // "RECENT" or "FORECAST"

    if (showEditSummaryDialog) {
        AlertDialog(
            onDismissRequest = { showEditSummaryDialog = false },
            title = {
                Text(
                    text = if (summaryEditType == "RECENT") "ثبت خلاصه فعالیت‌های کارگاه 📝" else "ثبت پیش‌بینی و یادآوری فردا 🔮",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val smartVal = if (summaryEditType == "RECENT") generatedRecentActivities else generatedTomorrowForecast
                    if (smartVal.isNotEmpty()) {
                        Button(
                            onClick = { tempSummaryText = smartVal },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "درج", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("درج پیشنهاد خودکار از ۲ روز قبل 🪄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = tempSummaryText,
                        onValueChange = { tempSummaryText = it },
                        placeholder = {
                            Text(
                                text = if (summaryEditType == "RECENT") "خلاصه عملیات خاکی، بتن‌ریزی یا پیشرفت روزهای اخیر..."
                                       else "پیاده‌سازی نقاط Km20، بررسی توافق‌نامه با معارضین جاده، کارهای اجرایی..."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.None
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (summaryEditType == "RECENT") {
                            recentActivities = tempSummaryText
                            viewModel.sharedPreferences.edit().putString("dashboard_recent_activities", tempSummaryText).apply()
                        } else {
                            tomorrowForecast = tempSummaryText
                            viewModel.sharedPreferences.edit().putString("dashboard_tomorrow_forecast", tempSummaryText).apply()
                        }
                        showEditSummaryDialog = false
                    }
                ) {
                    Text("ذخیره و ثبت")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSummaryDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "داشبورد مدیریت پروژه کارگاهی",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardKpiCard(
                title = "کل گزارش‌های ثبت‌شده کارگاه",
                value = "$reportsCount مورد ثبت‌شده",
                icon = Icons.Default.List,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardKpiCard(
                title = "میانگین پرسنل فعال روزانه",
                value = personnelVal,
                icon = Icons.Default.Person,
                modifier = Modifier.weight(1f)
            )
            DashboardKpiCard(
                title = "ماشین‌آلات صنعتی فعال",
                value = machineryVal,
                icon = Icons.Default.Settings,
                modifier = Modifier.weight(1f)
            )
        }

        // CARD: Noticeboard, Work Summary & Prediction Forecast
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "اطلاعات و یادآوری‌ها",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "خلاصه وضعیت کارگاه و یادآوری اکیپ‌ها 📌",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 1: Summary / Latest Activities
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                        Text(
                            text = "خلاصه فعالیت‌های اخیر:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (recentActivities.isEmpty() && generatedRecentActivities.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "پیشنهاد هوشمند گزارش‌های قبلی ✨",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                tempSummaryText = recentActivities.ifEmpty { generatedRecentActivities }
                                summaryEditType = "RECENT"
                                showEditSummaryDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("ثبت/ویرایش", fontSize = 10.sp)
                            }
                        }
                    }
                    Text(
                        text = displayRecent.ifEmpty { "هنوز هیچ خلاصه فعالیتی برای کارگاه ثبت نگردیده است. با فشردن دکمه ویرایش می‌توانید آن را بنویسید ✍️" },
                        fontSize = 11.sp,
                        color = if (displayRecent.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Item 2: Forecast of tomorrow's activities / reminder
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFF59E0B), CircleShape)
                        )
                        Text(
                            text = "پیش‌بینی فعالیت‌ها و یادآوری فردا:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (tomorrowForecast.isEmpty() && generatedTomorrowForecast.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "پیش‌بینی خودکار گزارش قبلی 🔮",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                tempSummaryText = tomorrowForecast.ifEmpty { generatedTomorrowForecast }
                                summaryEditType = "FORECAST"
                                showEditSummaryDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("ثبت/ویرایش", fontSize = 10.sp)
                            }
                        }
                    }
                    Text(
                        text = displayForecast.ifEmpty { "هنوز هیچ پیش‌بینی فعالیتی برای روز آینده ثبت نگردیده است. با فشردن دکمه ویرایش می‌توانید آن را بنویسید ✍️" },
                        fontSize = 11.sp,
                        color = if (displayForecast.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardKpiCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun ProjectSettingsTab(
    viewModel: ReportViewModel,
    importLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val context = LocalContext.current
    var defaultProject by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_project", "") ?: "") }
    var defaultSection by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_section", "") ?: "") }
    var defaultPreparedBy by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_prepared_by", "") ?: "") }
    var customUnitTitle by remember { mutableStateOf(viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها") }
    var defaultWeather by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_weather", "آفتابی ☀️") ?: "آفتابی ☀️") }
    var defaultReportType by remember { mutableStateOf(viewModel.sharedPreferences.getString("default_report_type", "ASK") ?: "ASK") }
    var showWeatherPickerForSettings by remember { mutableStateOf(false) }

    var userSignatureBase64 by remember { mutableStateOf(viewModel.sharedPreferences.getString("user_signature", "") ?: "") }
    var showSignaturePadDialog by remember { mutableStateOf(false) }

    var dailyReminderEnabled by remember { mutableStateOf(viewModel.sharedPreferences.getBoolean("daily_reminder_enabled", true)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "مجوز اعلان تایید شد و یادآور روزانه ساعت ۲۰ فعال گردید ✅", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "توجه: برای دریافت هشدارها باید دسترسی اعلان فعال باشد ⚠️", Toast.LENGTH_LONG).show()
            dailyReminderEnabled = false
        }
    }

    val signatureImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val base64 = uriToBase64(context, uri)
            if (base64 != null) {
                userSignatureBase64 = base64
                viewModel.sharedPreferences.edit().putString("user_signature", base64).apply()
                Toast.makeText(context, "تصویر امضا با موفقیت بارگذاری و ذخیره شد ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "خطا در پردازش تصویر امضا", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var defaultAppTheme by remember { mutableStateOf(viewModel.sharedPreferences.getString("app_theme", "LIGHT") ?: "LIGHT") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab Title
        Text(
            text = "تنظیمات عمومی سامانه",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = "تنظیمات پیش‌فرض برای هوشمندسازی و سرعت‌بخشی به ثبت گزارش‌های کارگاه ساختمانی شما.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        // CARD 1: Basic Information Defaults
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "مشخصات پایه کارگاه",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = defaultProject,
                    onValueChange = { defaultProject = it },
                    label = { Text("نام پروژه پیش‌فرض") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                OutlinedTextField(
                    value = defaultSection,
                    onValueChange = { defaultSection = it },
                    label = { Text("بخش/واحد کارگاهی پیش‌فرض") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                OutlinedTextField(
                    value = defaultPreparedBy,
                    onValueChange = { defaultPreparedBy = it },
                    label = { Text("نام تنظیم‌کننده پیش‌فرض") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                OutlinedTextField(
                    value = customUnitTitle,
                    onValueChange = { customUnitTitle = it },
                    label = { Text("عنوان سفارشی سایر واحدها (گزارش نوع پنجم)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }

        // CARD 2: Theme Selection Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "تم کاربری و ظاهر نرم‌افزار",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Triple("LIGHT", "روشن ☀️", Color(0xFFF59E0B)),
                        Triple("DARK", "تیره 🌙", Color(0xFF3B82F6)),
                        Triple("SYSTEM", "سیستم ⚙️", Color(0xFF10B981))
                    ).forEach { (themeKey, label, activeClr) ->
                        val isSelected = defaultAppTheme == themeKey
                        Button(
                            onClick = { defaultAppTheme = themeKey },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CARD 3: Custom Default Configurations (Categorization & Weather)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "دسته‌بندی و گزارش‌نویسی پیش‌فرض",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: ASK, EXECUTION, WAREHOUSE
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple("ASK", "همیشه بپرس", Icons.Default.Help),
                            Triple("EXECUTION", "اجرا", Icons.Default.Engineering),
                            Triple("WAREHOUSE", "انبارداری", Icons.Default.Warehouse)
                        ).forEach { (typeKey, typeLabel, typeIcon) ->
                            val isSelected = defaultReportType == typeKey
                            Button(
                                onClick = { defaultReportType = typeKey },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(imageVector = typeIcon, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Row 2: LEGAL, SURVEY, TECHNICAL
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple("LEGAL", "امور حقوقی", Icons.Default.Gavel),
                            Triple("SURVEY", "نقشه‌برداری", Icons.Default.Map),
                            Triple("TECHNICAL", "دفتر فنی", Icons.Default.Description)
                        ).forEach { (typeKey, typeLabel, typeIcon) ->
                            val isSelected = defaultReportType == typeKey
                            Button(
                                onClick = { defaultReportType = typeKey },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(imageVector = typeIcon, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Row 3: HSE, CUSTOM
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple("HSE", "ایمنی HSE", Icons.Default.Warning),
                            Triple("CUSTOM", customUnitTitle, Icons.Default.Construction)
                        ).forEach { (typeKey, typeLabel, typeIcon) ->
                            val isSelected = defaultReportType == typeKey
                            Button(
                                onClick = { defaultReportType = typeKey },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(imageVector = typeIcon, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWeatherPickerForSettings = true }
                ) {
                    OutlinedTextField(
                        value = defaultWeather,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("آب و هوای پیش‌فرض") },
                        trailingIcon = {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "انتخاب")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                            .clickable { showWeatherPickerForSettings = true }
                    )
                }
            }
        }

        // CARD 4: Electronic Signature Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "امضای الکترونیکی تنظیم‌کننده گزارش",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                val sigBitmap = remember(userSignatureBase64) {
                    if (userSignatureBase64.isNotEmpty()) base64ToBitmap(userSignatureBase64) else null
                }
                
                if (sigBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = sigBitmap.asImageBitmap(),
                            contentDescription = "پیش‌نمایش امضا",
                            modifier = Modifier.fillMaxHeight().padding(8.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "امضا ثبت نشده است (فیلد امضا در PDF خالی خواهد ماند)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSignaturePadDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Create, contentDescription = "ترسیم", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ترسیم امضا", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { signatureImageLauncher.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "بارگذاری", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("آپلود عکس", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    if (userSignatureBase64.isNotEmpty()) {
                        Button(
                            onClick = {
                                userSignatureBase64 = ""
                                viewModel.sharedPreferences.edit().remove("user_signature").apply()
                                Toast.makeText(context, "امضا با موفقیت حذف شد 🗑️", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(0.8f).heightIn(min = 40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CARD 5: Backup and Restore Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "پشتیبان‌گیری و بازیابی اطلاعات",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button 1: Export (پشتیبان‌گیری)
                    Button(
                        onClick = {
                            val backupJson = viewModel.exportBackup()
                            try {
                                val backupFile = java.io.File(context.cacheDir, "daily_report_backup.json")
                                backupFile.writeText(backupJson)
                                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "com.aistudio.civilsync.fileprovider",
                                    backupFile
                                )
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_TITLE, "پشتیبان داده‌های گزارش‌یار")
                                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "ارسال فایل پشتیبان (بکاپ JSON)"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در ارسال: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "پشتیبان‌گیری", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ارسال بکاپ (خروجی)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Button 2: Import (بازیابی)
                    Button(
                        onClick = {
                            try {
                                importLauncher.launch("*/*")
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در انتخاب اطلاعات پشتیبان: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "بازیابی", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بازیابی بکاپ (ورود)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Primary Save Button
        Button(
            onClick = {
                viewModel.saveDefaultConfig(
                    defaultProject,
                    defaultSection,
                    defaultPreparedBy,
                    defaultReportType,
                    defaultWeather,
                    "", 
                    ""  
                )
                viewModel.sharedPreferences.edit().putString("custom_unit_title", customUnitTitle).apply()
                viewModel.sharedPreferences.edit().putString("app_theme", defaultAppTheme).apply()
                viewModel.sharedPreferences.edit().putBoolean("daily_reminder_enabled", dailyReminderEnabled).apply()
                if (dailyReminderEnabled) {
                    com.example.receiver.DailyReminderReceiver.scheduleDailyReminder(context)
                } else {
                    com.example.receiver.DailyReminderReceiver.cancelDailyReminder(context)
                }
                Toast.makeText(context, "تنظیمات پیش‌فرض با موفقیت ذخیره شد ✅", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(imageVector = Icons.Default.Done, contentDescription = "ذخیره تغییرات", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ذخیره نهایی تنظیمات", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Weather Picker Dialog
    if (showWeatherPickerForSettings) {
        AlertDialog(
            onDismissRequest = { showWeatherPickerForSettings = false },
            title = {
                Text(
                    text = "انتخاب وضعیت جوی پیش‌فرض",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        "آفتابی ☀️",
                        "نیمه ابری ⛅",
                        "ابری کامل ☁️",
                        "سوزناک و گرم 🥵",
                        "باد شدید 💨",
                        "باران ملایم 🌧️",
                        "باران شدید و سیل‌آسا ⛈️",
                        "برفی و کولاک ❄️",
                        "یخبندان شدید 🥶",
                        "گرد و غبار شدید 😷"
                    ).forEach { weatherText ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    defaultWeather = weatherText
                                    showWeatherPickerForSettings = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = weatherText,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherPickerForSettings = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Signature Pad Dialog (Inline popup)
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
                            .height(200.dp)
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
                                    onDrag = { change, dragAmount ->
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
                            // Draw previously finished lines
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
                            // Draw active currently drawn line
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

@Composable
fun AboutAppTab(viewModel: ReportViewModel) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        
        // Compact single construction/engineering brand logo icon
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Engineering,
                    contentDescription = "آیکون مهندسی کارگاه",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        // Combined App Name and Version Badge row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "سامانه گزارش یار کارگاه",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Surface(
                color = Color(0xFFFEF3C7), // Amber background badge
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
            ) {
                Text(
                    text = "نسخه ۳.۰.۴",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        
        // Compact Builder Name Badge
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                text = "سازنده برنامه: مصطفی عرفانی",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        
        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 2.dp))
        
        Text(
            text = "برنامه گزارش یار یک ابزار هوشمند، مهندسی و جامع برای ثبت، بایگانی و گزارش‌نویسی روزانه و مستندسازی کارگاه‌های عمرانی، راهسازی، ساختمانی و انبارداری است. با کمک این سیستم می‌توانید گزارش‌های خود را ثبت کرده و خروجی‌های PDF دقیق و استاندارد تولید کنید.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Justify,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
        
        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 2.dp))
        
        // Action buttons
        
        // Button 1: Send Feedback / Contact Builder via Email
        Button(
            onClick = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("MOSTAFA5804@GMAIL.COM"))
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "بازخورد و ارتباط با سازنده سامانه گزارش یار")
                        putExtra(android.content.Intent.EXTRA_TEXT, "با سلام و احترام،\nبازخورد من درباره برنامه سامانه گزارش یار کارگاه نسخه ۳.۰.۴:\n\n")
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "ارسال ایمیل به سازنده"))
                } catch (e: Exception) {
                    Toast.makeText(context, "سیستم ارسال ایمیل پیش‌فرض یافت نشد", Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(imageVector = Icons.Default.Email, contentDescription = "ارسال ایمیل", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("ارتباط با سازنده برنامه (ارسال ایمیل)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        
        // Button 2: Export backup directly to the builder's email
        Button(
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
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "پشتیبان داده‌های سامانه گزارش یار - نسخه ۳.۰.۴")
                        putExtra(android.content.Intent.EXTRA_TEXT, "با سلام،\nپشتیبان داده‌های کارگاه ساختمانی من پیوست شده است:")
                        putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "ارسال پشتیبان اطلاعات"))
                } catch (e: Exception) {
                    Toast.makeText(context, "سیستم ارسال ایمیل پیش‌فرض یافت نشد", Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = "ارسال بکاپ", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("ارسال مستقیم فایل پشتیبانی (بکاپ JSON)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}
