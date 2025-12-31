package com.example.amazonmusictrigger

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class TriggerService : AccessibilityService() {

    private var lastNextPressTime: Long = 0
    private val doublePressThreshold = 800L // 800ms to allow for system lag
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSinglePressRunnable: Runnable? = null
    private var ignoreNextKey = false
    
    // Preference Constants (Must match MainActivity)


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TriggerService", "Service Connected")
        Toast.makeText(this, "Amazon Music Trigger サービス開始", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, but required to override
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (ignoreNextKey) {
            return super.onKeyEvent(event)
        }
        val action = event.action
        val keyCode = event.keyCode

        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            if (action == KeyEvent.ACTION_DOWN) {
                // ダウンイベントのみ処理
                handleMediaNext()
            }
            // UPおよびDOWN両方のイベントを消費 (return true) して、デフォルトの曲送りを防ぐ
            return true
        }

        return super.onKeyEvent(event)
    }

    private fun handleMediaNext() {
        val currentTime = System.currentTimeMillis()
        
        if (pendingSinglePressRunnable != null) {
            // 前回のシングルプレス処理待ちがある状態で、再度押された -> ダブルプレス成立
            handler.removeCallbacks(pendingSinglePressRunnable!!)
            pendingSinglePressRunnable = null
            
            // ダブルプレス処理実行
            launchAmazonMusic()
        } else {
            // 1回目のプレス -> タイマーセット
            pendingSinglePressRunnable = Runnable {
                // タイマー満了 -> シングルプレス確定 -> 本来の曲送り機能を実行
                performOriginalMediaNext()
                pendingSinglePressRunnable = null
            }
            handler.postDelayed(pendingSinglePressRunnable!!, doublePressThreshold)
        }

        lastNextPressTime = currentTime
    }

    private fun launchAmazonMusic() {
        Log.d("TriggerService", "Detected Double Skip! Preparing to launch Amazon Music.")
        vibrateFeedback()
        launchMyBgmStation()
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                    vibrator.vibrate(effect, audioAttributes)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
        } catch (e: Exception) {
            Log.e("TriggerService", "Vibration failed", e)
        }
    }

    private fun launchMyBgmStation() {
        handler.post {
            Toast.makeText(applicationContext, "My BGM 起動", Toast.LENGTH_SHORT).show()
        }
        try {
            // "amzn://" スキームが認識されないため、HTTPSリンク (App Links) を使用
            // ユーザー指定のステーションIDを使用: A1ESXGJW9GSMCX
            val uri = Uri.parse("https://music.amazon.co.jp/stations/A1ESXGJW9GSMCX")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setPackage("com.amazon.mp3")
            startActivity(intent)
            
            // 1.5秒後にホーム画面へ遷移 (バグ回避のため)
            handler.postDelayed({
                try {
                    val homeIntent = packageManager.getLaunchIntentForPackage("com.amazon.mp3")
                    if (homeIntent != null) {
                        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(homeIntent)
                    }
                } catch (e: Exception) {
                    Log.e("TriggerService", "Failed to launch Home", e)
                }
            }, 1500)

        } catch (e: Exception) {
            Log.e("TriggerService", "Error launching Amazon Music Station", e)
            handler.post {
                Toast.makeText(applicationContext, "起動エラー: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performOriginalMediaNext() {
        Log.d("TriggerService", "Single press confirmed. Injecting media key.")
        
        // 自分のイベントを無視するようにフラグを立てる
        ignoreNextKey = true
        
        // AudioManager経由でメディアキーイベントを発行 (これが一番安定確実)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
        
        // 少し待ってからフラグを下ろす
        handler.postDelayed({ ignoreNextKey = false }, 300)
    }
}
