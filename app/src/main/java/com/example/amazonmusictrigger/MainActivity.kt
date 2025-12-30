package com.example.amazonmusictrigger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "「Amazon Music Trigger」を探してONにしてください", Toast.LENGTH_LONG).show()
        }

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

        findViewById<Button>(R.id.btn_test_bgm).setOnClickListener {
            try {
                // Amazon Music "My BGM" (My Soundtrack) URI
                val uri = Uri.parse("amzn://music/station/mysoundtrack")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                
                Toast.makeText(this, "My BGM 起動リクエスト送信", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Amazon Musicの起動に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
