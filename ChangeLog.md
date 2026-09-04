# Changelog

All notable changes to the **NajiAether** project will be documented in this file.

---

## [1.2.0] — 2026-09-04

### ✨ New Features
* **Endpoint Scanner Added:** A dedicated tool to discover healthy endpoints across 4 protocols (`MASQUE`, `MASQUE-H2`, `WireGuard`, and `AmneziaWG`). Features IPv4/IPv6 and port filters with one-tap configuration to apply settings and connect directly from search results.

### ⚡ Core Engine Upgrade
* **Core Engine Upgraded to v1.8.0:** Updated to Aether Core v1.8.0, supporting advanced routing rules (`--route-block` & `--route-direct`) by domain, IP, and port, optimized endpoint scanning sequences with Zero Trust range support, and resilient registration fallbacks with TLS fingerprint rotation for censored networks.

### 📱 UI & UX Improvements
* **Advanced Settings Redesign:** Streamlined UI for smoother navigation, added `MASQUE-H2` protocol support inside Advanced Settings, and optimized endpoint options with direct quick links to Endpoint Scanner and Endpoint History.

### 🐛 Bug Fixes & Stability
* **Tunnel DNS Bug Fix:** Resolved an issue where `DNS inside the tunnel` was not properly applied, ensuring accurate and reliable DNS routing.

---

## [1.1.1] — 2026-09-02

### 📱 UI & UX Improvements
* **Redesigned Connection Info Card:** Upgraded the main connection status card layout with a modern, clean visual representation to enhance overall user experience.
* **Complete Redesign of Usage Calendar:**
  * Accurate real-time tracking and display of data traffic used while using the application.
  * Dual-calendar support: Automatically displays the **Jalali (Persian) calendar** for Persian language and the **Gregorian calendar** for English language.
  * Categorized usage views aggregated by **Daily**, **Weekly**, and **Monthly** intervals.
  * Detailed traffic breakdown categorized by **Download**, **Upload**, and **Active Sessions**.

### ✨ New Features
* **New Advanced Home Screen Widget (4x2 Size):**
  * Built a rich, full-featured widget for quick home screen controls and monitoring.
  * Real-time **Download & Upload speed** meters.
  * Dedicated **Toggle Connection** button.
  * Display of current **IP Address** alongside the **Country Name & Flag icon**.
  * Live **Connection Duration Timer**.
* **Endpoint History & Quick Reconnect:**
  * Automatic logging and persistence of endpoint parameters after each successful connection.
  * Real-time display of **Ping** metrics and the **Network Type** used (Cellular / Wi-Fi).
  * **One-Tap Quick Reconnect:** Tap any saved endpoint entry to instantly re-establish a connection with its preserved profile configurations.
* **Background Keep-Alive & Battery Optimization Prompt:**
  * Smart prompt triggered on the initial VPN connection guiding users to prevent system background kills when the screen locks.
  * Integrated direct button navigation to device battery optimization settings to ensure uninterrupted background connectivity.

### 🐛 Bug Fixes & Stability
* **Fixed 3x1 Widget Crash Bug:** Resolved the application crash issue that occurred when tapping on the existing 3x1 home screen widget.
* **Fixed Unhandled ProfileCodec Fields:**
  * Resolved data serialization gaps within `ProfileCodec` in `AetherController.kt`.
  * Fully mapped missing `ConnectionProfile` fields (`dnsServers`, `team`, `teamAuth`, `gateway`, `routeBlock`, `routeDirect`, `upstreamProxy`, `routeSniff`, `routeSniffMs`, and `autoReprovision`) when parsing profiles down to `VpnService`, ensuring all UI settings are accurately reflected in active runtime tunnels.

---

## [1.1.0] — 2026-08-15

### 📱 UI & UX Improvements
* **Theme Support Expansion:** Added Light Mode and System Default theme matching alongside the existing Dark Mode.
* **Embedded In-App Changelog:** Added an integrated changelog viewer allowing users to inspect release notes directly within the app.

### ✨ New Features
* **Bandwidth Usage Graph:** Added a real-time graph component for monitoring live download and upload speeds.
* **Usage Calendar (Initial Release):** Integrated a visual daily usage calendar for tracking monthly internet data consumption.

### 🐛 Bug Fixes & Stability
* **Diagnostics Scroll Fix:** Resolved a UI scrolling issue in the Debug and Diagnostics section.

---

<div dir="rtl">

## [1.2.0] — ۱۴۰۵/۰۶/۱۴ (نسخه فارسی)

### ✨ قابلیت‌های جدید
* **افزودن اسکنر اندپوینت (Endpoint Scanner):** یک ابزار اختصاصی جهت یافتن اندپوینت‌های سالم برای ۴ پروتکل (`MASQUE` ،`MASQUE-H2` ،`WireGuard` و `AmneziaWG`). دارای فیلتر نوع آی‌پی (IPv4/IPv6) و پورت همراه با قابلیت پیکربندی و اتصال مستقیم از نتایج جستجو تنها با یک لمس.

### ⚡ ارتقای هسته اصلی (Core Engine Upgrade)
* **ارتقای هسته اصلی به نسخه v1.8.0:** به‌روزرسانی به هسته Aether Core v1.8.0 با پشتیبانی از قواعد مسیریابی پیشرفته (`--route-block` و `--route-direct`) بر اساس دامنه، IP و پورت، بهینه‌سازی ترتیب اسکن اندپوینت با پشتیبانی از رنج‌های Zero Trust، و مکانیزم ثبت‌نام مقاوم همراه با چرخش TLS Fingerprint برای شبکه‌های دارای اختلال شدید.

