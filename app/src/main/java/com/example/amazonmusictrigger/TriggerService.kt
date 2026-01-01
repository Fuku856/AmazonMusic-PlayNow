package com.example.amazonmusictrigger

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast

class TriggerService : AccessibilityService() {

    private var lastNextPressTime: Long = 0
    private val doublePressThreshold = 800L // 800ms to allow for system lag
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSinglePressRunnable: Runnable? = null

    private var ignoreNextKey = false
    
    private lateinit var mediaSession: MediaSessionCompat
    
    // Preference Constants (Must match MainActivity)


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TriggerService", "Service Connected")
        Toast.makeText(this, "Amazon Music Trigger サービス開始", Toast.LENGTH_SHORT).show()
        
        initializeMediaSession()
        requestAudioFocus()
    }
    
    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result: Int
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d("TriggerService", "AudioFocus Changed: $focusChange")
                }
                .build()
            result = audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            result = audioManager.requestAudioFocus(
                { focusChange -> Log.d("TriggerService", "AudioFocus Changed: $focusChange") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
             Log.d("TriggerService", "Audio Focus GRANTED")
             Toast.makeText(this, "Focus Obtained", Toast.LENGTH_SHORT).show()
        } else {
             Log.d("TriggerService", "Audio Focus FAILED: $result")
        }
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "TriggerServiceMediaSession")
        
        // メディアボタンなどを受け取るためのフラグを設定
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        // 常に再生中（待機中）として振る舞い、ボタンの優先権を得る
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
        
        mediaSession.setPlaybackState(stateBuilder.build())

        // コールバック設定
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null) {
                    Log.d("TriggerService", "MediaSession onMediaButtonEvent: ${keyEvent.keyCode} ${keyEvent.action}")
                    // ここでもキーイベント処理を共有
                    if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                         android.os.Handler(android.os.Looper.getMainLooper()).post {
                             Toast.makeText(applicationContext, "Session Key: ${keyEvent.keyCode}", Toast.LENGTH_SHORT).show()
                         }
                         if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                             handleMediaNext()
                             return true // 消費する
                         }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        })

        mediaSession.isActive = true
        Log.d("TriggerService", "MediaSession initialized and active")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
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
        Log.d("TriggerService", "onKeyEvent: action=$action, keyCode=$keyCode")
        
        // Debug: Show toast for ANY key to verify service is alive and receiving events
        // Filtering only DOWN to avoid double toasts
        if (action == KeyEvent.ACTION_DOWN) {
             android.os.Handler(android.os.Looper.getMainLooper()).post {
                 Toast.makeText(applicationContext, "Key Detect: $keyCode", Toast.LENGTH_SHORT).show()
             }
        }


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
        
        // フォーカスを一時的に放棄して、システムにイベントを通過させる（Amazon Music等へ）
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             // シンプルに放棄 (AudioFocusRequestインスタンスを保持していないため、厳密には再生成が必要だが、
             // ここでは簡易的に古いAPIまたは同じパラメータで放棄を試みる)
             // 本来は保持したrequestを使うべきだが、簡単のため非推奨APIで代用または再構築
             // 今回はdispatchMediaKeyEventで送るため、フォーカス持ちっぱなしだと自分に戻る可能性があるが
             // 一旦そのまま送ってみる
        }
        
        // AudioManager経由でメディアキーイベントを発行 (これが一番安定確実)
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
        
        // 少し待ってからフラグを下ろす
        handler.postDelayed({ 
            ignoreNextKey = false
            // 必要なら再取得するが、今回はイベントドリブンで動くため放置
        }, 300)
    }
    }
}
