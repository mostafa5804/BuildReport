package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.DailyReport
import java.io.File

object PdfGenerator {
    // Generate HTML String from DailyReport
    fun generateHtmlReport(report: DailyReport, signatureBase64: String = "", customUnitTitle: String = "سایر واحدها"): String {
        val rType = report.reportType
        
        // Base title based on reportType
        val mainReportTitle = when (rType) {
            "WAREHOUSE" -> "گزارش روزانه ورود و خروج مصالح انبار"
            "LEGAL" -> "گزارش روزانه امور حقوقی و تحصیل اراضی"
            "SURVEY" -> "گزارش روزانه عملیات نقشه‌برداری کارگاه"
            "TECHNICAL" -> "گزارش روزانه دفتر فنی کارگاه"
            "HSE" -> "گزارش روزانه واحد ایمنی HSE کارگاه"
            "CUSTOM" -> "گزارش روزانه واحد $customUnitTitle"
            else -> "گزارش روزانه فعالیت‌های فنی و اجرایی کارگاه"
        }

        // Shared machinery rows helper
        val machineryRows = if (report.machinery.isEmpty()) {
            "<tr><td colspan='6' class='text-muted'>هیچ ماشین‌آلاتی برای این روز ثبت نشده است</td></tr>"
        } else {
            report.machinery.mapIndexed { index, machinery ->
                """
                <tr>
                    <td class="cell-index">${index + 1}</td>
                    <td class="cell-main">${machinery.type}</td>
                    <td>${machinery.activeCount}</td>
                    <td>${machinery.inactiveCount}</td>
                    <td>${machinery.workingHours.ifEmpty { "---" }}</td>
                    <td class="cell-desc">${machinery.comments.ifEmpty { "بدون اشکال / فعال" }}</td>
                </tr>
                """.trimIndent()
            }.joinToString("")
        }

        // Shared manpower rows helper
        val manpowerRows = if (report.manpower.isEmpty()) {
            "<tr><td colspan='5' class='text-muted'>هیچ آمار نیروی کارگری ثبت نشده است</td></tr>"
        } else {
            report.manpower.mapIndexed { index, manpower ->
                val empTypeLabel = if (manpower.employmentType == "SUBCONTRACTOR") {
                    if (manpower.subcontractorName.isNotEmpty()) "پیمانکار: ${manpower.subcontractorName}" else "پیمانکار دست دوم"
                } else {
                    "نیروی شرکت"
                }
                val isLeave = manpower.isOnLeave
                val leaveText = if (isLeave) " <span style='color: #dc2626; font-weight: bold;'>(در مرخصی ✈️)</span>" else ""
                val responsiblePerson = if (manpower.name.isNotEmpty()) {
                    "${manpower.name}$leaveText ($empTypeLabel)"
                } else {
                    "اکیپ فعال$leaveText ($empTypeLabel)"
                }
                val countText = if (isLeave) "<span style='color: #dc2626;'>۰ نفر (مرخصی)</span>" else "${manpower.count} نفر"
                """
                <tr>
                    <td class="cell-index">${index + 1}</td>
                    <td class="cell-main" style="${if (isLeave) "color: #9ca3af; text-decoration: line-through;" else ""}">${manpower.role}</td>
                    <td style="${if (isLeave) "color: #4b5563;" else ""}">$responsiblePerson</td>
                    <td>$countText</td>
                    <td class="cell-desc">${manpower.comments.ifEmpty { "---" }}</td>
                </tr>
                """.trimIndent()
            }.joinToString("")
        }

        var bodyContentHtml = ""

        when (rType) {
            "WAREHOUSE" -> {
                // Split materials into incoming (entries) and outgoing (exits)
                val entriesList = report.materials.filter { !it.isExit }
                val exitsList = report.materials.filter { it.isExit }

                val entriesRowsHtml = if (entriesList.isEmpty()) {
                    "<tr><td colspan='7' class='text-muted'>هیچ مصالح وارده‌ای ثبت نشده است</td></tr>"
                } else {
                    entriesList.mapIndexed { index, entry ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${entry.type}</td>
                            <td>${entry.quantity}</td>
                            <td>${entry.unit}</td>
                            <td>${entry.loadingLocation.ifEmpty { "نامشخص/مبداء" }}</td>
                            <td>${entry.unloadingLocation.ifEmpty { "انبار اصلی" }}</td>
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
                            <td>${exit.receiver.ifEmpty { "سرپرست/اکیپ اجرایی" }}</td>
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
                                <th style="width: 12%;">مقدار ورودی</th>
                                <th style="width: 10%;">واحد</th>
                                <th style="width: 15%;">تامین کننده / مبدا بارگیری</th>
                                <th style="width: 15%;">مبلغ دپو / محل تخلیه</th>
                                <th style="width: 18%;">ملاحظات و وضعیت ظاهری</th>
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
                                <th style="width: 23%;">مورد مصرف و توضیحات</th>
                            </tr>
                        </thead>
                        <tbody>
                            $exitsRowsHtml
                        </tbody>
                    </table>

                    <!-- 3. ماشین‌آلات انبار -->
                    <div class="section-title">۳. وضعیت ماشین‌آلات و تجهیزات لجستیکی انبار</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نام و مدل تجهیزات</th>
                                <th style="width: 12%;">فعال</th>
                                <th style="width: 12%;">غیر فعال</th>
                                <th style="width: 15%;">ساعت کارکرد</th>
                                <th style="width: 21%;">توضیحات و وضعیت</th>
                            </tr>
                        </thead>
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی انبار -->
                    <div class="section-title">۴. آمار پرسنل و نیروهای شاغل در انبارداری</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">سمت / تخصص</th>
                                <th style="width: 25%;">نام سرپرست/مسئول</th>
                                <th style="width: 15%;">تعداد حاضر</th>
                                <th style="width: 20%;">توضیحات کارکرد</th>
                            </tr>
                        </thead>
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

                // Inquiries & Permits list stored in 'materials'
                val inquiriesRows = if (report.materials.isEmpty()) {
                    "<tr><td colspan='4' class='text-muted'>هیچ استعلام یا مجوز قانونی در این روز ثبت نگردیده است</td></tr>"
                } else {
                    report.materials.mapIndexed { index, item ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${item.type}</td>
                            <td><strong>${item.receiver.ifEmpty { "سازمان مربوطه" }}</strong></td>
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
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نام و مدل تجهیزات</th>
                                <th style="width: 12%;">فعال</th>
                                <th style="width: 12%;">غیر فعال</th>
                                <th style="width: 15%;">ساعت کارکرد</th>
                                <th style="width: 21%;">توضیحات</th>
                            </tr>
                        </thead>
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی -->
                    <div class="section-title">۴. مشاورین، کارشناسان رسمی و پرسنل پیگیری حقوقی اراضی</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">سمت / تخصص</th>
                                <th style="width: 25%;">نام پزشک/کارشناس/مسئول</th>
                                <th style="width: 15%;">تعداد حاضر</th>
                                <th style="width: 20%;">شرح فعالیت موضوعی امروز</th>
                            </tr>
                        </thead>
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
                            <td class="cell-desc">${task.comments.ifEmpty { "خروجی نقشه انجام شد" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                // Surveying Tomorrow forecast (saved in Materials)
                val forecastRows = if (report.materials.isEmpty()) {
                    "<tr><td colspan='7' class='text-muted'>هیچ برنامه‌ای برای پیش‌بینی عملیات نقشه‌برداری روز بعد ثبت نگردیده است</td></tr>"
                } else {
                    report.materials.mapIndexed { index, item ->
                        """
                        <tr>
                            <td class="cell-index">${index + 1}</td>
                            <td class="cell-main">${item.type}</td>
                            <td>${if (item.loadingLocation.isEmpty() && item.unloadingLocation.isEmpty()) "سرتا‌سر کارگاه" else item.loadingLocation + " الی " + item.unloadingLocation}</td>
                            <td class="cell-highlight">${item.quantity}</td>
                            <td>${item.unit}</td>
                            <td>${item.count}</td>
                            <td class="cell-desc">${item.comments.ifEmpty { "---" }}</td>
                        </tr>
                        """.trimIndent()
                    }.joinToString("")
                }

                bodyContentHtml = """
                    <!-- 1. کارهای انجام شده -->
                    <div class="section-title">۱. خلاصه آمار عملیات‌های نقشه‌برداری، شیت‌ها و پیاده‌سازی گره‌ها</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">شرح دقیق عملیات نقشه‌برداری / برداشت / پیاده‌سازی</th>
                                <th style="width: 15%;">موقعیت / کیلومتر ابتدا-انتها</th>
                                <th style="width: 10%;">کارکرد امروز</th>
                                <th style="width: 8%;">واحد</th>
                                <th style="width: 12%;">حجم کل تجمیعی</th>
                                <th style="width: 15%;">ملاحظات و وضعیت تحویل فنی</th>
                            </tr>
                        </thead>
                        <tbody>
                            $surveyRows
                        </tbody>
                    </table>

                    <!-- 2. پیش بینی روز آینده -->
                    <div class="section-title">۲. پیش‌بینی و برنامه‌ریزی عملیات نقشه‌برداری برای روز آینده</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">شرح برنامه‌ریزی برداشت و نقاط هدف روز بعد</th>
                                <th style="width: 15%;">محدوده فرضی / کیلومتر ابتدا-انتها</th>
                                <th style="width: 10%;">کارکرد برآورد</th>
                                <th style="width: 8%;">واحد</th>
                                <th style="width: 12%;">حجم کل تجمیعی برآورد</th>
                                <th style="width: 15%;">ملاحظات پیش‌نیاز فنی</th>
                            </tr>
                        </thead>
                        <tbody>
                            $forecastRows
                        </tbody>
                    </table>

                    <!-- 3. ماشین‌آلات -->
                    <div class="section-title">۳. وضعیت دوربین‌ها، پهپادها، تجهیزات GPS و ماشین ابزار نقشه‌برداری</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نام و مدل تجهیزات سنجش و دوربین‌ها</th>
                                <th style="width: 12%;">فعال</th>
                                <th style="width: 12%;">نیمه‌فعال یا کالیبره زمان‌دار</th>
                                <th style="width: 15%;">ساعت کارکرد سنج</th>
                                <th style="width: 21%;">توضیحات و تراز سنج</th>
                            </tr>
                        </thead>
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی -->
                    <div class="section-title">۴. آمار مهندسین نقشه‌بردار، کمک‌ نقشه‌برداران و میرداران حاضر در کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">سمت / تخصص اکیپ نقشه‌برداری</th>
                                <th style="width: 25%;">نام سرپرست اکیپ / شیت‌نویس</th>
                                <th style="width: 15%;">تعداد حاضر</th>
                                <th style="width: 20%;">توضیحات کارکرد نقاط موضوعی</th>
                            </tr>
                        </thead>
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
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نام و مدل تجهیزات</th>
                                <th style="width: 12%;">فعال</th>
                                <th style="width: 12%;">غیر فعال</th>
                                <th style="width: 15%;">ساعت کارکرد</th>
                                <th style="width: 21%;">توضیحات و وضعیت</th>
                            </tr>
                        </thead>
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی ایمنی -->
                    <div class="section-title">۴. آمار افسران ایمنی، پزشک‌یار و پرسنل بخش HSE</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">سمت / تخصص</th>
                                <th style="width: 25%;">نام سرپرست/مسئول تکنسین</th>
                                <th style="width: 15%;">تعداد حاضر</th>
                                <th style="width: 20%;">توضیحات کارکرد پرسنل</th>
                            </tr>
                        </thead>
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
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نام و مدل تجهیزات</th>
                                <th style="width: 12%;">فعال</th>
                                <th style="width: 12%;">غیر فعال</th>
                                <th style="width: 15%;">ساعت کارکرد</th>
                                <th style="width: 21%;">توضیحات و وضعیت</th>
                            </tr>
                        </thead>
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی -->
                    <div class="section-title">۴. آمار پرسنل و نیروهای فعال واحد $customUnitTitle</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">سمت / تخصص</th>
                                <th style="width: 25%;">نام سرپرست/مسئول مربوطه</th>
                                <th style="width: 15%;">تعداد حاضر</th>
                                <th style="width: 20%;">توضیحات کارکرد پرسنل</th>
                            </tr>
                        </thead>
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
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نام و مدل تجهیزات</th>
                                <th style="width: 12%;">فعال</th>
                                <th style="width: 12%;">غیر فعال</th>
                                <th style="width: 15%;">ساعت کارکرد</th>
                                <th style="width: 21%;">توضیحات و وضعیت</th>
                            </tr>
                        </thead>
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 4. نیروی انسانی -->
                    <div class="section-title">۴. آمار مهندسان، کارشناسان و پرسنل بخش دفتر فنی و کنترل پروژه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">سمت / تخصص</th>
                                <th style="width: 25%;">نام سرپرست/مسئول تکنسین</th>
                                <th style="width: 15%;">تعداد حاضر</th>
                                <th style="width: 20%;">توضیحات کارکرد پرسنل</th>
                            </tr>
                        </thead>
                        <tbody>
                            $manpowerRows
                        </tbody>
                    </table>
                """.trimIndent()
            }
            else -> {
                // Execution report layout
                val tasksRows = if (report.tasks.isEmpty()) {
                    "<tr><td colspan='6' class='text-muted'>هیچ فعالیت فنی و اجرایی در این تاریخ ثبت نشده است</td></tr>"
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

                bodyContentHtml = """
                    <!-- 1. شرح کارها -->
                    <div class="section-title">۱. خلاصه شرح کار و فعالیت‌های فنی و اجرایی کارگاه</div>
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
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">نام و مدل تجهیزات</th>
                                <th style="width: 12%;">تعداد فعال</th>
                                <th style="width: 12%;">تعداد خراب/متوقف</th>
                                <th style="width: 15%;">ساعت کارکرد</th>
                                <th style="width: 21%;">توضیحات و وضعیت</th>
                            </tr>
                        </thead>
                        <tbody>
                            $machineryRows
                        </tbody>
                    </table>

                    <!-- 3. نیروی انسانی -->
                    <div class="section-title">۳. آمار نیروی انسانی فنی، اجرایی و کارگری شاغل در کارگاه</div>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th style="width: 5%;">ردیف</th>
                                <th style="width: 35%;">سمت / تخصص نیروی کار</th>
                                <th style="width: 25%;">نام سرپرست/مسئول اکیپ</th>
                                <th style="width: 15%;">تعداد حاضر</th>
                                <th style="width: 20%;">شرح کارکرد/وضعیت اکیپ</th>
                            </tr>
                        </thead>
                        <tbody>
                            $manpowerRows
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
                        background-color: #1e3a8a;
                        padding: 6px 12px;
                        margin-top: 16px;
                        margin-bottom: 8px;
                        border-radius: 4px;
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
                            background-color: #333333 !important;
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
                            background-color: #e5e7eb !important;
                            color: #000000 !important;
                            border-bottom: 1.5px solid #000000 !important;
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
                        <div class="memo-text">${report.obstacles.ifEmpty { "موردی گزارش نشده است و عملیات طبق برنامه پیش رفته است." }}</div>
                    </div>
    
                    <div class="memo-box" style="border-right-color: #10b981; background-color: #ecfdf5;">
                        <div class="memo-title" style="color: #047857;">📅 پیش‌بینی فعالیت‌های برنامه‌ریزی شده برای روز آینده:</div>
                        <div class="memo-text" style="color: #064e3b;">${report.tomorrowPlan.ifEmpty { "موردی ثبت نشده است." }}</div>
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
            </body>
            </html>
        """.trimIndent()
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
