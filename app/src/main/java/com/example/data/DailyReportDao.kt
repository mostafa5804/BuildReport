package com.example.data

import androidx.room.*
import com.example.data.model.DailyReport
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReportDao {
    @Query("SELECT * FROM daily_reports ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<DailyReport>>

    @Query("SELECT * FROM daily_reports WHERE id = :id")
    suspend fun getReportById(id: Int): DailyReport?

    @Query("SELECT MAX(reportNumber) FROM daily_reports")
    suspend fun getMaxReportNumber(): Int?

    @Query("SELECT * FROM daily_reports ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestReport(): DailyReport?

    @Query("SELECT * FROM daily_reports WHERE date = :date AND section = :section AND project = :project LIMIT 1")
    suspend fun getExistingReport(date: String, section: String, project: String): DailyReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DailyReport): Long

    @Update
    suspend fun updateReport(report: DailyReport)

    @Delete
    suspend fun deleteReport(report: DailyReport)
}
