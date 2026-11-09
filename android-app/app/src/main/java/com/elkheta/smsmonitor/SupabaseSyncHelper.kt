package com.elkheta.smsmonitor

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object SupabaseSyncHelper {

    private const val PREFS_NAME = "sms_monitor_prefs"
    private const val KEY_SUPABASE_URL = "supabase_url"
    private const val KEY_SUPABASE_KEY = "supabase_key"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ONLY_PAYMENT_KEYWORDS = "only_payment_keywords"
    private const val KEY_LOGS = "sms_logs_json"
    private const val KEY_PENDING_QUEUE = "pending_queue_json"

    const val ACTION_LOGS_UPDATED = "com.elkheta.smsmonitor.LOGS_UPDATED"

    private val executor = Executors.newSingleThreadExecutor()

    // تعبيرات الـ Regex المطلوبة لاستخراج البيانات من رسالة فودافون كاش
    private val AMOUNT_REGEX = Regex("""مبلغ\s*([\d.]+)\s*جنيه""")
    private val PHONE_REGEX = Regex("""من\s*(01[0-9]{9})""")
    private val TX_ID_REGEX = Regex("""رقم العملية\s*:\s*(\d+)""")

    data class ParsedPayment(
        val isValid: Boolean,
        val amount: Double?,
        val senderPhone: String?,
        val transactionId: String?
    )

    fun parseSms(message: String): ParsedPayment {
        val amountStr = AMOUNT_REGEX.find(message)?.groupValues?.getOrNull(1)
        val amount = amountStr?.toDoubleOrNull()
        val phone = PHONE_REGEX.find(message)?.groupValues?.getOrNull(1)
        val txId = TX_ID_REGEX.find(message)?.groupValues?.getOrNull(1)

        val isValid = amount != null && !phone.isNullOrBlank() && !txId.isNullOrBlank()
        return ParsedPayment(
            isValid = isValid,
            amount = if (isValid) amount else null,
            senderPhone = if (isValid) phone else null,
            transactionId = if (isValid) txId else null
        )
    }

    fun saveConfig(
        context: Context,
        supabaseUrl: String,
        supabaseKey: String,
        deviceId: String,
        onlyPaymentKeywords: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SUPABASE_URL, supabaseUrl.trim().trimEnd('/'))
            .putString(KEY_SUPABASE_KEY, supabaseKey.trim())
            .putString(KEY_DEVICE_ID, deviceId.trim().ifEmpty { "Sales_Phone_1" })
            .putBoolean(KEY_ONLY_PAYMENT_KEYWORDS, onlyPaymentKeywords)
            .apply()
    }

    private const val DEFAULT_SUPABASE_URL = "https://wbpotzyvvdirytnugzlb.supabase.co"
    private const val DEFAULT_SUPABASE_KEY = "sb_publishable_t1_zYtk_-EoGnWF--nx9vg_zkQxXsxA"

    fun getSupabaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUPABASE_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL

    fun getSupabaseKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUPABASE_KEY, DEFAULT_SUPABASE_KEY) ?: DEFAULT_SUPABASE_KEY

    fun getDeviceId(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, "Sales_Phone_1") ?: "Sales_Phone_1"

    fun isOnlyPaymentKeywords(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONLY_PAYMENT_KEYWORDS, false)

    fun processIncomingSms(context: Context, sender: String, messageBody: String) {
        val appContext = context.applicationContext

        if (isOnlyPaymentKeywords(appContext)) {
            val looksLikePayment = messageBody.contains("جنيه") ||
                messageBody.contains("كاش") ||
                messageBody.contains("رقم العملية") ||
                sender.contains("VF", ignoreCase = true) ||
                sender.contains("Cash", ignoreCase = true)
            if (!looksLikePayment) {
                return
            }
        }

        executor.execute {
            val deviceId = getDeviceId(appContext)
            val success = sendToSupabaseSync(appContext, messageBody, deviceId)
            if (success) {
                appendLog(appContext, "✅ تم الإرسال ($sender): $messageBody")
                flushPendingQueueSync(appContext)
            } else {
                enqueuePendingSms(appContext, messageBody, deviceId)
                appendLog(appContext, "⏳ محفوظة بالانتظار لإعادة الإرسال ($sender): $messageBody")
            }
        }
    }

    fun flushPendingQueueAsync(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            flushPendingQueueSync(appContext)
        }
    }

    private fun flushPendingQueueSync(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawQueue = prefs.getString(KEY_PENDING_QUEUE, "[]") ?: "[]"
        val queueArray = try {
            JSONArray(rawQueue)
        } catch (e: Exception) {
            JSONArray()
        }

        if (queueArray.length() == 0) return

        val remaining = JSONArray()
        var sentCount = 0

        for (i in 0 until queueArray.length()) {
            val item = queueArray.optJSONObject(i) ?: continue
            val msg = item.optString("message", "")
            val dev = item.optString("device_id", getDeviceId(context))
            if (msg.isNotBlank()) {
                val ok = sendToSupabaseSync(context, msg, dev)
                if (ok) {
                    sentCount++
                } else {
                    remaining.put(item)
                }
            }
        }

        prefs.edit().putString(KEY_PENDING_QUEUE, remaining.toString()).apply()
        if (sentCount > 0) {
            appendLog(context, "🔄 تم إرسال $sentCount رسالة كانت معلقة في طابور الانتظار بنجاح.")
        }
    }

    private fun enqueuePendingSms(context: Context, message: String, deviceId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawQueue = prefs.getString(KEY_PENDING_QUEUE, "[]") ?: "[]"
        val queueArray = try {
            JSONArray(rawQueue)
        } catch (e: Exception) {
            JSONArray()
        }

        val obj = JSONObject().apply {
            put("message", message)
            put("device_id", deviceId)
            put("queued_at", System.currentTimeMillis())
        }
        queueArray.put(obj)
        prefs.edit().putString(KEY_PENDING_QUEUE, queueArray.toString()).apply()
    }

    fun getPendingCount(context: Context): Int {
        val rawQueue = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_QUEUE, "[]") ?: "[]"
        return try {
            JSONArray(rawQueue).length()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * يرسل الرسالة إلى Supabase:
     * يحاول أولاً استدعاء الدالة `/rest/v1/rpc/ingest_sms_payment`
     * وفي حال عدم وجودها يقوم بالإدخال المباشر في جدول `/rest/v1/incoming_payments` بعد استخراج الـ Regex محلياً
     */
    private fun sendToSupabaseSync(context: Context, message: String, deviceId: String): Boolean {
        val baseUrl = getSupabaseUrl(context)
        val apiKey = getSupabaseKey(context)
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return false
        }

        // 1. المحاولة عبر دالة RPC
        try {
            val rpcUrl = URL("$baseUrl/rest/v1/rpc/ingest_sms_payment")
            val conn = (rpcUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = JSONObject().apply {
                put("p_raw_message", message)
                put("p_device_id", deviceId)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                return true
            }
        } catch (e: Exception) {
            // ننتقل للمحاولة المباشرة أو حفظها في طابور الانتظار
        }

        // 2. الإدخال المباشر في جدول incoming_payments كـ Fallback
        return try {
            val parsed = parseSms(message)
            val tableUrl = URL("$baseUrl/rest/v1/incoming_payments")
            val conn = (tableUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Prefer", "resolution=ignore-duplicates,return=minimal")
            }

            val rowPayload = JSONObject().apply {
                put("sender_phone", if (parsed.isValid) parsed.senderPhone else JSONObject.NULL)
                put("amount", if (parsed.isValid) parsed.amount else JSONObject.NULL)
                put("transaction_id", if (parsed.isValid) parsed.transactionId else JSONObject.NULL)
                put("raw_message", message)
                put("device_id", deviceId)
                put("status", if (parsed.isValid) "unlinked" else "error")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(rowPayload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            code in 200..299 || code == 409 // 409 يعني عملية مكررة تم تسجيلها مسبقاً
        } catch (e: Exception) {
            false
        }
    }

    fun appendLog(context: Context, entry: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }

        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newArr = JSONArray()
        newArr.put("[$timeFmt] $entry")

        val maxLogs = 40
        for (i in 0 until minOf(arr.length(), maxLogs - 1)) {
            newArr.put(arr.getString(i))
        }

        prefs.edit().putString(KEY_LOGS, newArr.toString()).apply()
        context.sendBroadcast(Intent(ACTION_LOGS_UPDATED).setPackage(context.packageName))
    }

    fun getLogs(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOGS, "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {
        }
        return list
    }
}
