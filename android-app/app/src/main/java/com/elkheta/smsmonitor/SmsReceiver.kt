package com.elkheta.smsmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // تجميع أجزاء الرسالة النصية الطويلة (Multi-part SMS) في نص واحد متكامل
        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val fullMessageBody = StringBuilder()
        for (sms in messages) {
            sms.displayMessageBody?.let { fullMessageBody.append(it) }
        }

        val bodyText = fullMessageBody.toString().trim()
        if (bodyText.isNotEmpty()) {
            SupabaseSyncHelper.processIncomingSms(context, sender, bodyText)
        }
    }
}
