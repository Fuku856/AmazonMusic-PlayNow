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

    private val PREFS_NAME = "AmazonMusicTriggerPrefs"
    private val KEY_SIMPLE_LAUNCH = "simple_launch_mode"

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

        // 3. Launch Mode Settings
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isSimpleLaunch = prefs.getBoolean(KEY_SIMPLE_LAUNCH, false)

        val rgLaunchMode = findViewById<RadioGroup>(R.id.rg_launch_mode)
        if (isSimpleLaunch) {
            findViewById<RadioButton>(R.id.rb_mode_launch).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.rb_mode_station).isChecked = true
        }

        rgLaunchMode.setOnCheckedChangeListener { _, checkedId ->
            val simpleMode = (checkedId == R.id.rb_mode_launch)
            prefs.edit().putBoolean(KEY_SIMPLE_LAUNCH, simpleMode).apply()
            val modeStr = if (simpleMode) "シンプル起動" else "My BGM 再生"
            Toast.makeText(this, "設定を保存しました: $modeStr", Toast.LENGTH_SHORT).show()
        }

        // 4. Test Buttons
        // Existing Standard Test (uses current preference logic technically, but here we force the specific URI for testing)
        // But wait, the original button was "Test My BGM". Let's make it test the STANDARD URI we use.
        findViewById<Button>(R.id.btn_test_bgm).setOnClickListener {
            attemptLaunch("amzn://music/station/mysoundtrack")
        }

        findViewById<Button>(R.id.btn_test_prime).setOnClickListener {
            attemptLaunch("amzn://music/prime/station/mysoundtrack")
        }

        findViewById<Button>(R.id.btn_test_shuffled).setOnClickListener {
            attemptLaunch("amzn://music/shuffled-station/mysoundtrack")
        }

        findViewById<Button>(R.id.btn_test_play_param).setOnClickListener {
            attemptLaunch("amzn://music/play?type=station&id=mysoundtrack")
        }
    }

    private fun attemptLaunch(uriString: String) {
        val tag = "AmazonMusicTrigger_Debug"
        Log.d(tag, "Testing URI: $uriString")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // intent.setPackage("com.amazon.mp3") // Explicit package sometimes helps, sometimes restricts. keeping generic if user has diff version? 
            // Actually, safe to restrict to amazon music to avoid browser opening it.
            intent.setPackage("com.amazon.mp3") 

            startActivity(intent)
            Toast.makeText(this, "起動試行: $uriString", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch $uriString", e)
            Toast.makeText(this, "起動失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
