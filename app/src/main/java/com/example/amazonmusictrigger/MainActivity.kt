package com.example.amazonmusictrigger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import android.util.Log
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
            val tag = "AmazonMusicTrigger_Debug"
            Log.d(tag, "=== My BGM Test Button Clicked ===")
            
            // 試行するURIパターンのリスト
            val urisToTry = listOf(
                "amzn://music/station/mysoundtrack", // My BGM (My Soundtrack)
                "amzn://launch", // 単純起動
                "https://music.amazon.co.jp/stations/mysoundtrack" // Webリンク
            )

            var success = false
            val sb = StringBuilder()

            // 1. URIスキームでの起動を試行
            Log.d(tag, "Starting intent trials...")
            for (uriString in urisToTry) {
                Log.d(tag, "Trying URI: $uriString")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.setPackage("com.amazon.mp3") // パッケージを明示指定
                    
                    // Intentの内容をログ出力
                    Log.d(tag, "Created Intent: action=${intent.action}, data=${intent.data}, package=${intent.package}")
                    
                    startActivity(intent)
                    success = true
                    Log.d(tag, "SUCCESS: Launched $uriString")
                    Toast.makeText(this, "起動成功: $uriString", Toast.LENGTH_SHORT).show()
                    break
                } catch (e: Exception) {
                    val errorMsg = "FAILED: $uriString - ${e.javaClass.simpleName}: ${e.message}"
                    Log.e(tag, errorMsg)
                    sb.append("[$uriString: NG (${e.message})] ")
                }
            }

            // 2. 失敗した場合、パッケージからの直接起動を試行（My BGM指定なし）
            if (!success) {
                Log.d(tag, "URIs failed. Trying getLaunchIntentForPackage('com.amazon.mp3')...")
                try {
                    val intent = packageManager.getLaunchIntentForPackage("com.amazon.mp3")
                    if (intent != null) {
                        Log.d(tag, "Found launch intent for package. Starting activity...")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        success = true
                        Log.d(tag, "SUCCESS: Direct package launch")
                        Toast.makeText(this, "Amazon Musicを通常起動しました (My BGM指定不可)", Toast.LENGTH_LONG).show()
                    } else {
                        val msg = "Direct Launch: getLaunchIntentForPackage returned null (App not installed or not visible?)"
                        Log.e(tag, msg)
                        sb.append("[$msg]")
                    }
                } catch (e: Exception) {
                     val msg = "Direct Launch Error: ${e.message}"
                     Log.e(tag, msg, e)
                     sb.append("[$msg]")
                }
            }

            if (!success) {
                // すべて失敗した場合
                val msg = "ALL ATTEMPTS FAILED. Details: $sb"
                Log.e(tag, msg)
                // ユーザーに通知 (詳細エラー)
                Toast.makeText(this, "起動失敗。ログを確認してください。", Toast.LENGTH_LONG).show()
                
                // クリップボードにエラーをコピー（デバッグ用）
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Error Log", msg)
                clipboard.setPrimaryClip(clip)
            }
        }
    }
}
