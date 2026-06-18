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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DailyReport): Long

    @Update
    suspend fun updateReport(report: DailyReport)

    @Delete
    suspend fun deleteReport(report: DailyReport)
}
