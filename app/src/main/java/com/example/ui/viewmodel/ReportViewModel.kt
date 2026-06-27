package com.example.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ReportRepository
import com.example.data.model.*
import com.example.util.PdfGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val repository: ReportRepository,
    val sharedPreferences: SharedPreferences
) : ViewModel() {

    // List of all historic reports
    val allReports: StateFlow<List<DailyReport>> = repository.allReports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search query for list filtering
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Real-time filtered reports based on date, project, or preparedBy
    val filteredReports: StateFlow<List<DailyReport>> = combine(allReports, _searchQuery) { reports, query ->
        if (query.isBlank()) {
            reports
        } else {
            reports.filter { report ->
                report.date.contains(query, ignoreCase = true) ||
                report.project.contains(query, ignoreCase = true) ||
                report.preparedBy.contains(query, ignoreCase = true) ||
                (report.section.contains(query, ignoreCase = true))
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current report being edited
    private val _currentReport = MutableStateFlow<DailyReport?>(null)
    val currentReport: StateFlow<DailyReport?> = _currentReport.asStateFlow()

    // Flag showing if auto-saving is currently occurring
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // Export status tracking
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    val backupRestoredEvent = MutableStateFlow(0)

    init {
        // Auto-save pipeline with debounce to prevent database write spam during typing
        _currentReport
            .filterNotNull()
            .debounce(2000) // Debounce 2 seconds of inactivity
            .distinctUntilChanged()
            .onEach { report ->
                if (report.id != 0 || report.project.isNotEmpty() || report.preparedBy.isNotEmpty()) {
                    _isSaving.value = true
                    val insertedId = repository.insertReport(report)
                    if (report.id == 0 && insertedId > 0) {
                        // Keep track of the generated Room ID for future updates instead of inserting a new row
                        _currentReport.value = report.copy(id = insertedId.toInt())
                    }
                    val todayDate = getTodayPersianDate()
                    if (report.date == todayDate) {
                        sharedPreferences.edit().putString("last_reported_date", todayDate).apply()
                    }
                    _isSaving.value = false
                }
            }
            .launchIn(viewModelScope)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Force an immediate manual save
    fun saveCurrentReportImmediately() {
        val report = _currentReport.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            val insertedId = repository.insertReport(report)
            if (report.id == 0 && insertedId > 0) {
                _currentReport.value = report.copy(id = insertedId.toInt())
            }
            val todayDate = getTodayPersianDate()
            if (report.date == todayDate) {
                sharedPreferences.edit().putString("last_reported_date", todayDate).apply()
            }
            _isSaving.value = false
        }
    }

    fun startNewReport(type: String) {
        // SharedPreferences settings with sensible fallbacks
        val defaultProject = sharedPreferences.getString("default_project", "")?.ifEmpty { null }
            ?: allReports.value.firstOrNull()?.project
            ?: "پروژه عمرانی مپصا"
            
        val defaultPreparedBy = sharedPreferences.getString("default_prepared_by", "")?.ifEmpty { null }
            ?: allReports.value.firstOrNull()?.preparedBy
            ?: ""
            
        val defaultSection = sharedPreferences.getString("default_section", "")?.ifEmpty { null }
            ?: allReports.value.firstOrNull()?.section
            ?: (if (type == "EXECUTION") "بخش فنی و مهندسی" else "انبار مرکزی")
            
        val defaultStartKm = sharedPreferences.getString("default_start_km", "")?.ifEmpty { null }
            ?: allReports.value.firstOrNull()?.startKm
            ?: ""
            
        val defaultEndKm = sharedPreferences.getString("default_end_km", "")?.ifEmpty { null }
            ?: allReports.value.firstOrNull()?.endKm
            ?: ""
            
        val defaultWeather = sharedPreferences.getString("default_weather", "")?.ifEmpty { null }
            ?: allReports.value.firstOrNull()?.weather
            ?: "آفتابی ☀️"

        val todayPersian = getTodayPersianDate()
        
        _currentReport.value = DailyReport(
            project = defaultProject,
            preparedBy = defaultPreparedBy,
            section = defaultSection,
            startKm = defaultStartKm,
            endKm = defaultEndKm,
            date = todayPersian,
            weather = defaultWeather,
            reportType = type
        )
    }

    // Duplicate an existing report with today's date so user doesn't re-enter data
    fun duplicateReport(report: DailyReport) {
        viewModelScope.launch {
            val duplicatedReport = report.copy(
                id = 0, // Auto-generate new Room ID
                date = getTodayPersianDate(),
                createdAt = System.currentTimeMillis()
            )
            repository.insertReport(duplicatedReport)
        }
    }

    fun selectReport(reportId: Int) {
        viewModelScope.launch {
            val report = repository.getReportById(reportId)
            _currentReport.value = report
        }
    }

    fun deleteReport(report: DailyReport) {
        viewModelScope.launch {
            if (_currentReport.value?.id == report.id) {
                _currentReport.value = null
            }
            repository.deleteReport(report)
        }
    }

    fun clearCurrentReport() {
        _currentReport.value = null
    }

    fun updateCurrentReport(updater: (DailyReport) -> DailyReport) {
        val current = _currentReport.value ?: return
        _currentReport.value = updater(current)
    }

    // Helper: Dynamic Shamsi Date Calculator
    fun getTodayPersianDate(): String {
        val calendar = java.util.GregorianCalendar(java.util.Locale.US)
        val gYear = calendar.get(Calendar.YEAR)
        val gMonth = calendar.get(Calendar.MONTH) + 1
        val gDay = calendar.get(Calendar.DAY_OF_MONTH)
        
        val pDate = gregorianToJalali(gYear, gMonth, gDay)
        return "${toPersianDigits(pDate[0])}/${toPersianDigits(pDate[1])}/${toPersianDigits(pDate[2])}"
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
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
                } else {
                    break
                }
            } else {
                jDayNo -= jDaysInMonth[jm]
                jm++
            }
        }
        return intArrayOf(jy, jm + 1, jDayNo + 1)
    }

    private fun toPersianDigits(num: Int): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        if (num < 0) {
            val positivePart = (-num).toString()
            val converted = positivePart.map { c -> if (c.isDigit()) persianDigits[c - '0'] else c }.joinToString("")
            return "-$converted"
        }
        val numStr = if (num < 10) "0$num" else num.toString()
        val builder = StringBuilder()
        for (i in 0 until numStr.length) {
            val c = numStr[i]
            if (Character.isDigit(c)) {
                val digit = Character.getNumericValue(c)
                builder.append(persianDigits[digit])
            } else {
                builder.append(c)
            }
        }
        return builder.toString()
    }

    // Save defaults to SharedPreferences
    fun saveDefaultConfig(
        project: String,
        section: String,
        preparedBy: String,
        reportType: String,
        weather: String,
        startKm: String,
        endKm: String
    ) {
        sharedPreferences.edit().apply {
            putString("default_project", project)
            putString("default_section", section)
            putString("default_prepared_by", preparedBy)
            putString("default_report_type", reportType)
            putString("default_weather", weather)
            putString("default_start_km", startKm)
            putString("default_end_km", endKm)
            apply()
        }
    }

    // PDF generation and direct share/print logic using the platform print service
    fun generateAndSharePdf(context: Context, report: DailyReport, onFinished: (Boolean) -> Unit) {
        saveCurrentReportImmediately()
        _isExporting.value = true
        
        val signatureBase64 = sharedPreferences.getString("user_signature", "") ?: ""
        val customUnitTitle = sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها"
        val htmlContent = PdfGenerator.generateHtmlReport(report, signatureBase64, customUnitTitle)
        
        // WebViews must be run on the UI Main thread
        viewModelScope.launch {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                        val jobName = "CivilSync_${report.project.replace(" ", "_")}_${report.date.replace("/", "-")}"
                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                        
                        // Action: Starts native PDF Preview, Export & Print flow
                        printManager.print(
                            jobName, 
                            printAdapter, 
                            PrintAttributes.Builder().build()
                        )
                        _isExporting.value = false
                        onFinished(true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _isExporting.value = false
                        onFinished(false)
                    }
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }

    // Direct PDF File Generation and share to other apps (system chooser sharing .pdf file)
    fun generateAndSharePdfFileDirectly(context: Context, report: DailyReport, onFinished: (Boolean) -> Unit) {
        saveCurrentReportImmediately()
        _isExporting.value = true
        
        val signatureBase64 = sharedPreferences.getString("user_signature", "") ?: ""
        val customUnitTitle = sharedPreferences.getString("custom_unit_title", "سایر واحدها") ?: "سایر واحدها"
        val htmlContent = PdfGenerator.generateHtmlReport(report, signatureBase64, customUnitTitle)
        
        viewModelScope.launch {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        val jobName = "CivilSync_${report.project.replace(" ", "_")}_${report.date.replace("/", "-")}"
                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                        
                        // Output pdf file in cache directory
                        val cacheDir = context.cacheDir
                        val sectionName = when (report.reportType) {
                            "WAREHOUSE" -> "گزارش انبار"
                            "LEGAL" -> "گزارش حقوقی"
                            "SURVEY" -> "گزارش نقشه برداری"
                            "TECHNICAL" -> "گزارش دفتر فنی"
                            "HSE" -> "گزارش ایمنی HSE"
                            "CUSTOM" -> customUnitTitle
                            else -> "گزارش اجرا"
                        }
                        val cleanDateStr = report.date.replace("/", ".")
                        val outputPdfFile = File(cacheDir, "$sectionName - $cleanDateStr.pdf")
                        if (outputPdfFile.exists()) {
                            outputPdfFile.delete()
                        }
                        
                        android.print.PdfPrinterHelper.printAdapterToFile(printAdapter, outputPdfFile) { success ->
                            _isExporting.value = false
                            if (success) {
                                PdfGenerator.sharePdfFile(context, outputPdfFile)
                                onFinished(true)
                            } else {
                                onFinished(false)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _isExporting.value = false
                        onFinished(false)
                    }
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }

    // Export all reports as a pretty JSON string
    fun exportBackup(): String {
        val rootObj = org.json.JSONObject()
        rootObj.put("backup_version", 2)
        
        // Reports List
        val rootArray = org.json.JSONArray()
        for (report in allReports.value) {
            val reportObj = org.json.JSONObject().apply {
                put("project", report.project)
                put("section", report.section)
                put("date", report.date)
                put("weather", report.weather)
                put("preparedBy", report.preparedBy)
                put("startKm", report.startKm)
                put("endKm", report.endKm)
                put("createdAt", report.createdAt)
                put("obstacles", report.obstacles)
                put("tomorrowPlan", report.tomorrowPlan)
                put("reportType", report.reportType)

                // Tasks array
                val tasksArray = org.json.JSONArray()
                for (task in report.tasks) {
                    tasksArray.put(org.json.JSONObject().apply {
                        put("description", task.description)
                        put("location", task.location)
                        put("quantity", task.quantity)
                        put("unit", task.unit)
                        put("accumulativeQuantity", task.accumulativeQuantity)
                        put("comments", task.comments)
                    })
                }
                put("tasks", tasksArray)

                // Machinery array
                val mechArray = org.json.JSONArray()
                for (mech in report.machinery) {
                    mechArray.put(org.json.JSONObject().apply {
                        put("type", mech.type)
                        put("activeCount", mech.activeCount)
                        put("inactiveCount", mech.inactiveCount)
                        put("workingHours", mech.workingHours)
                        put("comments", mech.comments)
                        put("ownershipType", mech.ownershipType)
                    })
                }
                put("machinery", mechArray)

                // Manpower array
                val manArray = org.json.JSONArray()
                for (man in report.manpower) {
                    manArray.put(org.json.JSONObject().apply {
                        put("name", man.name)
                        put("role", man.role)
                        put("count", man.count)
                        put("comments", man.comments)
                        put("employmentType", man.employmentType)
                        put("subcontractorName", man.subcontractorName)
                        put("isOnLeave", man.isOnLeave)
                    })
                }
                put("manpower", manArray)

                // Materials array
                val matArray = org.json.JSONArray()
                for (mat in report.materials) {
                    matArray.put(org.json.JSONObject().apply {
                        put("type", mat.type)
                        put("count", mat.count)
                        put("unit", mat.unit)
                        put("quantity", mat.quantity)
                        put("loadingLocation", mat.loadingLocation)
                        put("unloadingLocation", mat.unloadingLocation)
                        put("unloadingTime", mat.unloadingTime)
                        put("comments", mat.comments)
                        put("isExit", mat.isExit)
                        put("receiver", mat.receiver)
                    })
                }
                put("materials", matArray)
            }
            rootArray.put(reportObj)
        }
        rootObj.put("reports", rootArray)

        // Settings / SharedPreferences
        val settingsObj = org.json.JSONObject().apply {
            val stringKeys = listOf(
                "default_project", "default_section", "default_prepared_by", "default_weather",
                "custom_unit_title", "default_start_km", "default_end_km", "default_report_type",
                "user_signature", "app_theme", "dashboard_recent_activities", "dashboard_tomorrow_forecast"
            )
            for (key in stringKeys) {
                put(key, sharedPreferences.getString(key, ""))
            }
            put("daily_reminder_enabled", sharedPreferences.getBoolean("daily_reminder_enabled", true))

            val stringSetKeys = listOf(
                "manpower_roles_history", "manpower_names_history", "machinery_history", "site_materials_history",
                "warehouse_legal_history", "warehouse_survey_history", "warehouse_technical_history",
                "warehouse_exit_history", "warehouse_import_history"
            )
            for (key in stringSetKeys) {
                val set = sharedPreferences.getStringSet(key, null)
                if (set != null) {
                    put(key, org.json.JSONArray(set.toList()))
                }
            }
        }
        rootObj.put("settings", settingsObj)

        return rootObj.toString(2)
    }

    // Import reports from a JSON string and merge them into the repository
    fun importBackup(jsonString: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = jsonString.trim()
                val reportsArray: org.json.JSONArray
                val settingsObj: org.json.JSONObject?

                if (trimmed.startsWith("[")) {
                    // Old format: direct array of reports
                    reportsArray = org.json.JSONArray(trimmed)
                    settingsObj = null
                } else {
                    // New format: JSONObject wrapper
                    val root = org.json.JSONObject(trimmed)
                    reportsArray = root.getJSONArray("reports")
                    settingsObj = root.optJSONObject("settings")
                }

                // Restore SharedPreferences Settings
                if (settingsObj != null) {
                    val editor = sharedPreferences.edit()
                    val stringKeys = listOf(
                        "default_project", "default_section", "default_prepared_by", "default_weather",
                        "custom_unit_title", "default_start_km", "default_end_km", "default_report_type",
                        "user_signature", "app_theme", "dashboard_recent_activities", "dashboard_tomorrow_forecast"
                    )
                    for (key in stringKeys) {
                        if (settingsObj.has(key)) {
                            val v = settingsObj.getString(key)
                            if (v.isNotEmpty()) editor.putString(key, v)
                        }
                    }
                    if (settingsObj.has("daily_reminder_enabled")) {
                        editor.putBoolean("daily_reminder_enabled", settingsObj.getBoolean("daily_reminder_enabled"))
                    }

                    val stringSetKeys = listOf(
                        "manpower_roles_history", "manpower_names_history", "machinery_history", "site_materials_history",
                        "warehouse_legal_history", "warehouse_survey_history", "warehouse_technical_history",
                        "warehouse_exit_history", "warehouse_import_history"
                    )
                    for (key in stringSetKeys) {
                        if (settingsObj.has(key)) {
                            val arr = settingsObj.getJSONArray(key)
                            val set = mutableSetOf<String>()
                            for (k in 0 until arr.length()) {
                                set.add(arr.getString(k))
                            }
                            if (set.isNotEmpty()) editor.putStringSet(key, set)
                        }
                    }
                    editor.apply()
                    backupRestoredEvent.value += 1
                }

                var importedCount = 0
                for (i in 0 until reportsArray.length()) {
                    val obj = reportsArray.getJSONObject(i)
                    
                    // Parse tasks
                    val tasksList = mutableListOf<TaskEntry>()
                    val tasksArray = obj.optJSONArray("tasks")
                    if (tasksArray != null) {
                        for (j in 0 until tasksArray.length()) {
                            val taskObj = tasksArray.getJSONObject(j)
                            tasksList.add(TaskEntry(
                                description = taskObj.optString("description", ""),
                                location = taskObj.optString("location", ""),
                                quantity = taskObj.optString("quantity", ""),
                                unit = taskObj.optString("unit", ""),
                                accumulativeQuantity = taskObj.optString("accumulativeQuantity", ""),
                                comments = taskObj.optString("comments", "")
                            ))
                        }
                    }

                    // Parse machinery
                    val mechList = mutableListOf<MachineryEntry>()
                    val mechArray = obj.optJSONArray("machinery")
                    if (mechArray != null) {
                        for (j in 0 until mechArray.length()) {
                            val mechObj = mechArray.getJSONObject(j)
                            mechList.add(MachineryEntry(
                                type = mechObj.optString("type", ""),
                                activeCount = mechObj.optInt("activeCount", 0),
                                inactiveCount = mechObj.optInt("inactiveCount", 0),
                                workingHours = mechObj.optString("workingHours", ""),
                                comments = mechObj.optString("comments", ""),
                                ownershipType = mechObj.optString("ownershipType", "COMPANY")
                            ))
                        }
                    }

                    // Parse manpower
                    val manList = mutableListOf<ManpowerEntry>()
                    val manArray = obj.optJSONArray("manpower")
                    if (manArray != null) {
                        for (j in 0 until manArray.length()) {
                            val manObj = manArray.getJSONObject(j)
                            manList.add(ManpowerEntry(
                                name = manObj.optString("name", ""),
                                role = manObj.optString("role", ""),
                                count = manObj.optInt("count", 1),
                                comments = manObj.optString("comments", ""),
                                employmentType = manObj.optString("employmentType", "COMPANY"),
                                subcontractorName = manObj.optString("subcontractorName", ""),
                                isOnLeave = manObj.optBoolean("isOnLeave", false)
                            ))
                        }
                    }

                    // Parse materials
                    val matList = mutableListOf<MaterialEntry>()
                    val matArray = obj.optJSONArray("materials")
                    if (matArray != null) {
                        for (j in 0 until matArray.length()) {
                            val matObj = matArray.getJSONObject(j)
                            matList.add(MaterialEntry(
                                type = matObj.optString("type", ""),
                                count = matObj.optString("count", ""),
                                unit = matObj.optString("unit", ""),
                                quantity = matObj.optString("quantity", ""),
                                loadingLocation = matObj.optString("loadingLocation", ""),
                                unloadingLocation = matObj.optString("unloadingLocation", ""),
                                unloadingTime = matObj.optString("unloadingTime", ""),
                                comments = matObj.optString("comments", ""),
                                isExit = matObj.optBoolean("isExit", false),
                                receiver = matObj.optString("receiver", "")
                            ))
                        }
                    }

                    val report = DailyReport(
                        project = obj.optString("project", ""),
                        section = obj.optString("section", ""),
                        date = obj.optString("date", ""),
                        weather = obj.optString("weather", ""),
                        preparedBy = obj.optString("preparedBy", ""),
                        startKm = obj.optString("startKm", ""),
                        endKm = obj.optString("endKm", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        tasks = tasksList,
                        machinery = mechList,
                        manpower = manList,
                        materials = matList,
                        obstacles = obj.optString("obstacles", ""),
                        tomorrowPlan = obj.optString("tomorrowPlan", ""),
                        reportType = obj.optString("reportType", "EXECUTION")
                    )
                    
                    repository.insertReport(report)
                    importedCount++
                }
                onSuccess(importedCount)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "فرمت نامعتبر فایل پشتیبان")
            }
        }
    }
}

class ReportViewModelFactory(
    private val repository: ReportRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(repository, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
