package com.example.amazonmusictrigger


import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Settings Button
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Auto-check battery optimization on startup (silent)
        checkBatteryOptimization()
        checkBatteryOptimization()
        checkOverlayPermission()
        updateServiceStatus()

        // 4. Test Buttons
        // Existing Standard Test (uses current preference logic technically, but here we force the specific URI for testing)
        // But wait, the original button was "Test My BGM". Let's make it test the STANDARD URI we use.
        findViewById<Button>(R.id.btn_test_bgm).setOnClickListener {
            // ユーザー指定のステーションID
            attemptLaunch("https://music.amazon.co.jp/stations/A1ESXGJW9GSMCX")
        }

        findViewById<Button>(R.id.btn_run_diagnostics).setOnClickListener {
            runDiagnostics()
        }
    }



    private fun attemptLaunch(uriString: String) {
        val tag = "AmazonMusicTrigger_Debug"
        Log.d(tag, "Testing URI: $uriString")

        // バイブレーション実行
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Vibration failed", e)
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // intent.setPackage("com.amazon.mp3") // REMOVED: Causing result code -91 on some devices

            startActivity(intent)
            Toast.makeText(this, "起動試行: $uriString", Toast.LENGTH_SHORT).show()

            // 1.5秒後にホーム画面へ遷移 (バグ回避のため)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val homeIntent = packageManager.getLaunchIntentForPackage("com.amazon.mp3")
                    if (homeIntent != null) {
                        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(homeIntent)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to launch Home", e)
                }
            }, 1500)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch $uriString", e)
            Toast.makeText(this, "起動失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private fun runDiagnostics() {
        val sb = StringBuilder()
        val tvLog = findViewById<android.widget.TextView>(R.id.tv_log_output)
        sb.append("=== DIAGNOSTIC REPORT ===\n")
        sb.append("Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")

        // 1. Check Package Availability
        sb.append("\n[1] Package Check 'com.amazon.mp3':\n")
        try {
            val pi = packageManager.getPackageInfo("com.amazon.mp3", 0)
            sb.append(" - FOUND. Ver: ${pi.versionName} (${pi.longVersionCode})\n")
        } catch (e: Exception) {
            sb.append(" - NOT FOUND (NameNotFoundException)\n")
            sb.append("   (Reason: App not installed OR <queries> missing in Manifest)\n")
        }

        // 2. Check Launch Intent
        sb.append("\n[2] GetLaunchIntentForPackage:\n")
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.amazon.mp3")
            if (intent != null) {
                sb.append(" - SUCCESS. Intent: $intent\n")
            } else {
                sb.append(" - FAILED. Returned null.\n")
            }
        } catch (e: Exception) {
            sb.append(" - ERROR: ${e.message}\n")
        }

        // 3. Check URI Resolution (Implicit) - OLD
        sb.append("\n[3] Resolve 'amzn://...':\n")
        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("amzn://music/station/mysoundtrack"))
        val activities = packageManager.queryIntentActivities(uriIntent, 0)
        sb.append(" - Found ${activities.size} handlers.\n")

        // 3b. Check URI Resolution (HTTPS) - NEW
        sb.append("\n[3b] Resolve 'https://music.amazon.co.jp/...':\n")
        val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.amazon.co.jp/stations/A1ESXGJW9GSMCX"))
        val httpsactivities = packageManager.queryIntentActivities(httpsIntent, 0)
        sb.append(" - Found ${httpsactivities.size} handlers.\n")
        for (resolveInfo in httpsactivities) {
            sb.append("   * ${resolveInfo.activityInfo.packageName}\n")
        }

        // 4. Test Explicit Intent (What caused -91?)
        sb.append("\n[4] Explicit Intent 'com.amazon.mp3':\n")
        try {
            val explicitIntent = Intent(Intent.ACTION_VIEW, Uri.parse("amzn://music/station/mysoundtrack"))
            explicitIntent.setPackage("com.amazon.mp3")
            // Just resolve, don't start
            val resolveExplicit = packageManager.resolveActivity(explicitIntent, 0)
            if (resolveExplicit != null) {
                sb.append(" - RESOLVED: ${resolveExplicit.activityInfo.name}\n")
            } else {
                sb.append(" - FAILED to resolve explicitly.\n")
            }
        } catch (e: Exception) {
            sb.append(" - ERROR: ${e.message}\n")
        }

        tvLog.text = sb.toString()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "バックグラウンド実行のため、「他のアプリの上に重ねて表示」を許可してください", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityServiceEnabled()
        updateServiceStatus()
    }
    
    private fun updateServiceStatus() {
        val tvLog = findViewById<android.widget.TextView>(R.id.tv_log_output)
        val statusText = if (TriggerService.isConnected) "Status: Connected (OK)" else "Status: Disconnected (NG)"
        val color = if (TriggerService.isConnected) android.graphics.Color.GREEN else android.graphics.Color.RED
        
        // Append status to log area for visibility
        val currentText = tvLog.text.toString()
        if (!currentText.startsWith("Status:")) {
             tvLog.text = "$statusText\n\n$currentText"
        } else {
             // Replace first line
             val lines = currentText.split("\n", limit = 2)
             if (lines.size > 1) {
                 tvLog.text = "$statusText\n${lines[1]}"
             } else {
                 tvLog.text = statusText
             }
        }
        tvLog.setTextColor(color)
    }

    private fun checkAccessibilityServiceEnabled() {
        if (!isAccessibilityServiceEnabled(TriggerService::class.java)) {
            Toast.makeText(this, "動作にはアクセシビリティサービス(ユーザー補助)の有効化が必要です", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == packageName && enabledServiceInfo.name == service.name) {
                return true
            }
        }
        return false
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "バックグラウンド動作のため、制限なしに設定してください", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch battery optimization settings", e)
                try {
                     val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                     startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
             // Already optimized (ignored), maybe show toast only if called from button?
             // Since we call this from onCreate, checking if called explicitly would be complex without param.
             // But for onCreate usage, we just want to silent check.
             // If button clicked, we might want feedback.
             // For now, let's leave as is. The user mainly asked for flow.
        }
    }


    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) {
            val keyCode = event.keyCode
            val action = event.action
             if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                if (action == KeyEvent.ACTION_DOWN) {
                    Toast.makeText(this, "MainAct Key: $keyCode", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
