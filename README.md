# BuildReportYar - سامانه گزارش یار کارگاه 🏗️📝

An offline-first, professional daily reporting & monitoring assistant for construction, infrastructure, Civil Engineering, and warehouse management. Built entirely using modern Android technologies (Jetpack Compose, Room DB, Material Design 3, and PDF Generation).

یک ابزار آفلاین، مدرن و مهندسی برای ثبت، مدیریت و تولید گزارش‌های روزانه کارگاه‌های ساختمانی، عمرانی و فیلد راهسازی همراه با خروجی استاندارد PDF و قابلیت‌های هوشمند ثبت داده‌ها.

---

## 🚀 Key Features | قابلیت‌های کلیدی

### 🔹 Intelligent Station & Kilometer Formatting (فرمت هوشمند کیلومتراژ)
* **Automatic Detection**: Easily key in mileage values like `250` or `30`, and the app automatically pads and converts them to standard road-construction format (e.g., `0+250` or `0+030`).
* **Format Conversion**: Converts raw numbers or partial stationing seamlessly into standard $K+M$ format.
* **Smart PDF Rendering**: Displays ranges in the precise visual order side-by-side (`14+500 الی 14+650`) without character-flapping or RTL truncation problems, using specialized HTML inline direction handlers.

### ✍️ Hand-Off Ready PDF Reports (خروجی رسمی PDF)
* Generates standardized, visually stunning PDF reports offline.
* Support for uploading and burning **Electronic Signatures (امضای الکترونیک)** onto the sheets instantly.
* Weather integration, mileage, activity logs, machinery delays, workforce categories, and warehouse logs directly exported to highly polished tables.

### 📱 Responsive Material 3 Interface (رابط کاربری مدرن متریال ۳)
* Visual KPIs on a dedicated dashboard featuring daily averages and activity breakdowns.
* Beautiful adaptive columns, customized RTL flows, and localized Persian typography.
* Zero-scrolling "About" panel optimized for standard mobile screen sizes.

### 🛡️ Secure & Offline-First (کاملاً آفلاین و امن)
* **Room SQLite database** guarantees offline data protection.
* Secure backup system to export/import reports via encrypted JSON directly to email or local storage.
* Zero external backend dependency, keeping your project and structural data completely confidential.

---

## 🛠️ Built With | تکنولوژی‌ها

* **Language**: Kotlin 1.9+
* **UI Toolkit**: Jetpack Compose (Material Design 3 Components)
* **Database**: SQLite powered by Room DB
* **Navigation**: Type-safe Jetpack Navigation Compose
* **Document Engine**: Custom Android WebKit-based offline PDF engine
* **Threading**: Kotlin Coroutines & StateFlow

---

## 📖 Persian Overview | راهنمای فارسی سیستم

### بخش‌های اصلی برنامه
1. **داشبورد هوشمند (Dashboard)**: نمایه گرافیکی و آماری برای مشاهده میانگین پرسنل فعال روزانه، مصالح تایید شده، و کاراکتر کلی عملکرد.
2. **برگه ثبت گزارش جدید**: ثبت دقیق پرسنل کار و حضور غیاب، مصالح وارده/صادره، ماشین آلات فنی فعال یا کالیبره، و فعالیت‌های موضعی (کیلومتراژ جاده یا ابنیه فنی).
3. **آرشیو و جستجو**: جستجو سریع بر اساس تاریخ، وضعیت تایید، نام استادکار یا نوع متریال با فیلترگذاری پویا.
4. **درباره برنامه**: صفحه سبک و ساده مجهز به راه ارتباطی سریع با پشتیبانی و نسخه جاری پروژه بدون شلوغی و نیاز به اسکرول.

---

## 📦 How To Build & Install | راهنمای کامپایل پروژه

To build your own version of **BuildReportYar**, follow these simple steps:

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/BuildReportYar.git
   ```
2. Open the project root folder inside **Android Studio (Hedgehog or higher)**.
3. Wait for the Gradle sync to finish successfully.
4. Run standard build or assemble release:
   ```bash
   ./gradlew assembleDebug
   ```

---

## ✉️ Feedback & Contact | ارتباط با ما

We're always excited to improve BuildReportYar! If you have any suggestions, bug reports, or build feedbacks, please feel free to send an email to:
* **Developer Email**: `mostafa5804@gmail.com`