### 📱 تغییرات و بهبودهای رابط کاربری (UI & UX)
* **بازطراحی بخش تنظیمات پیشرفته (Advanced Settings):** بهینه‌سازی رابط کاربری برای پیمایش آسان‌تر، اضافه شدن پشتیبانی از پروتکل `MASQUE-H2` در تنظیمات پیشرفته و بهینه‌سازی گزینه‌های اندپوینت با جایگزینی لینک‌های مستقیم و سریع به اسکنر و تاریخچه اندپوینت.

### 🐛 رفع باگ‌ها و افزایش پایداری
* **رفع باگ DNS داخل تونل:** برطرف شدن مشکل عدم اعمال صحیح `DNS inside the tunnel` و تضمین مسیریابی دقیق و پایدار DNS.

---

## [1.1.1] — ۱۴۰۵/۰۶/۱۲ (نسخه فارسی)

### 📱 تغییرات و بهبودهای رابط کاربری (UI & UX)
* **بازطراحی کارت اطلاعات اتصال:** بهبود ساختار بصری و مدرن‌سازی کارت نمایش وضعیت اتصال در صفحه اصلی برای ارتقای تجربه کاربری.
* **بازطراحی کامل بخش تقویم مصرف (Usage Calendar):**
  * نمایش دقیق میزان ترافیک مصرفی اینترنت هنگام استفاده از برنامه.
  * پشتیبانی هوشمند از **تقویم شمسی** برای زبان فارسی و **تقویم میلادی** برای زبان انگلیسی.
  * دسته‌بندی و فیلتر گزارش‌ها در بازه‌های **روزانه، هفتگی و ماهانه**.
  * تفکیک داده‌ها به صورت **دانلود، آپلود و نشست‌ها (Sessions)**.

### ✨ قابلیت‌های جدید
* **افزودن ویجت پیشرفته جدید (سایز ۴x۲):**
  * طراحی ویجت کامل‌تر و حرفه‌ای‌تر برای صفحه اصلی دستگاه.
  * نمایش زنده سرعت **دانلود و آپلود**.
  * دکمه اختصاصی **قطع/وصل اتصال**.
  * نمایش **آدرس IP** به همراه **نام و آیکون پرچم کشور**.
  * نمایش **تایمر مدت زمان اتصال**.
* **قابلیت Endpoint History (تاریخچه اندپوینت‌ها):**
  * ذخیره‌سازی خودکار مشخصات اندپوینت پس از هر اتصال موفق.
  * نمایش پینگ (Ping) و نوع شبکه متصل‌شده (داده موبایل / وای‌فای).
  * امکان **اتصال سریع (Quick Reconnect)** تنها با لمس اندپوینت مورد نظر و اعمال مجدد تنظیمات قبلی.
* **مدیریت پایداری اجرای برنامه در پس‌زمینه (Background Keep-Alive):**
  * درخواست هوشمند از کاربر هنگام اولین اتصال برای جلوگیری از بسته شدن خودکار برنامه توسط سیستم‌عامل در زمان قفل شدن صفحه.
  * اضافه شدن دکمه اختصاصی جهت هدایت مستقیم کاربر به تنظیمات بهینه‌سازی باتری دستگاه (Disable Battery Optimization).

### 🐛 رفع باگ‌ها و افزایش پایداری
* **رفع باگ کرش ویجت سایز ۳x۱:** برطرف کردن مشکل متوقف شدن ناگهانی (Crash) برنامه هنگام لمس یا کلیک روی ویجت قدیمی ۳x۱.
* **رفع باگ عدم همگام‌سازی فیلدها در ProfileCodec:**
  * اصلاح کامل متدهای Encode و Decode در کلاس `AetherController.kt`.
  * اضافه شدن فیلدهای جامانده شامل `dnsServers` ،`team` ،`teamAuth` ،`gateway` ،`routeBlock` ،`routeDirect` ،`upstreamProxy` ،`routeSniff` ،`routeSniffMs` و `autoReprovision` به فرایند انتقال داده به `VpnService` جهت اطمینان از اعمال دقیق تمام تنظیمات UI در اتصال واقعی.

---

## [1.1.0] — ۱۴۰۵/۰۵/۲۵ (نسخه فارسی)

### 📱 تغییرات و بهبودهای رابط کاربری (UI & UX)
* **پوسته و تم:** اضافه شدن پوسته حالت روشن (Light Mode) و قابلیت تنظیم بر اساس حالت سیستم (System Default).
* **تغییرات نسخه در برنامه (Changelog):** اضافه شدن بخش تغییرات نسخه در درون برنامه جهت مشاهده جدیدترین قابلیت‌ها و به‌روزرسانی‌ها توسط کاربر.

### ✨ قابلیت‌های جدید
* **بخش نمودار مصرف (گراف):** اضافه شدن نمودار مصرف لحظه‌ای برای مشاهده سرعت دانلود و آپلود در لحظه.
* **تقویم میزان مصرف (نسخه اولیه):** امکان مشاهده و بررسی میزان مصرف روزانه اینترنت در طول ماه در قالب تقویم.

### 🐛 رفع باگ‌ها و افزایش پایداری
* **رفع باگ عیب‌یابی:** برطرف شدن مشکل عدم اسکرول در بخش دیباگ و عیب‌یابی.

</div>