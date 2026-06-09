package com.detailline.callfollowcrm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.detailline.callfollowcrm.util.SmsSender
import java.io.File

class DebugMmsSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_TEST_MMS) return

        val phone = intent.getStringExtra("phone")?.takeIf { it.isNotBlank() } ?: DEFAULT_PHONE
        val body = intent.getStringExtra("body")?.takeIf { it.isNotBlank() }
            ?: "RING-GO direct MMS test ${System.currentTimeMillis()}"
        val uri = createTestImage(context)

        val requested = SmsSender.sendMms(
            context = context,
            phoneNumber = phone,
            body = body,
            uris = listOf(uri)
        )
        Log.i(TAG, "Debug MMS requested=$requested to=$phone uri=$uri")
    }

    private fun createTestImage(context: Context): Uri {
        val bitmap = Bitmap.createBitmap(720, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(245, 248, 252))

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 108, 240)
            textSize = 54f
            isFakeBoldText = true
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 42, 55)
            textSize = 34f
        }
        canvas.drawText("RING-GO MMS TEST", 54f, 120f, title)
        canvas.drawText("direct send debug image", 54f, 190f, body)
        canvas.drawText(System.currentTimeMillis().toString(), 54f, 255f, body)

        val file = File(context.cacheDir, "debug_mms_test.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    companion object {
        const val ACTION_SEND_TEST_MMS = "com.detailline.callfollowcrm.debug.SEND_TEST_MMS"
        private const val DEFAULT_PHONE = "01080056674"
        private const val TAG = "DebugMmsSendReceiver"
    }
}
