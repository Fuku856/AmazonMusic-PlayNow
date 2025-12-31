package com.example.amazonmusictrigger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvBatteryStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        tvBatteryStatus = findViewById(R.id.tv_battery_status)

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

        // 3. Battery Optimization Button
        findViewById<Button>(R.id.btn_battery_optimization).setOnClickListener {
             requestBatteryOptimization()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
    }

    private fun updateBatteryStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
        
        if (isIgnoring) {
            tvBatteryStatus.text = "制限なし (OK)"
            tvBatteryStatus.setTextColor(android.graphics.Color.GREEN)
        } else {
            tvBatteryStatus.text = "制限あり (要設定)"
            tvBatteryStatus.setTextColor(android.graphics.Color.RED)
        }
    }

    private fun requestBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // First try direct dialog
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Failed to launch battery optimization dialog", e)
                // Fallback to settings list
                try {
                     val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                     startActivity(intent)
                     Toast.makeText(this, "「Amazon Music Trigger」を探して「制限なし」にしてください", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Toast.makeText(this, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "すでに最適化は解除されています", Toast.LENGTH_SHORT).show()
        }
    }
}
