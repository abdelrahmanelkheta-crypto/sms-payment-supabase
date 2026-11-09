package com.elkheta.smsmonitor

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var urlInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var deviceInput: EditText
    private lateinit var onlyPaymentCheckBox: CheckBox
    private lateinit var logsTextView: TextView

    private val logsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLogsUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(buildModernArabicUi())

        loadSavedSettings()
        checkAndRequestPermissions()
        refreshLogsUi()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SupabaseSyncHelper.ACTION_LOGS_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logsReceiver, filter)
        }
        refreshLogsUi()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logsReceiver)
        } catch (_: Exception) {
        }
    }

    private fun buildModernArabicUi(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#090D16"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 64)
        }

        val title = TextView(this).apply {
            text = "💸 راصد مدفوعات SMS (Supabase)"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "التقاط تلقائي لرسائل التحويلات وإرسالها فورياً إلى قاعدة بيانات Supabase"
            textSize = 13.5f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 8, 0, 28)
        }

        statusTextView = TextView(this).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#34D399"))
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(32, 24, 32, 24)
        }

        urlInput = createLabeledInput(
            container,
            "رابط مشروع Supabase (Project URL):",
            "https://xxxxxxxxxxxx.supabase.co",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )

        keyInput = createLabeledInput(
            container,
            "مفتاح Supabase (Anon / Publishable Key):",
            "eyJhbGciOiJIUzI1NiIsInR5cCI...",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )

        deviceInput = createLabeledInput(
            container,
            "معرف هذا التليفون (Device ID):",
            "Sales_Phone_1",
            InputType.TYPE_CLASS_TEXT
        )

        onlyPaymentCheckBox = CheckBox(this).apply {
            text = "إرسال الرسائل التي تحتوي على كلمات دفع فقط (جنيه / كاش / رقم العملية)"
            setTextColor(Color.parseColor("#E5E7EB"))
            textSize = 13.5f
            setPadding(12, 20, 12, 20)
        }

        val saveBtn = Button(this).apply {
            text = "💾 حفظ الإعدادات وتفعيل الرصد الدائم"
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener {
                saveSettingsAndStartService()
            }
        }

        val testBtn = Button(this).apply {
            text = "⚡ إرسال رسالة فودافون كاش تجريبية الآن"
            setBackgroundColor(Color.parseColor("#6366F1"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setOnClickListener {
                sendTestSms()
            }
        }

        val retryBtn = Button(this).apply {
            text = "🔄 إعادة إرسال الرسائل المعلقة في طابور الانتظار"
            setBackgroundColor(Color.parseColor("#1F2937"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setOnClickListener {
                SupabaseSyncHelper.flushPendingQueueAsync(this@MainActivity)
                Toast.makeText(this@MainActivity, "جاري فحص وإرسال الرسائل المعلقة...", Toast.LENGTH_SHORT).show()
            }
        }

        val logsHeader = TextView(this).apply {
            text = "📋 سجل آخر الرسائل المرصودة:"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 36, 0, 12)
        }

        logsTextView = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#D1D5DB"))
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(28, 28, 28, 28)
            gravity = Gravity.RIGHT
        }

        container.addView(title, 0)
        container.addView(subtitle, 1)
        container.addView(statusTextView, 2)
        container.addView(onlyPaymentCheckBox)
        container.addView(saveBtn, createMarginParams(top = 18))
        container.addView(testBtn, createMarginParams(top = 14))
        container.addView(retryBtn, createMarginParams(top = 14))
        container.addView(logsHeader)
        container.addView(logsTextView, createMarginParams(top = 8))

        scrollView.addView(container)
        return scrollView
    }

    private fun createLabeledInput(
        parent: LinearLayout,
        labelText: String,
        hintText: String,
        inputTypeFlags: Int
    ): EditText {
        val label = TextView(this).apply {
            text = labelText
            textSize = 13.5f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 22, 0, 8)
        }
        val edit = EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(28, 24, 28, 24)
            inputType = inputTypeFlags
            textSize = 14f
        }
        parent.addView(label)
        parent.addView(edit)
        return edit
    }

    private fun createMarginParams(top: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = top
        }
    }

    private fun loadSavedSettings() {
        urlInput.setText(SupabaseSyncHelper.getSupabaseUrl(this))
        keyInput.setText(SupabaseSyncHelper.getSupabaseKey(this))
        deviceInput.setText(SupabaseSyncHelper.getDeviceId(this))
        onlyPaymentCheckBox.isChecked = SupabaseSyncHelper.isOnlyPaymentKeywords(this)
    }

    private fun saveSettingsAndStartService() {
        val url = urlInput.text.toString().trim()
        val key = keyInput.text.toString().trim()
        val device = deviceInput.text.toString().trim().ifEmpty { "Sales_Phone_1" }

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال رابط ومفتاح Supabase أولاً", Toast.LENGTH_LONG).show()
            return
        }

        SupabaseSyncHelper.saveConfig(
            this,
            url,
            key,
            device,
            onlyPaymentCheckBox.isChecked
        )

        startMonitorForegroundService()
        refreshLogsUi()
        Toast.makeText(this, "✅ تم حفظ الإعدادات وتفعيل الرصد في الخلفية", Toast.LENGTH_SHORT).show()
    }

    private fun sendTestSms() {
        val url = urlInput.text.toString().trim()
        val key = keyInput.text.toString().trim()
        val device = deviceInput.text.toString().trim().ifEmpty { "Sales_Phone_1" }

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "احفظ بيانات Supabase أولاً قبل التجربة", Toast.LENGTH_LONG).show()
            return
        }

        SupabaseSyncHelper.saveConfig(this, url, key, device, onlyPaymentCheckBox.isChecked)

        val randomTx = (100000000..999999999).random()
        val sampleMsg = "تم استلام مبلغ 350.00 جنيه من 01012345678 في محفظتك. رقم العملية : $randomTx"
        SupabaseSyncHelper.processIncomingSms(this, "VF-Cash", sampleMsg)
        Toast.makeText(this, "🚀 جاري إرسال الرسالة التجريبية...", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 200)
        } else {
            startMonitorForegroundService()
        }
    }

    private fun startMonitorForegroundService() {
        val serviceIntent = Intent(this, SmsMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshLogsUi()
        startMonitorForegroundService()
    }

    private fun refreshLogsUi() {
        val smsGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasSupabase = SupabaseSyncHelper.getSupabaseUrl(this).isNotBlank() &&
            SupabaseSyncHelper.getSupabaseKey(this).isNotBlank()

        val pendingCount = SupabaseSyncHelper.getPendingCount(this)

        statusTextView.text = buildString {
            append(if (smsGranted) "✅ صلاحية قراءة الرسائل: مفعلة\n" else "⚠️ صلاحية قراءة الرسائل: غير مفعلة (يرجى السماح)\n")
            append(if (hasSupabase) "✅ إعدادات Supabase: محفوظة وجاهزة\n" else "⚠️ إعدادات Supabase: لم يتم إدخالها بعد\n")
            append("📦 رسائل بالانتظار (Offline Queue): $pendingCount")
        }

        val logs = SupabaseSyncHelper.getLogs(this)
        logsTextView.text = if (logs.isEmpty()) {
            "لا توجد رسائل مرصودة حتى الآن. يمكنك الضغط على زر (⚡ إرسال رسالة تجريبية) للتجربة."
        } else {
            logs.joinToString("\n\n")
        }
    }
}
