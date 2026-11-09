# 📱💸 راصد مدفوعات SMS السحابي (Android App + Supabase Realtime)

نظام متكامل لالتقاط رسائل التحويلات المالية (مثل فودافون كاش) من هواتف المبيعات عبر تطبيق أندرويد مخصص وإرسالها لحظياً إلى قاعدة بيانات **Supabase (PostgreSQL + Realtime)** مع لوحة تحكم ويب عصرية.

---

## 📂 هيكل المشروع

```text
sms-payment-supabase/
├── .github/workflows/
│   └── build-apk.yml          # بناء ملف الـ APK تلقائياً عبر GitHub Actions
├── android-app/               # كود تطبيق الأندرويد (Kotlin)
│   └── app/src/main/java/com/elkheta/smsmonitor/
│       ├── MainActivity.kt        # شاشة إعداد بيانات Supabase وسجل الرسائل
│       ├── SmsReceiver.kt         # التقاط رسائل الـ SMS حتى لو كان التطبيق مغلقاً
│       ├── SmsMonitorService.kt   # خدمة خلفية دائمة (Foreground Service)
│       ├── BootReceiver.kt        # تشغيل الخدمة تلقائياً عند إعادة تشغيل الهاتف
│       └── SupabaseSyncHelper.kt  # استخراج الـ Regex، الإرسال لـ Supabase، وطابور Offline
├── supabase/
│   └── schema.sql             # كود SQL لإنشاء الجدول ودالة RPC وتفعيل Realtime في Supabase
└── dashboard/
    └── index.html             # لوحة التحكم الأمامية المتصلة لحظياً بـ Supabase Realtime
```

---

## 🚀 خطوات التشغيل الكاملة (3 خطوات فقط)

### الخطوة 1: إعداد قاعدة البيانات على Supabase
1. أنشئ مشروعاً جديداً مجانياً على [Supabase](https://supabase.com).
2. افتح قسم **SQL Editor** في لوحة تحكم Supabase، وانسخ محتوى ملف [`supabase/schema.sql`](./supabase/schema.sql) بالكامل ثم اضغط **Run**.
3. من قسم **Project Settings -> API**، انسخ كلاً من:
   - **`Project URL`**
   - **`anon` / `public` Key**

### الخطوة 2: رفع الكود على GitHub واستخراج ملف الـ `APK`
1. ارفع هذا المجلد إلى مستودع (Repository) على حسابك في **GitHub**.
2. بمجرد الرفع، ادخل إلى تبويب **Actions** داخل مستودعك في GitHub.
3. ستجد عملية البناء **`Build Android APK & Deploy Dashboard`** تعمل تلقائياً، وخلال دقيقتين ستجد ملف **`SMS-Payment-Monitor-APK`** جاهزاً للتحميل تحت قسم **Artifacts**.
4. حمّل ملف `SMS-Payment-Monitor.apk` وثبته على تليفونات المبيعات، ثم افتح التطبيق وأدخل `Project URL` و `Anon Key` ومعرف الهاتف (`Sales_Phone_1`) واضغط **حفظ الإعدادات وتفعيل الرصد الدائم**.

### الخطوة 3: فتح لوحة التحكم (Dashboard)
1. افتح ملف [`dashboard/index.html`](./dashboard/index.html) في أي متصفح (أو فعّل **GitHub Pages** من إعدادات المستودع ليكون لديك رابط ثابت للوحة التحكم).
2. اضغط على زر **`⚙️ إعدادات Supabase`** في أعلى اللوحة وأدخل نفس `Project URL` و `Anon Key`، وستتصل اللوحة لحظياً بقاعدة البيانات!
