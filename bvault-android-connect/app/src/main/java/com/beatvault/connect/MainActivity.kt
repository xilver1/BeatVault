package com.beatvault.connect

import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PREFS_NAME = "BeatVaultPrefs"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnSync = findViewById<Button>(R.id.btnSync)

        setupWebView()

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnSync.setOnClickListener {
            syncCookies()
        }
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        
        webView.webViewClient = WebViewClient()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        
        webView.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube")
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        val etUrl = view.findViewById<EditText>(R.id.etGatewayUrl)
        val etUser = view.findViewById<EditText>(R.id.etUsername)
        val etPass = view.findViewById<EditText>(R.id.etPassword)

        etUrl.setText(prefs.getString("gateway_url", "http://192.168.1.100:8080"))
        etUser.setText(prefs.getString("username", ""))
        etPass.setText(prefs.getString("password", ""))

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putString("gateway_url", etUrl.text.toString())
                    .putString("username", etUser.text.toString())
                    .putString("password", etPass.text.toString())
                    .apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun syncCookies() {
        val cookieManager = CookieManager.getInstance()
        val rawCookies = cookieManager.getCookie("https://youtube.com")
        
        if (rawCookies.isNullOrEmpty()) {
            Toast.makeText(this, "No cookies found for youtube.com. Are you logged in?", Toast.LENGTH_LONG).show()
            return
        }

        // Convert the raw "key=value; key2=value2" into a Netscape cookies.txt format
        val netscapeCookies = parseToNetscape(rawCookies, ".youtube.com")
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gatewayUrl = prefs.getString("gateway_url", "")
        val username = prefs.getString("username", "")
        val password = prefs.getString("password", "")

        if (gatewayUrl.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty()) {
            Toast.makeText(this, "Please configure settings first.", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            return
        }

        Toast.makeText(this, "Syncing cookies to Gateway...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Authenticate to get session token
                val authJson = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                
                val authReq = Request.Builder()
                    .url("${gatewayUrl.trimEnd('/')}/auth/login")
                    .post(authJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                    
                val authRes = client.newCall(authReq).execute()
                if (!authRes.isSuccessful) {
                    throw Exception("Login failed: ${authRes.code}")
                }
                
                val token = JSONObject(authRes.body!!.string()).getString("token")
                
                // 2. Upload cookies
                val uploadJson = JSONObject().apply {
                    put("cookies_txt", netscapeCookies)
                }
                
                val uploadReq = Request.Builder()
                    .url("${gatewayUrl.trimEnd('/')}/auth/youtube/cookies")
                    .header("Authorization", "Bearer $token")
                    .post(uploadJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                    
                val uploadRes = client.newCall(uploadReq).execute()
                if (!uploadRes.isSuccessful) {
                    throw Exception("Cookie upload failed: ${uploadRes.code}")
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "✓ Cookies synced successfully!", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Sync Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseToNetscape(cookieHeader: String, domain: String): String {
        val sb = StringBuilder()
        sb.append("# Netscape HTTP Cookie File\n")
        sb.append("# Generated by BeatVault Connect\n\n")
        
        val pairs = cookieHeader.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0]
                val value = parts[1]
                val isSecure = key.startsWith("__Secure")
                val secureStr = if (isSecure) "TRUE" else "FALSE"
                // domain, includeSubdomains, path, secure, expiry, name, value
                // We set a fake expiry far in the future
                val expiry = (System.currentTimeMillis() / 1000) + (365 * 24 * 60 * 60)
                sb.append("$domain\tTRUE\t/\t$secureStr\t$expiry\t$key\t$value\n")
            }
        }
        return sb.toString()
    }
}
