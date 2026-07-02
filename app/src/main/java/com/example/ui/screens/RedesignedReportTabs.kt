package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.viewmodel.ReportViewModel
import android.content.Intent
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// --- CUSTOM PERSISTENCE PARSER HELPERS ---

data class ParsedWeather(
    val condition: String = "آفتابی ☀️",
    val temperature: String = "۲۸°C",
    val shift: String = "صبح",
    val workStatus: String = "ON_TRACK"
) {
    fun serialize() = "$condition | $temperature | $shift | $workStatus"
    
    companion object {
        fun deserialize(raw: String): ParsedWeather {
            if (raw.isEmpty()) return ParsedWeather()
            val parts = raw.split(" | ")
            if (parts.size >= 4) {
                return ParsedWeather(parts[0], parts[1], parts[2], parts[3])
            }
            return ParsedWeather(condition = raw)
        }
    }
}

@Composable
fun PersianDatePickerDialog(
    initialDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = initialDate.split("/")
    var selectedYear by remember { mutableStateOf(if (parts.size == 3) parts[0].toIntOrNull() ?: 1403 else 1403) }
    var selectedMonth by remember { mutableStateOf(if (parts.size == 3) parts[1].toIntOrNull() ?: 1 else 1) }
    var selectedDay by remember { mutableStateOf(if (parts.size == 3) parts[2].toIntOrNull() ?: 1 else 1) }

    var isMonthSelectionMode by remember { mutableStateOf(false) }

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    fun isLeapYear(year: Int): Boolean {
        return (year == 1403 || year == 1407 || year == 1411 || year == 1399 || (year - 1399) % 4 == 0)
    }

    fun getStartDayOfWeek(year: Int, month: Int): Int {
        var days = 0
        for (y in 1400 until year) {
            days += if (isLeapYear(y)) 366 else 365
        }
        for (y in year until 1400) {
            days -= if (isLeapYear(y)) 366 else 365
        }
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        for (m in 1 until month) {
            days += jDaysInMonth[m - 1]
        }
        var dow = (1 + days) % 7
        if (dow < 0) dow += 7
        return dow
    }

    val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, if (isLeapYear(selectedYear)) 30 else 29)
    val daysInCurrentMonth = jDaysInMonth[selectedMonth - 1]
    val startDay = getStartDayOfWeek(selectedYear, selectedMonth)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header: Year Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear++ }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Year")
                    }
                    Text(
                        text = selectedYear.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    IconButton(onClick = { selectedYear-- }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Year")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Month Selector Button
                TextButton(
                    onClick = { isMonthSelectionMode = !isMonthSelectionMode },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = monthNames[selectedMonth - 1],
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (isMonthSelectionMode) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isMonthSelectionMode) {
                    // Month Grid 4x3
                    val rows = 4
                    val cols = 3
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (r in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                for (c in 0 until cols) {
                                    val mIndex = r * cols + c
                                    val isSelected = (mIndex + 1 == selectedMonth)
                                    TextButton(
                                        onClick = { 
                                            selectedMonth = mIndex + 1
                                            isMonthSelectionMode = false 
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text(monthNames[mIndex])
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Day Grid
                    val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        weekDays.forEach { dayName ->
                            Text(
                                text = dayName,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    var dayCounter = 1
                    val totalRows = (startDay + daysInCurrentMonth + 6) / 7
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (r in 0 until totalRows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                for (c in 0 until 7) {
                                    if (r == 0 && c < startDay || dayCounter > daysInCurrentMonth) {
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val day = dayCounter
                                        val isSelected = (day == selectedDay)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                .clickable { selectedDay = day },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = day.toString(),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        dayCounter++
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { 
                        val mStr = selectedMonth.toString().padStart(2, '0')
                        val dStr = selectedDay.toString().padStart(2, '0')
                        onDateSelected("$selectedYear/$mStr/$dStr") 
                    }) {
                        Text("تایید")
                    }
                }
            }
        }
    }
}

// --- TAB 1: BASE INFORMATION ---
@Composable
fun BaseInfoTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var showWeatherPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val customUnitTitle = remember { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
    val unitLabel = when (report.reportType) {
        "WAREHOUSE" -> "انبار"
        "LEGAL" -> "حقوقی"
        "SURVEY" -> "نقشه‌برداری"
        "TECHNICAL" -> "فنی"
        "HSE" -> "ایمنی"
        "CUSTOM" -> customUnitTitle
        else -> "اجرا"
    }

    if (showDatePicker) {
        PersianDatePickerDialog(
            initialDate = report.date,
            onDateSelected = { selectedDate ->
                viewModel.updateCurrentReport { reportCopy -> reportCopy.copy(date = selectedDate) }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Info Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "مشخصات پایه گزارش روزانه 📋",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF00695C)
                )

                Text(
                    text = "بخش جاری: $unitLabel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 1. Report Date
                OutlinedTextField(
                    value = report.date,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("تاریخ گزارش") },
                    trailingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "انتخاب تاریخ",
                            modifier = Modifier.clickable { showDatePicker = true }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                // 2. Weather Row Selection
                Button(
                    onClick = { showWeatherPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0F2F1),
                        contentColor = Color(0xFF00695C)
                    )
                ) {
                    Icon(Icons.Default.WbSunny, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("آب و هوا: ${report.weather}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Premium Continue Button
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
        ) {
            Text("ادامه به بخش فعالیت‌ها ➡️", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    if (showWeatherPicker) {
        val options = listOf("آفتابی ☀️", "ابری ☁️", "نیمه ابری ⛅", "بارانی 🌧️", "برفی ❄️", "طوفانی 💨", "غبارآلود 🌫️")
        AlertDialog(
            onDismissRequest = { showWeatherPicker = false },
            title = { Text("انتخاب وضعیت جوی کارگاه", fontWeight = FontWeight.Bold) },
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
                            Text(opt, fontWeight = FontWeight.Bold, color = Color(0xFF00695C), fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherPicker = false }) {
                    Text("انصراف", color = Color.Gray)
                }
            }
        )
    }
}

// --- TAB 2: TECHNICAL ACTIVITIES ---
@Composable
fun TasksTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport
) {
    var description by remember { mutableStateOf("") }
    var startKm by remember { mutableStateOf("") }
    var endKm by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    
    var editingIndex by remember { mutableStateOf(-1) }

    val context = LocalContext.current

    val isDetailed = report.reportType == "EXECUTION" || report.reportType == "LEGAL" || report.reportType == "SURVEY" || report.reportType == "WAREHOUSE"

    val formatKm = { input: String ->
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) "" else {
            val num = digits.toLong()
            val km = num / 1000
            val m = num % 1000
            "$km+${String.format("%03d", m)}"
        }
    }

    val autoCalculateQuantity = { sKm: String, eKm: String ->
        val sDigits = sKm.filter { it.isDigit() }
        val eDigits = eKm.filter { it.isDigit() }
        if (sDigits.isNotEmpty() && eDigits.isNotEmpty()) {
            val sVal = sDigits.toLongOrNull()
            val eVal = eDigits.toLongOrNull()
            if (sVal != null && eVal != null) {
                quantity = kotlin.math.abs(eVal - sVal).toString()
            }
        }
    }

    val customUnitTitle = remember { viewModel.sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها" }
    val unitLabel = when (report.reportType) {
        "WAREHOUSE" -> "انبار"
        "LEGAL" -> "تحصیل اراضی"
        "SURVEY" -> "نقشه‌برداری"
        "TECHNICAL" -> "دفتر فنی"
        "HSE" -> "ایمنی HSE"
        "CUSTOM" -> customUnitTitle
        else -> "اجرایی"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Container Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val title = if (report.reportType == "WAREHOUSE") "ثبت مصالح ورودی به انبار 📥" else "ثبت شرح فعالیت‌های واحد $unitLabel 📝"
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Description multiline
                val descLabel = if (report.reportType == "WAREHOUSE") "نوع مصالح" else "شرح کامل فعالیت"
                val descPlaceholder = if (report.reportType == "WAREHOUSE") "مثلا سیمان فله" else "مثلا بتن‌ریزی شفت‌ها..."
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(descLabel, fontSize = 12.sp) },
                    placeholder = { Text(descPlaceholder, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 2
                )

                if (isDetailed) {
                    // 2. Start and End Kilometers (Not for warehouse)
                    if (report.reportType != "WAREHOUSE") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            var startKmFocused by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = startKm,
                                onValueChange = { newValue -> 
                                    startKm = newValue.filter { it.isDigit() || it == '+' }
                                    autoCalculateQuantity(newValue, endKm)
                                },
                                label = { Text("کیلومتر ابتدا", fontSize = 12.sp) },
                                placeholder = { Text("مثلا 12500", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused && startKmFocused) {
                                            val formatted = formatKm(startKm)
                                            if (formatted.isNotEmpty()) {
                                                startKm = formatted
                                                autoCalculateQuantity(formatted, endKm)
                                            }
                                        }
                                        startKmFocused = focusState.isFocused
                                    },
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )

                            var endKmFocused by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = endKm,
                                onValueChange = { newValue -> 
                                    endKm = newValue.filter { it.isDigit() || it == '+' }
                                    autoCalculateQuantity(startKm, newValue)
                                },
                                label = { Text("کیلومتر انتها", fontSize = 12.sp) },
                                placeholder = { Text("مثلا 13200", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused && endKmFocused) {
                                            val formatted = formatKm(endKm)
                                            if (formatted.isNotEmpty()) {
                                                endKm = formatted
                                                autoCalculateQuantity(startKm, formatted)
                                            }
                                        }
                                        endKmFocused = focusState.isFocused
                                    },
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }

                    // 3. Location / Zone
                    val locationLabel = if (report.reportType == "WAREHOUSE") "محل تامین" else "محدوده / محل فعالیت"
                    val locationPlaceholder = if (report.reportType == "WAREHOUSE") "مثلا کارخانه سیمان صوفیان" else "مثلا باند چپ"
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(locationLabel, fontSize = 12.sp) },
                        placeholder = { Text(locationPlaceholder, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    // 4. Quantity & Unit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("مقدار", fontSize = 12.sp) },
                            placeholder = { Text("مثلا ۴۵۰", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("واحد", fontSize = 12.sp) },
                            placeholder = { Text("مثلا تن", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                    }
                }

                // 5. Comments / Notes
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("توضیحات تکمیلی", fontSize = 12.sp) },
                    placeholder = { Text("ملاحظات...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 2
                )

                // Large Orange Submit Button
                Button(
                    onClick = {
                        if (description.isBlank()) {
                            val msg = if (report.reportType == "WAREHOUSE") "لطفا نوع مصالح را وارد کنید" else "لطفا شرح فعالیت را وارد کنید"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val entry = TaskEntry(
                            description = description,
                            startKm = if (isDetailed && report.reportType != "WAREHOUSE") startKm else "",
                            endKm = if (isDetailed && report.reportType != "WAREHOUSE") endKm else "",
                            location = if (isDetailed) location else "",
                            quantity = if (isDetailed) quantity else "",
                            unit = if (isDetailed) unit else "",
                            comments = comments
                        )
                        
                        if (editingIndex >= 0) {
                            viewModel.updateCurrentReport { r -> 
                                val newList = r.tasks.toMutableList()
                                newList[editingIndex] = entry
                                r.copy(tasks = newList)
                            }
                            editingIndex = -1
                            Toast.makeText(context, "ویرایش با موفقیت انجام شد", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateCurrentReport { r -> r.copy(tasks = r.tasks + entry) }
                            Toast.makeText(context, "با موفقیت درج شد", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Clear fields
                        description = ""
                        startKm = ""
                        endKm = ""
                        location = ""
                        quantity = ""
                        unit = ""
                        comments = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (editingIndex >= 0) Color(0xFF2563EB) else Color(0xFFD97706))
                ) {
                    Icon(if (editingIndex >= 0) Icons.Default.Edit else Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (editingIndex >= 0) "ثبت ویرایش" else "درج در جدول +", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Added List Header
        if (report.tasks.isNotEmpty()) {
            Text(
                text = "جدول فعالیت‌های کارگاهی ثبت‌شده 📋",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            report.tasks.forEachIndexed { index, item ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "فعالیت شماره ${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                fontSize = 12.sp
                            )
                            Row {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.tasks.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index - 1]
                                            updated[index - 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(tasks = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, "بالا", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                if (index < report.tasks.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.tasks.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index + 1]
                                            updated[index + 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(tasks = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, "پایین", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        description = item.description
                                        startKm = item.startKm
                                        endKm = item.endKm
                                        location = item.location
                                        quantity = item.quantity
                                        unit = item.unit
                                        comments = item.comments
                                        editingIndex = index
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "ویرایش", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val updated = report.tasks.filterIndexed { i, _ -> i != index }
                                        viewModel.updateCurrentReport { r -> r.copy(tasks = updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        var expanded by remember(item.description) { mutableStateOf(false) }
                        Text(
                            text = item.description,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )

                        if (item.startKm.isNotEmpty() || item.endKm.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Navigation, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("کیلومتراژ: از ${item.startKm} الی ${item.endKm}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (item.location.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("محل/محدوده: ${item.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (item.quantity.isNotEmpty() || item.unit.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Scale, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("مقدار: ${item.quantity} ${item.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (item.comments.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Comment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("توضیحات: ${item.comments}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 3: MATERIALS INVENTORY ---
@Composable
fun MaterialsTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport
) {
    var type by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var unloadingLocation by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Container Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val title = if (report.reportType == "WAREHOUSE") "ثبت مصالح خروجی انبار 📦" else "ثبت آمار مصالح تخصصی وارده به کارگاه 📦"
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Material Type/Name with List icon
                val typeLabel = if (report.reportType == "WAREHOUSE") "نوع مصالح" else "نوع مصالح تخصصی وارده"
                val typePlaceholder = if (report.reportType == "WAREHOUSE") "مثلا سیمان پرتلند" else "مثلا سیمان پرتلند تیپ ۵..."
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(typeLabel, fontSize = 12.sp) },
                    placeholder = { Text(typePlaceholder, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.List, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // 2. Quantity & Unit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("مقدار کالا", fontSize = 12.sp) },
                        placeholder = { Text("مثلا ۲۵۰", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("واحد اندازه‌گیری", fontSize = 12.sp) },
                        placeholder = { Text("مثلا کیسه", fontSize = 11.sp) },
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }

                // 3. Storage / Unloading Location
                val locLabel = if (report.reportType == "WAREHOUSE") "تحویل گیرنده" else "محل استقرار یا مخزن"
                val locPlaceholder = if (report.reportType == "WAREHOUSE") "مثلا پیمانکار آرماتوربند" else "مثلا انبار سرپوشیده شماره ۲"
                OutlinedTextField(
                    value = unloadingLocation,
                    onValueChange = { unloadingLocation = it },
                    label = { Text(locLabel, fontSize = 12.sp) },
                    placeholder = { Text(locPlaceholder, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // 4. Document / Lab Reference No. (using Comments)
                val commentLabel = if (report.reportType == "WAREHOUSE") "اطلاعات حواله خروج" else "اطلاعات بارنامه / تاییدیه"
                val commentPlaceholder = if (report.reportType == "WAREHOUSE") "مثلا حواله شماره ۱۲۳۴" else "مثلا بارنامه شماره ۸۷۳۲۶۱"
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text(commentLabel, fontSize = 12.sp) },
                    placeholder = { Text(commentPlaceholder, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // Large Teal Submit Button
                Button(
                    onClick = {
                        if (type.isBlank()) {
                            Toast.makeText(context, "لطفا نوع مصالح را وارد کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val entry = MaterialEntry(
                            type = type,
                            quantity = quantity,
                            unit = unit,
                            unloadingLocation = unloadingLocation,
                            comments = comments
                        )
                        
                        if (editingIndex >= 0) {
                            viewModel.updateCurrentReport { r -> 
                                val newList = r.materials.toMutableList()
                                newList[editingIndex] = entry
                                r.copy(materials = newList)
                            }
                            editingIndex = -1
                            Toast.makeText(context, "ویرایش با موفقیت انجام شد", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateCurrentReport { r -> r.copy(materials = r.materials + entry) }
                            Toast.makeText(context, "با موفقیت درج شد", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Clear fields
                        type = ""
                        quantity = ""
                        unit = ""
                        unloadingLocation = ""
                        comments = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (editingIndex >= 0) Color(0xFF2563EB) else Color(0xFF0D9488)) // Teal/Cyan color
                ) {
                    Icon(if (editingIndex >= 0) Icons.Default.Edit else Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val btnText = if (editingIndex >= 0) "ثبت ویرایش" else (if (report.reportType == "WAREHOUSE") "درج مصالح خروجی +" else "درج در جدول ورودی‌های انبار +")
                    Text(btnText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Added List Header
        if (report.materials.isNotEmpty()) {
            Text(
                text = "جدول مصالح ثبت‌شده امروز 🚚",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            report.materials.forEachIndexed { index, item ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "محموله شماره ${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D9488),
                                fontSize = 12.sp
                            )
                            Row {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.materials.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index - 1]
                                            updated[index - 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(materials = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, "بالا", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                if (index < report.materials.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.materials.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index + 1]
                                            updated[index + 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(materials = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, "پایین", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        type = item.type
                                        quantity = item.quantity
                                        unit = item.unit
                                        unloadingLocation = item.unloadingLocation
                                        comments = item.comments
                                        editingIndex = index
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "ویرایش", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val updated = report.materials.filterIndexed { i, _ -> i != index }
                                        viewModel.updateCurrentReport { r -> r.copy(materials = updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        var expanded by remember(item.type) { mutableStateOf(false) }
                        Text(
                            text = item.type,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Inventory, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Text("مقدار کالا: ${item.quantity} ${item.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (item.unloadingLocation.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.HomeWork, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                val locLabel = if (report.reportType == "WAREHOUSE") "تحویل گیرنده" else "محل تخلیه"
                                Text("$locLabel: ${item.unloadingLocation}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (item.comments.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                val docLabel = if (report.reportType == "WAREHOUSE") "اطلاعات حواله خروج" else "سند/بارنامه"
                                Text("$docLabel: ${item.comments}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 4: MACHINERY MANAGEMENT ---
@Composable
fun MachineryTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport
) {
    var type by remember { mutableStateOf("") }
    var activeCount by remember { mutableIntStateOf(1) }
    var inactiveCount by remember { mutableIntStateOf(0) }
    var ownershipType by remember { mutableStateOf("COMPANY") } // "COMPANY" or "RENTAL" or "CONTRACTOR"
    var workingHours by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Container Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val title = if (report.reportType == "SURVEY") "ثبت تجهیزات نقشه‌برداری 📐" else "ثبت اطلاعات ماشین‌آلات و تجهیزات کارگاه 🚜"
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Machine name selector
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(if (report.reportType == "SURVEY") "نام تجهیزات" else "نام و مدل ماشین‌آلات") },
                    placeholder = { Text(if (report.reportType == "SURVEY") "مثلا توتال استیشن، جی‌پی‌اس و..." else "مثلا لودر کوماتسو ۶۰۰، جرثقیل ۲۰ تن یا...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // 2. Steppers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Active Stepper
                    Column(
                        modifier = Modifier
                            .weight(1.0f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("تعداد فعال", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(
                                onClick = { if (activeCount > 0) activeCount-- },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF16A34A).copy(alpha = 0.1f), contentColor = Color(0xFF16A34A))
                            ) {
                                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                            }
                            Text(activeCount.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            FilledIconButton(
                                onClick = { activeCount++ },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF16A34A).copy(alpha = 0.1f), contentColor = Color(0xFF16A34A))
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Inactive Stepper
                    Column(
                        modifier = Modifier
                            .weight(1.0f)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("خراب/متوقف", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(
                                onClick = { if (inactiveCount > 0) inactiveCount-- },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.1f), contentColor = Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                            }
                            Text(inactiveCount.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            FilledIconButton(
                                onClick = { inactiveCount++ },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.1f), contentColor = Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // 3. Ownership segmented chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نوع مالکیت ماشین‌آلات:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("COMPANY" to "شرکتی", "RENTAL" to "استیجاری", "CONTRACTOR" to "پیمانکار").forEach { (typeKey, typeVal) ->
                            val isSelected = ownershipType == typeKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { ownershipType = typeKey },
                                label = { Text(typeVal, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 4. Hours & Comments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = workingHours,
                        onValueChange = { workingHours = it },
                        label = { Text("ساعت کارکرد") },
                        placeholder = { Text("مثلا ۸") },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = comments,
                        onValueChange = { comments = it },
                        label = { Text("توضیحات") },
                        placeholder = { Text("توضیح مبرهن...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }

                // Large Teal/Green Submit Button
                Button(
                    onClick = {
                        if (type.isBlank()) {
                            Toast.makeText(context, "لطفا نام ماشین‌آلات را وارد کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val entry = MachineryEntry(
                            type = type,
                            activeCount = activeCount,
                            inactiveCount = inactiveCount,
                            ownershipType = ownershipType,
                            workingHours = workingHours,
                            comments = comments
                        )
                        if (editingIndex >= 0) {
                            viewModel.updateCurrentReport { r -> 
                                val newList = r.machinery.toMutableList()
                                newList[editingIndex] = entry
                                r.copy(machinery = newList)
                            }
                            editingIndex = -1
                            Toast.makeText(context, "ویرایش با موفقیت انجام شد", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateCurrentReport { r -> r.copy(machinery = r.machinery + entry) }
                            Toast.makeText(context, "دستگاه با موفقیت درج شد", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Clear fields
                        type = ""
                        activeCount = 1
                        inactiveCount = 0
                        workingHours = ""
                        comments = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (editingIndex >= 0) Color(0xFF2563EB) else Color(0xFF0F766E)) // Dark Teal/Green color
                ) {
                    Icon(if (editingIndex >= 0) Icons.Default.Edit else Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (editingIndex >= 0) "ثبت ویرایش" else "درج در جدول ماشین‌آلات کارگاه +", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Added List Header
        if (report.machinery.isNotEmpty()) {
            Text(
                text = "جدول ماشین‌آلات ثبت‌شده امروز ⚙️",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            report.machinery.forEachIndexed { index, item ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تجهیزات شماره ${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E),
                                fontSize = 12.sp
                            )
                            Row {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.machinery.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index - 1]
                                            updated[index - 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(machinery = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, "بالا", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                if (index < report.machinery.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.machinery.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index + 1]
                                            updated[index + 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(machinery = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, "پایین", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        type = item.type
                                        activeCount = item.activeCount
                                        inactiveCount = item.inactiveCount
                                        ownershipType = item.ownershipType
                                        workingHours = item.workingHours
                                        comments = item.comments
                                        editingIndex = index
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "ویرایش", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val updated = report.machinery.filterIndexed { i, _ -> i != index }
                                        viewModel.updateCurrentReport { r -> r.copy(machinery = updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        var expanded by remember(item.type) { mutableStateOf(false) }
                        Text(
                            text = item.type,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("فعال: ${item.activeCount} | خراب: ${item.inactiveCount}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            
                            val ownershipLabel = when (item.ownershipType) {
                                "COMPANY" -> "شرکتی"
                                "RENTAL" -> "استیجاری"
                                else -> "پیمانکار"
                            }
                            Text("مالکیت: $ownershipLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }

                        if (item.workingHours.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("ساعت کارکرد: ${item.workingHours} ساعت", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (item.comments.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Comment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("توضیحات: ${item.comments}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 5: PERSONNEL INVENTORY ---
@Composable
fun ManpowerTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(1) }
    var employmentType by remember { mutableStateOf("COMPANY") } // "COMPANY" or "SUBCONTRACTOR"
    var comments by remember { mutableStateOf("") }
    var isOnLeave by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Container Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "ثبت اطلاعات نیروی انسانی شاغل کارگاه 👷",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Employee Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام و نام خانوادگی") },
                    placeholder = { Text("مثلا علی علوی یا اکیپ جوشکاران") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // 2. Role
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("سمت") },
                    placeholder = { Text("مثلا سرپرست کارگاه، تکنسین، راننده یا...") },
                    leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // 3. Attendance count stepper
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("تعداد حاضرین", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledIconButton(
                            onClick = { if (count > 1) count-- },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                        }
                        Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        FilledIconButton(
                            onClick = { count++ },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // 4. Employment segmented chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نوع استخدام / شرکت:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("COMPANY" to "شرکتی (امانی)", "SUBCONTRACTOR" to "پیمانکار").forEach { (typeKey, typeVal) ->
                            val isSelected = employmentType == typeKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { employmentType = typeKey },
                                label = { Text(typeVal, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 5. Comments
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("توضیحات") },
                    placeholder = { Text("ساعات اضافه کار، غیبت‌ها یا...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // 6. Vacation / Leave checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isOnLeave = !isOnLeave }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = isOnLeave,
                        onCheckedChange = { isOnLeave = it }
                    )
                    Text(
                        text = "این فرد در مرخصی است ✈️ (بدون محاسبه در آمار فعالان)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Large Submit Button
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "لطفا نام پرسنل را وارد کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val entry = ManpowerEntry(
                            name = name,
                            role = role,
                            count = count,
                            comments = comments,
                            employmentType = employmentType,
                            isOnLeave = isOnLeave
                        )
                        if (editingIndex >= 0) {
                            viewModel.updateCurrentReport { r -> 
                                val newList = r.manpower.toMutableList()
                                newList[editingIndex] = entry
                                r.copy(manpower = newList)
                            }
                            editingIndex = -1
                            Toast.makeText(context, "ویرایش با موفقیت انجام شد", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateCurrentReport { r -> r.copy(manpower = r.manpower + entry) }
                            Toast.makeText(context, "پرسنل با موفقیت درج شد", Toast.LENGTH_SHORT).show()
                        }
                        // Clear fields
                        name = ""
                        role = ""
                        count = 1
                        comments = ""
                        isOnLeave = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (editingIndex >= 0) Color(0xFF2563EB) else Color(0xFF0D9488)) // Dark Teal color
                ) {
                    Icon(if (editingIndex >= 0) Icons.Default.Edit else Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (editingIndex >= 0) "ثبت ویرایش" else "درج در جدول نیروهای انسانی کارگاه +", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Added List Header
        if (report.manpower.isNotEmpty()) {
            Text(
                text = "جدول نیروهای انسانی ثبت‌شده امروز 👥",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            report.manpower.forEachIndexed { index, item ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "نیروی شماره ${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D9488),
                                fontSize = 12.sp
                            )
                            Row {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.manpower.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index - 1]
                                            updated[index - 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(manpower = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, "بالا", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                if (index < report.manpower.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val updated = report.manpower.toMutableList()
                                            val temp = updated[index]
                                            updated[index] = updated[index + 1]
                                            updated[index + 1] = temp
                                            viewModel.updateCurrentReport { r -> r.copy(manpower = updated) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, "پایین", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        name = item.name
                                        role = item.role
                                        count = item.count
                                        employmentType = item.employmentType
                                        comments = item.comments
                                        isOnLeave = item.isOnLeave
                                        editingIndex = index
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "ویرایش", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val updated = report.manpower.filterIndexed { i, _ -> i != index }
                                        viewModel.updateCurrentReport { r -> r.copy(manpower = updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        var expanded by remember(item.name) { mutableStateOf(false) }
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("سمت: ${item.role.ifEmpty { "نا مشخص" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("تعداد حاضرین: ${item.count} نفر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val typeLabel = if (item.employmentType == "COMPANY") "شرکتی (امانی)" else "پیمانکار"
                            Text("نوع استخدام: $typeLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            
                            if (item.isOnLeave) {
                                Text("وضعیت: در مرخصی ✈️", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                            } else {
                                Text("وضعیت: حاضر در کارگاه ✅", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                            }
                        }

                        if (item.comments.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Comment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text("توضیحات: ${item.comments}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB: PHOTO GALLERY ---
@Composable
fun PhotoGalleryTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport
) {
    val context = LocalContext.current
    val temporaryPhotos by viewModel.temporaryPhotos.collectAsStateWithLifecycle()

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris: List<android.net.Uri> ->
        val currentCount = temporaryPhotos.size
        val allowedToAdd = 4 - currentCount
        val urisToAdd = uris.take(allowedToAdd)
        
        urisToAdd.forEach { uri ->
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flag)
            } catch (e: SecurityException) {
                // Ignore if we can't take persistable permission
            }
            viewModel.addTemporaryPhoto(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                if (temporaryPhotos.size < 4) {
                    launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = temporaryPhotos.size < 4
        ) {
            Icon(Icons.Default.Add, contentDescription = "افزودن تصویر")
            Spacer(modifier = Modifier.width(8.dp))
            Text("افزودن تصویر از گالری (${temporaryPhotos.size}/4)", fontWeight = FontWeight.Bold)
        }
        
        Text(
            text = "توجه: تصاویر فقط برای پیوست در فایل PDF استفاده می‌شوند و در حافظه برنامه ذخیره نمی‌گردند.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (temporaryPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("هیچ تصویری برای این گزارش پیوست نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            temporaryPhotos.forEach { photo ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        ) {
                            coil.compose.AsyncImage(
                                model = photo.uri,
                                contentDescription = "تصویر پیوست",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            IconButton(
                                onClick = { viewModel.removeTemporaryPhoto(photo) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        OutlinedTextField(
                            value = photo.description,
                            onValueChange = { newValue ->
                                viewModel.updateTemporaryPhotoDescription(photo, newValue)
                            },
                            placeholder = { Text("عنوان یا توضیحات تصویر...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

// --- TAB 6: GENERAL NOTES (OBSTACLES & TOMORROW'S PLAN) ---
@Composable
fun NotesTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Obstacles & Problems (موانع و مشکلات کارگاهی)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "موانع، مشکلات و نواقص کارگاهی 🛑",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFDC2626) // Warning/Error Red color
                    )
                    Icon(Icons.Default.Warning, "warning", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "مانند: خرابی ماشین‌آلات کلیدی، عدم تامین متریال لازم، شرایط جوی سهمگین، تاخیرات ترابری و...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = report.obstacles,
                    onValueChange = { newValue ->
                        viewModel.updateCurrentReport { it.copy(obstacles = newValue) }
                    },
                    placeholder = {
                        Text("مثال: خرابی گریدر خط آبرسانی در بخش کاتر، عدم دسترسی آسان به زون ۲ به دلیل گل سنگین...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 4
                )
            }
        }

        // Card 2: Tomorrow's Plan (پیش‌بینی برنامه فعالیت‌های روز آینده)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "پیش‌بینی برنامه فعالیت‌های روز آینده ✍️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F766E) // Dark Teal/Green color
                    )
                    Icon(Icons.Default.Info, "info", tint = Color(0xFF0F766E), modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "فعالیت‌هایی که برای روال کار بهینه‌تر فردا مد نظر دارید را به صورت موردی ذکر کنید.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = report.tomorrowPlan,
                    onValueChange = { newValue ->
                        viewModel.updateCurrentReport { it.copy(tomorrowPlan = newValue) }
                    },
                    placeholder = {
                        Text("مثال: بتن‌ریزی رینگ لبه سقف دوم، بارگیری آرماتورهای ستونی کارهای تکمیلی...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 4
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Bottom Button: Save final
        Button(
            onClick = {
                viewModel.saveCurrentReportImmediately()
                Toast.makeText(context, "گزارش با موفقیت ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)) // Teal/Green primary button
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("ذخیره نهایی و بازگشت 🏁", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// --- REDESIGNED SCREEN 4-2-LEGAL: LEGAL PERMITS TAB ---
@Composable
fun LegalPermitsTabRedesigned(
    viewModel: ReportViewModel,
    report: DailyReport
) {
    var title by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add form
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (editingIndex == null) "افزودن مجوز یا استعلام جدید ➕" else "ویرایش مجوز یا استعلام ✏️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { RequiredFieldLabel("عنوان مجوز یا استعلام") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it },
                    label = { Text("سازمان / ارگان") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("توضیحات") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "عنوان الزامی است", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val entry = LegalPermitEntry(
                            title = title,
                            organization = organization,
                            comments = comments
                        )
                        if (editingIndex == null) {
                            viewModel.updateCurrentReport { r -> r.copy(legalPermits = r.legalPermits + entry) }
                            Toast.makeText(context, "مجوز افزوده شد", Toast.LENGTH_SHORT).show()
                        } else {
                            val updated = report.legalPermits.toMutableList()
                            updated[editingIndex!!] = entry
                            viewModel.updateCurrentReport { r -> r.copy(legalPermits = updated) }
                            Toast.makeText(context, "مجوز ویرایش شد", Toast.LENGTH_SHORT).show()
                            editingIndex = null
                        }
                        
                        // Clear form
                        title = ""
                        organization = ""
                        comments = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (editingIndex == null) Icons.Default.Add else Icons.Default.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (editingIndex == null) "ثبت مجوز" else "ثبت ویرایش")
                }
                
                if (editingIndex != null) {
                    TextButton(
                        onClick = {
                            editingIndex = null
                            title = ""
                            organization = ""
                            comments = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("انصراف", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // List
        Text(
            "مجوزهای ثبت شده (${report.legalPermits.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )

        report.legalPermits.forEachIndexed { index, permit ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            permit.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row {
                            if (index > 0) {
                                IconButton(
                                    onClick = {
                                        val updated = report.legalPermits.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index - 1]
                                        updated[index - 1] = temp
                                        viewModel.updateCurrentReport { r -> r.copy(legalPermits = updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "بالا", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                            }
                            if (index < report.legalPermits.size - 1) {
                                IconButton(
                                    onClick = {
                                        val updated = report.legalPermits.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index + 1]
                                        updated[index + 1] = temp
                                        viewModel.updateCurrentReport { r -> r.copy(legalPermits = updated) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "پایین", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    title = permit.title
                                    organization = permit.organization
                                    comments = permit.comments
                                    editingIndex = index
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, "ویرایش", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val updated = report.legalPermits.filterIndexed { i, _ -> i != index }
                                    viewModel.updateCurrentReport { r -> r.copy(legalPermits = updated) }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (permit.organization.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Business, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ارگان: ${permit.organization}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    if (permit.comments.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Comment, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(permit.comments, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
