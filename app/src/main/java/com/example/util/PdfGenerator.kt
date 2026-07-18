package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.DailyReport
import java.io.File

object PdfGenerator {
    // Generate HTML String from DailyReport
    fun generateHtmlReport(
        context: Context,
        report: DailyReport, 
        signatureBase64: String = "", 
        customUnitTitle: String = "سایر واحدها",
        photos: List<com.example.data.model.DailyPhoto> = emptyList()
    ): String {
        val rType = report.reportType
        
        // Base title based on reportType
        val mainReportTitle = when (rType) {
            "WAREHOUSE" -> "گزارش روزانه ورود و خروج مصالح انبار"
            "LEGAL" -> "گزارش روزانه امور حقوقی و تحصیل اراضی"
            "SURVEY" -> "گزارش روزانه عملیات نقشه‌برداری کارگاه"
            "TECHNICAL" -> "گزارش روزانه دفتر فنی کارگاه"
            "HSE" -> "گزارش روزانه واحد ایمنی HSE کارگاه"
            "CUSTOM" -> "گزارش روزانه واحد $customUnitTitle"
            else -> "گزارش روزانه فعالیت‌های اجرایی کارگاه"
        }

        val machineryTableHeader = """
            <thead>
                <tr>
                    <th style="width: 5%;">ردیف</th>
                    <th style="width: 25%;">نام و مدل تجهیزات</th>
                    <th style="width: 12%;">وضعیت</th>
                    <th style="width: 10%;">فعال</th>
                    <th style="width: 10%;">غیر فعال</th>
                    <th style="width: 15%;">ساعت کارکرد</th>
                    <th style="width: 23%;">توضیحات و وضعیت</th>
                </tr>
            </thead>
        """.trimIndent()

        // Shared machinery rows helper
        val machineryRows = if (report.machinery.isEmpty()) {
            "<tr><td colspan='7' class='text-muted'>هیچ ماشین‌آلاتی برای این روز ثبت نشده است</td></tr>"
        } else {
            report.machinery.mapIndexed { index, machinery ->
                val statusLabel = when (machinery.ownershipType) {
                    "COMPANY" -> "شرکتی"
                    "RENTAL" -> "استیجاری"
                    else -> "پیمانکار"
                }
                """
                <tr>
                    <td class="cell-index">${index + 1}</td>
                    <td class="cell-main">${machinery.type}</td>
                    <td>$statusLabel</td>
                    <td>${machinery.activeCount}</td>
                    <td>${machinery.inactiveCount}</td>
                    <td>${machinery.workingHours.ifEmpty { "---" }}</td>
                    <td class="cell-desc">${machinery.comments.ifEmpty { "---" }}</td>
                </tr>
                """.trimIndent()
            }.joinToString("")
        }

        val manpowerTableHeader = """
            <thead>
                <tr>
                    <th style="width: 5%;">ردیف</th>
                    <th style="width: 25%;">نام پرسنل</th>
                    <th style="width: 20%;">سمت</th>
                    <th style="width: 15%;">تعداد حاضر</th>
                    <th style="width: 35%;">توضیحات (شرکتی - پیمانکار)</th>
                </tr>
            </thead>
        """.trimIndent()

        // Shared manpower rows helper
        val manpowerRows = if (report.manpower.isEmpty()) {
            "<tr><td colspan='5' class='text-muted'>هیچ آمار نیروی کارگری ثبت نشده است</td></tr>"
        } else {
            report.manpower.mapIndexed { index, manpower ->
                val empTypeLabel = if (manpower.employmentType == "SUBCONTRACTOR") {
                    if (manpower.subcontractorName.isNotEmpty()) "پیمانکار: ${manpower.subcontractorName}" else "پیمانکار"
                } else {
                    "شرکتی (امانی)"
                }
                val isLeave = manpower.isOnLeave
                val statusText = if (isLeave) "در مرخصی ✈️" else "حاضر - $empTypeLabel"
                val commentText = if (manpower.comments.isNotEmpty()) {
                    "$statusText (${manpower.comments})"
                } else {
                    statusText
                }
                val rowStyle = if (isLeave) "style='color: #ef4444; background-color: #fef2f2; font-weight: bold;'" else ""
                val countText = if (isLeave) "۰" else "${manpower.count}"
                """
                <tr $rowStyle>
                    <td class="cell-index">${index + 1}</td>
                    <td class="cell-main" style="${if (isLeave) "color: #ef4444;" else ""}">${manpower.name.ifEmpty { "اکیپ فعال" }}</td>
                    <td>${manpower.role.ifEmpty { "---" }}</td>
                    <td>$countText</td>
                    <td class="cell-desc">$commentText</td>
                </tr>
                """.trimIndent()
            }.joinToString("")
        }

        var bodyContentHtml = ""

        when (rType) {
            "WAREHOUSE" -> {
                // مصالح وارده (stored in tasks)
                val entriesList = report.tasks
                // مصالح صادره (stored in materials)
                val exitsList = report.materials

                val entriesRowsHtml = if (entriesList.isEmpty()) {
                    "<tr><td colspan='6' class='text-muted'>هیچ مصالح وارده‌ای ثبت نشده است</td></tr>"
                } else {
                    entriesList.mapIndexed { index, entry ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${entry.description}</td>
                            <td>${entry.quantity}</td>
                            <td>${entry.unit}</td>
                            <td>${entry.location.ifEmpty { "---" }}</td>
                            <td class="cell-desc">${entry.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                val exitsRowsHtml = if (exitsList.isEmpty()) {
                    "<tr><td colspan='6' class='text-muted'>هیچ مصالح صادره‌ای ثبت نشده است</td></tr>"
                } else {
                    exitsList.mapIndexed { index, exit ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${exit.type}</td>
                            <td>${exit.quantity}</td>
                            <td>${exit.unit}</td>
                            <td>${exit.unloadingLocation.ifEmpty { "---" }}</td>
                            <td class="cell-desc">${exit.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. مصالح وارده -->
                    <div class="section-title">۱. آمار ورود مصالح و متریال به انبار کارگاه (وارده)</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 25%;">نوع مصالح/کالا وارده</th>
                                <th style="width: 15%;">مقدار ورودی</th>
                                <th style="width: 15%;">واحد</th>
                                <th style="width: 20%;">تامین کننده / مبدا بارگیری</th>
                                <th style="width: 20%;">ملاحظات و وضعیت ظاهری</th>
                            </tr>
                        </thead>
                        <tbody>
                            $entriesRowsHtml
                        </tbody>
                    </table>

                    <!-- 2. مصالح صادره -->
                    <div class="section-title">۲. آمار مصرف و خروج متریال از انبار (صادره)</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 25%;">نوع مصالح/کالا خروجی</th>
                                <th style="width: 15%;">مقدار خروجی</th>
                                <th style="width: 12%;">واحد</th>
                                <th style="width: 20%;">تحویل گیرنده (اکیپ/شخص)</th>
                                <th style="width: 23%;">مورد مصرف و توضیحات (سند)</th>
                            </tr>
                        </thead>
                        <tbody>
                            $exitsRowsHtml
                        </tbody>
                    </table>

                    <!-- 3. ماشین‌آلات انبار -->
                    <div class="section-title">۳. وضعیت ماشین‌آلات و تجهیزات لجستیکی انبار</div>
                    <table class="report-table">
                        $machineryTableHeader
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی انبار -->
                    <div class="section-title">۴. آمار پرسنل و نیروهای شاغل در انبارداری</div>
                    <table class="report-table">
                        $manpowerTableHeader
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
            "LEGAL" -> {
                // Legal Task List representing "Tahsile Arazi"
                val landRows = if (report.tasks.isEmpty()) {
                    "<tr><td colspan='5' class='text-muted'>هیچ آمار مربوط به تحصیل اراضی در این روز ثبت نشده است</td></tr>"
                } else {
                    report.tasks.mapIndexed { index, task ->
                        val locText = if (task.startKm.isNotEmpty() || task.endKm.isNotEmpty()) {
                            formatKmRangeHtml(task.startKm, task.endKm)
                        } else {
                            task.location.ifEmpty { "عمومی" }
                        }
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${task.description}</td>
                            <td>$locText</td>
                            <td class="cell-highlight">${task.quantity} ${task.unit}</td>
                            <td class="cell-desc">${task.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                // Inquiries & Permits list stored in 'legalPermits'
                val inquiriesRows = if (report.legalPermits.isEmpty()) {
                    "<tr><td colspan='4' class='text-muted'>هیچ استعلام یا مجوز قانونی در این روز ثبت نگردیده است</td></tr>"
                } else {
                    report.legalPermits.mapIndexed { index, item ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${item.title}</td>
                            <td><strong>${item.organization.ifEmpty { "---" }}</strong></td>
                            <td class="cell-desc">${item.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. تحصیل اراضی -->
                    <div class="section-title">۱. روند پیشرفت تحصیل اراضی، تملک املاک و معارضات ملکی</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 40%;">شرح کار / ملک معارض</th>
                                <th style="width: 20%;">محدوده / کیلومتر ابتدا-انتها</th>
                                <th style="width: 15%;">حجم تملک / مقدار واگذاری</th>
                                <th style="width: 20%;">توضیحات و اقدامات حقوقی انجام شده</th>
                            </tr>
                        </thead>
                        <tbody>
                            $landRows
                        </tbody>
                    </table>

                    <!-- 2. اخذ مجوزات -->
                    <div class="section-title">۲. روند اخذ استعلامات ملی، موافقت‌نامه‌ها و مجوزهای قانونی</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 40%;">شرح استعلام / مجوز مورد نیاز</th>
                                <th style="width: 25%;">ارگان / سازمان صادرکننده</th>
                                <th style="width: 30%;">آخرین اقدامات و گزارش وضعیت پیگیری</th>
                            </tr>
                        </thead>
                        <tbody>
                            $inquiriesRows
                        </tbody>
                    </table>

                    <!-- 3. ماشین‌آلات -->
                    <div class="section-title">۳. تجهیزات نقلیه و مهندسی واحد حقوقی و نقشه برداری معارضین</div>
                    <table class="report-table">
                        $machineryTableHeader
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی -->
                    <div class="section-title">۴. مشاورین، کارشناسان رسمی و پرسنل پیگیری حقوقی اراضی</div>
                    <table class="report-table">
                        $manpowerTableHeader
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
            "SURVEY" -> {
                // Surveying Tasks Row
                val surveyRows = if (report.tasks.isEmpty()) {
                    "<tr><td colspan='7' class='text-muted'>هیچ عملیات نقشه‌برداری در این روز ثبت نشده است</td></tr>"
                } else {
                    report.tasks.mapIndexed { index, task ->
                        val locText = if (task.startKm.isNotEmpty() || task.endKm.isNotEmpty()) {
                            formatKmRangeHtml(task.startKm, task.endKm)
                        } else {
                            task.location.ifEmpty { "---" }
                        }
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${task.description}</td>
                            <td>$locText</td>
                            <td class="cell-highlight">${task.quantity}</td>
                            <td>${task.unit}</td>
                            <td>${task.accumulativeQuantity.ifEmpty { "---" }}</td>
                            <td class="cell-desc">${task.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. کارهای انجام شده -->
                    <div class="section-title">۱. خلاصه آمار عملیات‌های نقشه‌برداری و شیت‌ها</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">شرح عملیات نقشه‌برداری</th>
                                <th style="width: 20%;">ایستگاه / بنچمارک</th>
                                <th style="width: 10%;">کارکرد امروز</th>
                                <th style="width: 8%;">واحد</th>
                                <th style="width: 12%;">حجم کل تجمیعی</th>
                                <th style="width: 15%;">ملاحظات و وضعیت تحویل</th>
                            </tr>
                        </thead>
                        <tbody>
                            $surveyRows
                        </tbody>
                    </table>

                    <!-- 2. ماشین‌آلات -->
                    <div class="section-title">۲. وضعیت دوربین‌ها، پهپادها، تجهیزات GPS و ماشین ابزار نقشه‌برداری</div>
                    <table class="report-table">
                        $machineryTableHeader
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 3. نیروی انسانی -->
                    <div class="section-title">۳. آمار مهندسین نقشه‌بردار، کمک‌ نقشه‌برداران و میرداران حاضر در کارگاه</div>
                    <table class="report-table">
                        $manpowerTableHeader
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
            "HSE" -> {
                val tasksRows = if (report.tasks.isEmpty()) {
                    "<tr><td colspan='3' class='text-muted'>هیچ فعالیت ایمنی و بهداشت (HSE) در این تاریخ ثبت نشده است</td></tr>"
                } else {
                    report.tasks.mapIndexed { index, task ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${task.description}</td>
                            <td class="cell-desc">${task.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                val technicalMaterialsRows = if (report.materials.isEmpty()) {
                    "<tr><td colspan='5' class='text-muted'>هیچ اقلام یا مصالح ایمنی برای این روز ثبت نشده است</td></tr>"
                } else {
                    report.materials.mapIndexed { index, material ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${material.type}</td>
                            <td>${material.quantity}</td>
                            <td>${material.unit}</td>
                            <td class="cell-desc">${material.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. شرح فعالیت‌های ایمنی HSE -->
                    <div class="section-title">۱. خلاصه شرح کار و فعالیت‌های واحد ایمنی و بهداشت (HSE) کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 60%;">شرح اقدام یا فعالیت ایمنی (کنترل، آموزش، گشت و صدور مجوزها)</th>
                                <th style="width: 35%;">توضیحات و ملاحظات</th>
                            </tr>
                        </thead>
                        <tbody>
                            $tasksRows
                        </tbody>
                    </table>

                    <!-- 2. مصالح و تجهیزات ایمنی وارده -->
                    <div class="section-title">۲. آمار ورود مصالح و تجهیزات حفاظتی و ایمنی به کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نوع کالا/تجهیزات ایمنی وارده (مانند: کفش، کلاه، دستکش، علائم)</th>
                                <th style="width: 15%;">مقدار</th>
                                <th style="width: 12%;">واحد</th>
                                <th style="width: 33%;">توضیحات فرستنده و روال تامین</th>
                            </tr>
                        </thead>
                        <tbody>
                            $technicalMaterialsRows
                        </tbody>
                    </table>

                    <!-- 3. ماشین‌آلات ایمنی -->
                    <div class="section-title">۳. وضعیت تجهیزات ترابری، اطفای حریق و کمک‌های اولیه واحد ایمنی</div>
                    <table class="report-table">
                        $machineryTableHeader
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی ایمنی -->
                    <div class="section-title">۴. آمار افسران ایمنی، پزشک‌یار و پرسنل بخش HSE</div>
                    <table class="report-table">
                        $manpowerTableHeader
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
            "CUSTOM" -> {
                val tasksRows = if (report.tasks.isEmpty()) {
                    "<tr><td colspan='3' class='text-muted'>هیچ فعالیت واحد $customUnitTitle در این تاریخ ثبت نشده است</td></tr>"
                } else {
                    report.tasks.mapIndexed { index, task ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${task.description}</td>
                            <td class="cell-desc">${task.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                val technicalMaterialsRows = if (report.materials.isEmpty()) {
                    "<tr><td colspan='5' class='text-muted'>هیچ مصالح اختصاصی برای این روز ثبت نشده است</td></tr>"
                } else {
                    report.materials.mapIndexed { index, material ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${material.type}</td>
                            <td>${material.quantity}</td>
                            <td>${material.unit}</td>
                            <td class="cell-desc">${material.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. شرح فعالیت‌های واحد سفارشی -->
                    <div class="section-title">۱. خلاصه شرح کار و فعالیت‌های واحد $customUnitTitle کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 60%;">شرح کامل فعالیت واحد $customUnitTitle</th>
                                <th style="width: 35%;">توضیحات و ملاحظات</th>
                            </tr>
                        </thead>
                        <tbody>
                            $tasksRows
                        </tbody>
                    </table>

                    <!-- 2. مصالح تخصصی وارده -->
                    <div class="section-title">۲. آمار ورود مصالح و اقلام تخصصی واحد $customUnitTitle</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نوع مصالح/کالا تخصصی وارده</th>
                                <th style="width: 15%;">مقدار</th>
                                <th style="width: 12%;">واحد</th>
                                <th style="width: 33%;">توضیحات و فرستندگان اقلام</th>
                            </tr>
                        </thead>
                        <tbody>
                            $technicalMaterialsRows
                        </tbody>
                    </table>

                    <!-- 3. ماشین‌آلات -->
                    <div class="section-title">۳. وضعیت ماشین‌آلات و تجهیزات واحد $customUnitTitle</div>
                    <table class="report-table">
                        $machineryTableHeader
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی -->
                    <div class="section-title">۴. آمار پرسنل و نیروهای فعال واحد $customUnitTitle</div>
                    <table class="report-table">
                        $manpowerTableHeader
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
            "TECHNICAL" -> {
                val tasksRows = if (report.tasks.isEmpty()) {
                    "<tr><td colspan='3' class='text-muted'>هیچ فعالیت دفتر فنی در این تاریخ ثبت نشده است</td></tr>"
                } else {
                    report.tasks.mapIndexed { index, task ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${task.description}</td>
                            <td class="cell-desc">${task.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                val technicalMaterialsRows = if (report.materials.isEmpty()) {
                    "<tr><td colspan='5' class='text-muted'>هیچ مصالح تخصصی برای این روز ثبت نشده است</td></tr>"
                } else {
                    report.materials.mapIndexed { index, material ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${material.type}</td>
                            <td>${material.quantity}</td>
                            <td>${material.unit}</td>
                            <td class="cell-desc">${material.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. شرح فعالیت‌های دفتر فنی -->
                    <div class="section-title">۱. خلاصه شرح کار و فعالیت‌های دفتر فنی کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 60%;">شرح کامل فعالیت دفتر فنی (طراحی، متره و برآورد، رسیدگی به صورت وضعیت‌ها)</th>
                                <th style="width: 35%;">توضیحات و ملاحظات</th>
                            </tr>
                        </thead>
                        <tbody>
                            $tasksRows
                        </tbody>
                    </table>

                    <!-- 2. مصالح تخصصی وارده -->
                    <div class="section-title">۲. آمار ورود مصالح تخصصی و متریال خاص به کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نوع مصالح/کالا تخصصی وارده</th>
                                <th style="width: 15%;">مقدار</th>
                                <th style="width: 12%;">واحد</th>
                                <th style="width: 33%;">توضیحات واردات و فرستنده</th>
                            </tr>
                        </thead>
                        <tbody>
                            $technicalMaterialsRows
                        </tbody>
                    </table>

                    <!-- 3. ماشین‌آلات -->
                    <div class="section-title">۳. وضعیت تجهیزات دفتری، آزمایشی و ترابری واحد فنی</div>
                    <table class="report-table">
                        $machineryTableHeader
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی -->
                    <div class="section-title">۴. آمار مهندسان، کارشناسان و پرسنل بخش دفتر فنی و کنترل پروژه</div>
                    <table class="report-table">
                        $manpowerTableHeader
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
            else -> {
                // Execution report layout
                val tasksRows = if (report.tasks.isEmpty()) {
                    "<tr><td colspan='6' class='text-muted'>هیچ فعالیت اجرایی در این تاریخ ثبت نشده است</td></tr>"
                } else {
                    report.tasks.mapIndexed { index, task ->
                        val locText = if (task.startKm.isNotEmpty() || task.endKm.isNotEmpty()) {
                            formatKmRangeHtml(task.startKm, task.endKm)
                        } else {
                            task.location.ifEmpty { "---" }
                        }
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${task.description}</td>
                            <td>$locText</td>
                            <td class="cell-highlight">${task.quantity}</td>
                            <td>${task.unit}</td>
                            <td>${task.comments.ifEmpty { task.accumulativeQuantity.ifEmpty { "---" } }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                val executionMaterialsRows = if (report.materials.isEmpty()) {
                    "<tr><td colspan='5' class='text-muted'>هیچ آمار مصالح وارده‌ای برای این روز ثبت نشده است</td></tr>"
                } else {
                    report.materials.mapIndexed { index, material ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${material.type}</td>
                            <td>${material.quantity}</td>
                            <td>${material.unit}</td>
                            <td class="cell-desc">${material.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. شرح کارها -->
                    <div class="section-title">۱. خلاصه شرح کار و فعالیت‌های اجرایی کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 45%;">شرح کامل فعالیت</th>
                                <th style="width: 18%;">محل انجام / کیلومتر ابتدا-انتها</th>
                                <th style="width: 10%;">کارکرد امروز</th>
                                <th style="width: 10%;">واحد</th>
                                <th style="width: 12%;">توضیحات و ملاحظات</th>
                            </tr>
                        </thead>
                        <tbody>
                            $tasksRows
                        </tbody>
                    </table>

                    <!-- 2. ماشین‌آلات -->
                    <div class="section-title">۲. وضعیت ماشین‌آلات، تجهیزات سنگین و سبک مستقر در کارگاه</div>
                    <table class="report-table">
                        $machineryTableHeader
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 3. نیروی انسانی -->
                    <div class="section-title">۳. آمار نیروی انسانی فنی، اجرایی و کارگری شاغل در کارگاه</div>
                    <table class="report-table">
                        $manpowerTableHeader
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>

                    <!-- 4. مصالح وارده -->
                    <div class="section-title">۴. آمار ورود مصالح و متریال به کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نوع مصالح/کالا وارده</th>
                                <th style="width: 15%;">مقدار</th>
                                <th style="width: 12%;">واحد</th>
                                <th style="width: 33%;">توضیحات و محل تخلیه</th>
                            </tr>
                        </thead>
                        <tbody>
                            $executionMaterialsRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
        }

        return """
            <!DOCTYPE html>
            <html lang="fa" dir="rtl">
            <head>
                <meta charset="utf-8">
                <title>$mainReportTitle - ${report.project}</title>
                <style>
                    @font-face {
                        font-family: 'Vazir';
                        src: url('https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@v33.003/fonts/webfonts/Vazirmatn-Regular.woff2') format('woff2');
                    }
                    * {
                        box-sizing: border-box;
                    }
                    body {
                        font-family: 'Vazir', 'Tahoma', 'Arial', sans-serif;
                        direction: rtl;
                        font-size: 13px;
                        color: #1f2937;
                        line-height: 1.55;
                        padding: 15px;
                        margin: 0;
                        background-color: #ffffff;
                    }
                    .text-center { text-align: center; }
                    .text-right { text-align: right; }
                    .font-bold { font-weight: bold; }
                    
                    /* Design Header Container with Steel borders / Industrial Accent */
                    .header-container {
                        border: 2px solid #1e3a8a;
                        border-radius: 6px;
                        padding: 14px;
                        margin-bottom: 12px;
                        background-color: #f8fafc;
                        box-shadow: 0 1px 2px rgba(0,0,0,0.02);
                    }
                    .main-title {
                        font-size: 19px;
                        font-weight: bold;
                        text-align: center;
                        color: #1e3a8a;
                        margin: 0 0 8px 0;
                        border-bottom: 2px solid #3b82f6;
                        padding-bottom: 6px;
                    }
                    
                    /* Metadata grid - double column */
                    .meta-grid {
                        width: 100%;
                        border-collapse: collapse;
                    }
                    .meta-grid td {
                        border: none;
                        padding: 5px;
                        text-align: right;
                        font-size: 13px;
                        color: #374151;
                    }
                    .meta-grid td strong {
                        color: #1e3a8a;
                    }
                    
                    /* Section title header styles */
                    .section-title {
                        font-weight: bold;
                        font-size: 14px;
                        color: #ffffff;
                        background-color: #0284c7; /* Sky Blue variant */
                        padding: 6px 12px;
                        margin-top: 16px;
                        margin-bottom: 8px;
                        border-radius: 4px;
                        border-right: 4px solid #0369a1;
                    }
                    
                    /* Elegant Industrial Grid Tables with rounded borders */
                    table.report-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 14px;
                    }
                    table.report-table th, table.report-table td {
                        border: 1px solid #cbd5e1;
                        padding: 6px 5px;
                        text-align: center;
                        font-size: 12px;
                    }
                    table.report-table th {
                        background-color: #f1f5f9;
                        color: #1e3a8a;
                        font-weight: bold;
                        border-bottom: 2px solid #94a3b8;
                    }
                    table.report-table tr:nth-child(even) {
                        background-color: #f8fafc;
                    }
                    .cell-main {
                        text-align: right !important;
                        padding-right: 8px !important;
                        font-weight: bold;
                        color: #0f172a;
                    }
                    .cell-index {
                        font-weight: bold;
                        color: #64748b;
                        background-color: #f1f5f9;
                    }
                    .cell-highlight {
                        font-weight: bold;
                        color: #2563eb;
                    }
                    .cell-desc {
                        text-align: right !important;
                        padding-right: 6px !important;
                        font-size: 11.5px;
                        color: #475569;
                    }
                    .text-muted {
                        color: #94a3b8;
                        font-style: italic;
                        padding: 10px !important;
                    }
                    
                    /* Extra design elements for memos/notes */
                    .memo-container {
                        width: 100%;
                        margin-top: 12px;
                    }
                    .memo-box {
                        border: 1px solid #cbd5e1;
                        border-right: 5px solid #f59e0b; /* Safety amber */
                        border-radius: 4px;
                        padding: 10px;
                        margin-bottom: 10px;
                        background-color: #fffbeb;
                    }
                    .memo-title {
                        font-weight: bold;
                        color: #b45309;
                        margin-bottom: 4px;
                        font-size: 12.5px;
                    }
                    .memo-text {
                        color: #451a03;
                        white-space: pre-line;
                        font-size: 11.5px;
                    }
                    
                    /* Signatures footer modern grid with clear lines */
                    .footer-signatures {
                        margin-top: 28px;
                        width: 100%;
                        border-collapse: collapse;
                    }
                    .footer-signatures td {
                        border: none;
                        width: 50%;
                        text-align: center;
                        vertical-align: top;
                        font-weight: bold;
                        padding: 8px;
                        font-size: 13px;
                        color: #1e3a8a;
                    }
                    .signature-line {
                        margin-top: 24px;
                        border-top: 1.5px dashed #3b82f6;
                        width: 140px;
                        margin-left: auto;
                        margin-right: auto;
                    }
                    .signature-subtitle {
                        font-size: 11px;
                        color: #64748b;
                        margin-top: 4px;
                        font-weight: normal;
                    }

                    /* =======================================================
                       PRINT STYLING (A4 - Grayscale & fits exactly on 1 Page)
                       ======================================================= */
                    @media print {
                        @page {
                            size: A4 portrait;
                            margin: 8mm 8mm 8mm 8mm;
                        }
                        body {
                            padding: 0;
                            margin: 0;
                            font-size: 11px;
                            line-height: 1.4;
                            color: #000000 !important;
                            background-color: #ffffff !important;
                            -webkit-print-color-adjust: exact;
                            print-color-adjust: exact;
                        }
                        .header-container {
                            border: 1px solid #000000 !important;
                            border-radius: 4px !important;
                            background-color: #fbfbfb !important;
                            padding: 10px !important;
                            margin-bottom: 10px !important;
                            box-shadow: none !important;
                        }
                        .main-title {
                            font-size: 16px !important;
                            color: #000000 !important;
                            border-bottom: 1.2px solid #000000 !important;
                            padding-bottom: 4px !important;
                            margin-bottom: 4px !important;
                        }
                        .meta-grid td {
                            font-size: 11px !important;
                            color: #000000 !important;
                            padding: 3px 4px !important;
                        }
                        .meta-grid td strong {
                            color: #000000 !important;
                        }
                        .section-title {
                            font-size: 12px !important;
                            background-color: #0284c7 !important; /* Sky Blue variant for print as well */
                            color: #ffffff !important;
                            padding: 4px 6px !important;
                            margin-top: 10px !important;
                            margin-bottom: 6px !important;
                            border-radius: 2px !important;
                        }
                        table.report-table {
                            margin-bottom: 10px !important;
                            page-break-inside: avoid;
                        }
                        table.report-table th, table.report-table td {
                            padding: 4px 4px !important;
                            font-size: 10px !important;
                            border: 1px solid #000000 !important;
                        }
                        table.report-table th {
                            background-color: #e0f2fe !important; /* Light sky blue background for table headers */
                            color: #0369a1 !important; /* Dark blue text */
                            border-bottom: 1.5px solid #0284c7 !important;
                        }
                        table.report-table tr {
                            background-color: #ffffff !important;
                        }
                        .cell-highlight {
                            color: #000000 !important;
                            font-weight: bold;
                        }
                        .cell-main {
                            color: #000000 !important;
                        }
                        .cell-index {
                            color: #000000 !important;
                            background-color: #f3f4f6 !important;
                        }
                        .cell-desc {
                            color: #000000 !important;
                            font-size: 9.5px !important;
                        }
                        .memo-container {
                            margin-top: 8px !important;
                            page-break-inside: avoid;
                        }
                        .memo-box {
                            border: 1px solid #000000 !important;
                            border-right: 4px solid #000000 !important;
                            background-color: #ffffff !important;
                            padding: 8px !important;
                            margin-bottom: 6px !important;
                        }
                        .memo-title {
                            color: #000000 !important;
                            font-size: 11px !important;
                            margin-bottom: 2px !important;
                        }
                        .memo-text {
                            color: #000000 !important;
                            font-size: 9.5px !important;
                        }
                        .footer-signatures {
                            margin-top: 18px !important;
                            page-break-inside: avoid;
                        }
                        .footer-signatures td {
                            padding: 6px !important;
                            font-size: 11px !important;
                            color: #000000 !important;
                        }
                        .signature-line {
                            margin-top: 18px !important;
                            border-top: 1px dashed #000000 !important;
                            width: 120px !important;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="header-container">
                    <div class="main-title">$mainReportTitle</div>
                    <table class="meta-grid">
                        <tr>
                            <td style="width: 40%"><strong>نام پروژه:</strong> ${report.project.ifEmpty { "ثبت نشده" }}</td>
                            <td style="width: 30%"><strong>واحد/بخش مربوطه:</strong> ${report.section.ifEmpty { "بخش فنی" }}</td>
                            <td style="width: 30%"><strong>تاریخ گزارش:</strong> ${report.date.ifEmpty { "ثبت نشده" }}</td>
                        </tr>
                        <tr>
                            <td><strong>تهیه کننده گزارش:</strong> ${report.preparedBy.ifEmpty { "ثبت نشده" }}</td>
                            <td><strong>وضعیت جوی هوا:</strong> ${report.weather.ifEmpty { "آفتابی ☀️" }}</td>
                            <td>
                                <strong>محدوده کیلومتراژ:</strong> 
                                ${if (report.startKm.isEmpty() && report.endKm.isEmpty()) "عمومی کارگاه" else "کیلومتر " + formatKmRangeHtml(report.startKm, report.endKm)}
                            </td>
                        </tr>
                    </table>
                </div>

                $bodyContentHtml

                <!-- 4. موانع و مشکلات -->
                <div class="section-title">۳. موانع، مشکلات کارگاهی و پیش‌بینی روز آینده</div>
                <div class="memo-container">
                    <div class="memo-box">
                        <div class="memo-title">⚠️ موانع، مشکلات، نواقص و حوادث کارگاهی:</div>
                        <div class="memo-text">${report.obstacles.ifEmpty { "---" }}</div>
                    </div>
    
                    <div class="memo-box" style="border-right-color: #10b981; background-color: #ecfdf5;">
                        <div class="memo-title" style="color: #047857;">📅 پیش‌بینی فعالیت‌های برنامه‌ریزی شده برای روز آینده:</div>
                        <div class="memo-text" style="color: #064e3b;">${report.tomorrowPlan.ifEmpty { "---" }}</div>
                    </div>
                </div>

                <!-- Signatures -->
                <table class="footer-signatures">
                    <tr>
                        <td>
                            <strong style="font-size: 13px;">تهیه کننده: ${report.preparedBy.ifEmpty { "تنظیم کننده" }}</strong>
                            ${if (signatureBase64.isNotEmpty()) """
                                <div style="height: 75px; text-align: center; display: flex; align-items: center; justify-content: center; margin: 4px auto;">
                                    <img src="data:image/png;base64,$signatureBase64" style="max-height: 70px; max-width: 170px; display: inline-block; mix-blend-mode: multiply;" />
                                </div>
                            """ else """
                                <div class="signature-line"></div>
                            """}
                        </td>
                        <td>
                            <strong style="font-size: 13px;">تایید دفتر فنی و نظارت</strong>
                            <div class="signature-line" style="margin-top: 35px;"></div>
                        </td>
                    </tr>
                </table>
                
                ${generatePhotosPages(context, report, photos)}
            </body>
            </html>
        """.trimIndent()
    }

    private fun generatePhotosPages(context: Context, report: DailyReport, photos: List<com.example.data.model.DailyPhoto>): String {
        if (photos.isEmpty()) return ""

        val pages = photos.take(4).chunked(4)
        return pages.joinToString("") { pagePhotos ->
            val pageHtml = StringBuilder()
            pageHtml.append("<div style=\"page-break-before: always; width: 100%; height: 96%; display: flex; flex-direction: column;\">")
            
            // Header for Photo Page
            pageHtml.append("""
                <div class="header-container" style="margin-bottom: 20px; flex-shrink: 0;">
                    <div class="main-title">پیوست تصاویر گزارش روزانه</div>
                    <table class="meta-grid">
                        <tr>
                            <td><strong>پروژه:</strong> ${report.project.ifEmpty { "---" }}</td>
                            <td><strong>تاریخ:</strong> ${report.date.ifEmpty { "---" }}</td>
                        </tr>
                    </table>
                </div>
            """.trimIndent())

            val containerStyle = "display: grid; gap: 16px; flex-grow: 1; width: 100%; box-sizing: border-box;"
            val gridTemplate = when (pagePhotos.size) {
                1 -> "grid-template-columns: 1fr; grid-template-rows: 1fr;"
                2 -> "grid-template-columns: 1fr; grid-template-rows: 1fr 1fr;"
                3 -> "grid-template-columns: 1fr 1fr; grid-template-rows: 1fr 1fr;"
                else -> "grid-template-columns: 1fr 1fr; grid-template-rows: 1fr 1fr;"
            }
            
            pageHtml.append("<div style=\"$containerStyle $gridTemplate\">")
            
            pagePhotos.forEachIndexed { index, photo ->
                val base64 = uriToBase64(context, photo.uri)
                if (base64.isNotEmpty()) {
                    val itemStyle = if (pagePhotos.size == 3 && index == 0) {
                        "grid-column: 1 / 3; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 2px solid #cbd5e1; padding: 12px; border-radius: 8px; background-color: #f8fafc; overflow: hidden; box-sizing: border-box;"
                    } else {
                        "display: flex; flex-direction: column; align-items: center; justify-content: center; border: 2px solid #cbd5e1; padding: 12px; border-radius: 8px; background-color: #f8fafc; overflow: hidden; box-sizing: border-box;"
                    }
                    
                    pageHtml.append("""
                        <div style="$itemStyle">
                            <div style="flex-grow: 1; width: 100%; height: calc(100% - 28px); display: flex; justify-content: center; align-items: center; overflow: hidden;">
                                <img src="$base64" style="max-width: 100%; max-height: 100%; object-fit: contain; border-radius: 4px;" />
                            </div>
                            <div style="font-weight: bold; font-size: 14px; text-align: center; color: #1e3a8a; height: 20px; line-height: 20px; margin-top: 8px; flex-shrink: 0;">
                                ${photo.description.ifEmpty { "بدون عنوان" }}
                            </div>
                        </div>
                    """.trimIndent())
                }
            }
            pageHtml.append("</div></div>")
            pageHtml.toString()
        }
    }

    // Direct system share function
    fun sharePdfFile(context: Context, pdfFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.civilsync.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "ارسال گزارش روزانه کارگاه"))
    }

    private fun uriToBase64(context: Context, uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (originalBitmap != null) {
                // Resize if needed
                val maxWidth = 1024
                val maxHeight = 1024
                val ratioBitmap = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                var finalWidth = maxWidth
                var finalHeight = maxHeight
                if (ratioBitmap > 1) {
                    finalWidth = maxWidth
                    finalHeight = (maxWidth / ratioBitmap).toInt()
                } else {
                    finalHeight = maxHeight
                    finalWidth = (maxHeight * ratioBitmap).toInt()
                }
                
                // Only scale if the image is larger than max
                val scaledBitmap = if (originalBitmap.width > maxWidth || originalBitmap.height > maxHeight) {
                    android.graphics.Bitmap.createScaledBitmap(originalBitmap, finalWidth, finalHeight, true)
                } else {
                    originalBitmap
                }
                
                val outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                val bytes = outputStream.toByteArray()
                
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatKmRangeHtml(startKm: String, endKm: String): String {
        val cleanStart = startKm.map { char ->
            when (char) {
                '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> char
            }
        }.joinToString("").trim()

        val cleanEnd = endKm.map { char ->
            when (char) {
                '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> char
            }
        }.joinToString("").trim()

        if (cleanStart.isEmpty() && cleanEnd.isEmpty()) return "---"
        if (cleanStart.isEmpty()) return "<span dir=\"ltr\" style=\"direction: ltr; display: inline-block;\">$cleanEnd</span>"
        if (cleanEnd.isEmpty()) return "<span dir=\"ltr\" style=\"direction: ltr; display: inline-block;\">$cleanStart</span>"
        return "<span dir=\"ltr\" style=\"direction: ltr; display: inline-block;\">$cleanStart</span> الی <span dir=\"ltr\" style=\"direction: ltr; display: inline-block;\">$cleanEnd</span>"
    }
}
