package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TaskEntry(
    val description: String = "",
    val location: String = "",
    val quantity: String = "",
    val unit: String = "",
    val accumulativeQuantity: String = "",
    val comments: String = "",
    val startKm: String = "",
    val endKm: String = ""
)

@JsonClass(generateAdapter = true)
data class MachineryEntry(
    val type: String = "",
    val activeCount: Int = 0,
    val inactiveCount: Int = 0,
    val workingHours: String = "",
    val comments: String = "",
    val ownershipType: String = "COMPANY" // "COMPANY" (شرکتی) or "RENTAL" (استیجاری)
)

@JsonClass(generateAdapter = true)
data class ManpowerEntry(
    val name: String = "",
    val role: String = "",
    val count: Int = 1,
    val comments: String = "",
    val employmentType: String = "COMPANY", // "COMPANY" (نیروی شرکت) or "SUBCONTRACTOR" (پیمانکار دست دوم)
    val subcontractorName: String = "",
    val isOnLeave: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MaterialEntry(
    val type: String = "",
    val count: String = "",
    val unit: String = "",
    val quantity: String = "",
    val loadingLocation: String = "",
    val unloadingLocation: String = "",
    val unloadingTime: String = "",
    val comments: String = "",
    val isExit: Boolean = false, // true for exiting, false for entry
    val receiver: String = "" // receiver of material (for exits)
)

@Entity(tableName = "daily_reports")
data class DailyReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val project: String = "",
    val section: String = "",
    val date: String = "",
    val weather: String = "",
    val preparedBy: String = "",
    val startKm: String = "",
    val endKm: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tasks: List<TaskEntry> = emptyList(),
    val machinery: List<MachineryEntry> = emptyList(),
    val manpower: List<ManpowerEntry> = emptyList(),
    val materials: List<MaterialEntry> = emptyList(),
    val obstacles: String = "",
    val tomorrowPlan: String = "",
    val reportType: String = "EXECUTION" // "EXECUTION" (اجرا) or "WAREHOUSE" (انبار)
)
