package com.spiderai.app

import androidx.activity.ComponentActivity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var model: LlamaModel? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequest = 4107
    private var modelState = "Подготовка локального ИИ…"
    private var downloadId = -1L
    private val handler = Handler(Looper.getMainLooper())
    private val modelName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
    private val modelUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.addJavascriptInterface(LocalAI(), "SpiderLocal")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "audio/*", "text/*", "application/*"))
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    startActivityForResult(Intent.createChooser(intent, "Выбрать фото, видео, аудио или файл"), fileChooserRequest)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                loader.shouldInterceptRequest(request.url)
        }
        setContentView(webView)
        // Android 15 edge-to-edge: keep WebView content below status/navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
        prepareModel()
    }

    @Deprecated("Deprecated in Android API; retained for WebView file chooser compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileChooserRequest) return
        val callback = fileChooserCallback ?: return
        fileChooserCallback = null
        if (resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return
        }
        val uris = mutableListOf<Uri>()
        data?.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
        }
        if (uris.isEmpty()) data?.data?.let { uris.add(it) }
        callback.onReceiveValue(uris.distinct().toTypedArray())
    }

    private fun modelFile() = File(getExternalFilesDir("models"), modelName)

    private fun prepareModel() {
        lifecycleScope.launch {
            try {
                val file = modelFile()
                if (!file.exists() || file.length() < 300_000_000L) {
                    modelState = "Автоматически загружаю модель ИИ (~398 МБ)…"
                    notifyStatus()
                    val request = DownloadManager.Request(Uri.parse(modelUrl))
                        .setTitle("Spider AI — локальная модель")
                        .setDescription("Загрузка открытой модели Qwen2.5 0.5B")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setDestinationInExternalFilesDir(this@MainActivity, "models", modelName)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    downloadId = manager.enqueue(request)
                    monitorDownload(manager)
                    return@launch
                }
                loadLocalModel(file)
            } catch (e: Exception) {
                modelState = "Ошибка подготовки модели: ${e.message}"
                notifyStatus()
            }
        }
    }

    private fun monitorDownload(manager: DownloadManager) {
        val poll = object : Runnable {
            override fun run() {
                if (downloadId < 0) return
                val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cursor.close()
                        downloadId = -1
                        lifecycleScope.launch { loadLocalModel(modelFile()) }
                        return
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        cursor.close()
                        modelState = "Загрузка модели не удалась (код $reason). Проверь интернет и свободное место."
                        notifyStatus()
                        return
                    }
                    modelState = if (total > 0) "Загружаю модель: ${(soFar * 100 / total)}%" else "Загружаю локальную модель…"
                    cursor.close()
                    notifyStatus()
                } else cursor?.close()
                handler.postDelayed(this, 1200)
            }
        }
        handler.post(poll)
    }

    private suspend fun loadLocalModel(file: File) {
        modelState = "Загрузка модели в память телефона…"
        notifyStatus()
        try {
            val loaded = withContext(Dispatchers.Default) {
                Llama.loadModel(
                    modelPath = file.absolutePath,
                    config = LlamaConfig(contextSize = 2048, threads = 4)
                )
            }
            model = loaded
            modelState = "Локальный ИИ готов · Qwen2.5 0.5B · без API"
        } catch (e: Exception) {
            modelState = "Не удалось запустить модель: ${e.message}"
        }
        notifyStatus()
    }

    private fun notifyStatus() {
        runOnUiThread {
            if (::webView.isInitialized) webView.evaluateJavascript(
                "window.onSpiderLocalStatus && window.onSpiderLocalStatus(${JSONObject.quote(modelState)})", null
            )
        }
    }

    inner class LocalAI {
        @JavascriptInterface fun status(): String = modelState

        @JavascriptInterface fun chat(id: String, prompt: String) {
            lifecycleScope.launch {
                val active = model
                if (active == null) {
                    callback(id, "", modelState)
                    return@launch
                }
                try {
                    val result = withContext(Dispatchers.Default) {
                        Llama.complete(
                            active,
                            prompt = prompt,
                            systemPrompt = "Ты Spider AI. Отвечай по-русски, точно и понятно. Если не знаешь — честно скажи.",
                            maxTokens = 512
                        )
                    }
                    callback(id, result.text, "")
                } catch (e: Exception) {
                    callback(id, "", e.message ?: "Ошибка локальной модели")
                }
            }
        }
    }

    private fun callback(id: String, answer: String, error: String) {
        val script = "window.onSpiderLocalResult && window.onSpiderLocalResult(${JSONObject.quote(id)},${JSONObject.quote(answer)},${JSONObject.quote(error)})"
        runOnUiThread { webView.evaluateJavascript(script, null) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        val active = model
        if (active != null) lifecycleScope.launch(Dispatchers.Default) {
            try { Llama.releaseModel(active) } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
