package com.example.amazonmusictrigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast

class MediaKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                Log.d("MediaKeyReceiver", "Received key: ${keyEvent.keyCode}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Receiver: ${KeyEvent.keyCodeToString(keyEvent.keyCode)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
