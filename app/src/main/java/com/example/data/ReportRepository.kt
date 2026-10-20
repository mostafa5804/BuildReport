package com.example.data

import com.example.data.model.DailyReport
import kotlinx.coroutines.flow.Flow

class ReportRepository(private val dailyReportDao: DailyReportDao) {
    val allReports: Flow<List<DailyReport>> = dailyReportDao.getAllReports()

    suspend fun getReportById(id: Int): DailyReport? {
        return dailyReportDao.getReportById(id)
    }

    suspend fun getMaxReportNumber(): Int? {
        return dailyReportDao.getMaxReportNumber()
    }

    suspend fun getLatestReport(): DailyReport? {
        return dailyReportDao.getLatestReport()
    }

    suspend fun getExistingReport(date: String, section: String, project: String): DailyReport? {
        return dailyReportDao.getExistingReport(date, section, project)
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
