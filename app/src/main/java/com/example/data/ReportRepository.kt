package com.example.data

import com.example.data.model.DailyReport
import kotlinx.coroutines.flow.Flow

class ReportRepository(private val dailyReportDao: DailyReportDao) {
    val allReports: Flow<List<DailyReport>> = dailyReportDao.getAllReports()

    suspend fun getReportById(id: Int): DailyReport? {
        return dailyReportDao.getReportById(id)
    }

    suspend fun insertReport(report: DailyReport): Long {
        return dailyReportDao.insertReport(report)
    }

    suspend fun updateReport(report: DailyReport) {
        dailyReportDao.updateReport(report)
    }

    suspend fun deleteReport(report: DailyReport) {
        dailyReportDao.deleteReport(report)
    }
}
