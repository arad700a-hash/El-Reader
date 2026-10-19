# راهنمای ساخت (Build)

> هشدار صادقانه: این پروژه هنوز در هیچ محیطی کامپایل نشده است (محیط نگارنده SDK اندروید و دسترسی به Maven/Google نداشت).
> اولین ساخت ممکن است خطاهای کامپایل داشته باشد؛ احتمال‌های شناخته‌شده در `TEST_REPORT.md` بخش «ریسک‌های کامپایل» آمده است.

## نسخه‌های هماهنگ (مهم)
| ابزار | نسخه |
|---|---|
| JDK | 17 |
| Gradle (Wrapper) | 8.11.1 |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.3.20 |
| KSP | 2.3.4 |
| Room | 2.8.4 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 26 |
| Readium Kotlin Toolkit | 3.2.0 |

**ریشهٔ خطای `:app:kspDebugKotlin` در AndroidForge (از لاگ‌های واقعی):** Readium 3.2.0 و وابستگی‌هایش (kotlin-stdlib 2.3.x،
kotlinx-serialization 1.10) با متادیتای Kotlin 2.3.0 کامپایل شده‌اند، ولی پروژه Kotlin/KSP 2.0.21 داشت که فقط تا متادیتای ~2.1 را می‌خواند؛
KSP هنگام خواندن فایل‌های `kotlin_module` شکست می‌خورد. به همین دلیل `assembleDebug`، `assembleRelease` و `bundleDebug` همه یک‌جا شکست خوردند.
مراحل منابع، مانیفست، AAR metadata و desugaring در همان لاگ موفق بودند، پس AGP 8.9.1 / compileSdk 36 مشکلی نداشتند.
اصلاح: Kotlin/KSP/Room به نسخه‌های بالا ارتقا یافت و DSL قدیمی `android { kotlinOptions {} }` (حذف‌شده در Kotlin Gradle Plugin 2.2+)
به `kotlin { compilerOptions {} }` تغییر کرد. **این اصلاح هنوز با یک بیلد واقعی تأیید نشده است** (محیط نگارنده به Maven/Google دسترسی ندارد).
اگر AndroidForge باز هم خطا داد، متن خطای جدید را بفرستید.

## پیش‌نیازها
- Android Studio (نسخه‌ای که AGP 8.9.1 را پشتیبانی کند؛ Meerkat 2024.3.1 یا جدیدتر) یا JDK 17 + Android SDK (**platform 36**، build-tools)
- Gradle 8.11.1 (از طریق Gradle Wrapper همراه پروژه؛ حداقل لازم برای AGP 8.9.1)
- دسترسی به `mavenCentral()` و `google()`؛ Readium از mavenCentral با گروه `org.readium.kotlin-toolkit` می‌آید.

## ساخت با Android Studio
1. `File ▸ Open` و پوشهٔ `NovelReader` را باز کنید. Studio خودش Gradle را همگام می‌کند.
2. Gradle Wrapper (`gradlew`، نسخهٔ 8.11.1) در پروژه هست و نیازی به نصب جداگانهٔ Gradle نیست.
3. `Build ▸ Build APK(s)` یا اجرای مستقیم روی گوشی/شبیه‌ساز (minSdk 26).

## ساخت با خط فرمان
```
./gradlew :app:testDebugUnitTest     # تست‌های JVM (منطق خالص)
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
```

## نسخهٔ Release امضاشده
1. ساخت کلید (یک بار؛ کلید و رمز را امن نگه دارید، هرگز در مخزن نگذارید):
   ```
   keytool -genkeypair -v -keystore novelreader.jks -alias novelreader -keyalg RSA -keysize 2048 -validity 10000
   ```
2. تنظیم متغیرهای محیطی:
   ```
   export NR_KEYSTORE_PATH=/path/novelreader.jks
   export NR_KEYSTORE_PASSWORD=...
   export NR_KEY_ALIAS=novelreader
   export NR_KEY_PASSWORD=...
   ./gradlew :app:assembleRelease
   ```
3. خروجی: `app/build/outputs/apk/release/app-release.apk`. اگر متغیرها تنظیم نباشند، فایل **بدون امضا** تولید می‌شود و قابل نصب نیست.
4. Release با `minify`/`shrinkResources` ساخته می‌شود؛ قوانین در `app/proguard-rules.pro` هنوز **آزمایش‌نشده** است. اگر برنامه در Release کرش کرد
   (به‌ویژه Readium/Room/Coil)، موقتاً `isMinifyEnabled=false` کنید و قوانین keep را کامل کنید.

## تغییر نام، آیکون، شناسهٔ بسته
در `README.md` توضیح داده شده است.

## CI
`.github/workflows/build.yml` تست‌های واحد و `assembleDebug` را اجرا و APK را آپلود می‌کند (اجرا نشده).
