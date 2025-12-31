package com.example.amazonmusictrigger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Accessibility Button
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "「Amazon Music Trigger」を探してONにしてください", Toast.LENGTH_LONG).show()
        }

        // 2. Overlay Button
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "すでに許可されています", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Launch Mode Settings REMOVED

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

        findViewById<Button>(R.id.btn_run_diagnostics).setOnClickListener {
            runDiagnostics()
        }
    }

    private fun attemptLaunch(uriString: String) {
        val tag = "AmazonMusicTrigger_Debug"
        Log.d(tag, "Testing URI: $uriString")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // intent.setPackage("com.amazon.mp3") // REMOVED: Causing result code -91 on some devices

            startActivity(intent)
            Toast.makeText(this, "起動試行: $uriString", Toast.LENGTH_SHORT).show()
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
}
