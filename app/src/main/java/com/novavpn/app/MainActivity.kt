package com.novavpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var backend: GoBackend
    private lateinit var status: TextView
    private var config: Config? = null
    private val tunnel = object : Tunnel {
        override fun getName() = "NovaVPN"
        override fun onStateChange(newState: Tunnel.State) { runOnUiThread { status.text = "Статус: " + if (newState == Tunnel.State.UP) "Подключено" else "Отключено" } }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) connectNow()
        else status.text = "Статус: разрешение VPN не выдано"
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { Config.parse(it) } }
                config = parsed
                status.text = "Конфигурация импортирована. Можно подключаться."
            } catch (e: Exception) { status.text = "Ошибка конфигурации: ${e.message}" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 70, 40, 40) }
        status = TextView(this).apply { text = "Статус: отключено"; textSize = 18f }
        val import = Button(this).apply { text = "Импортировать WireGuard .conf"; setOnClickListener { filePicker.launch(arrayOf("text/*", "application/octet-stream")) } }
        val connect = Button(this).apply { text = "Подключить"; setOnClickListener { if (config == null) status.text = "Сначала импортируйте .conf" else { val intent = VpnService.prepare(this@MainActivity); if (intent != null) permission.launch(intent) else connectNow() } } }
        val disconnect = Button(this).apply { text = "Отключить"; setOnClickListener { lifecycleScope.launch { try { withContext(Dispatchers.IO) { backend.setState(tunnel, Tunnel.State.DOWN, null) }; status.text = "Статус: отключено" } catch (e: Exception) { status.text = "Ошибка: ${e.message}" } } } }
        layout.addView(TextView(this).apply { text = "NovaVPN"; textSize = 30f }); layout.addView(status); layout.addView(import); layout.addView(connect); layout.addView(disconnect)
        setContentView(layout)
    }

    private fun connectNow() { val cfg = config ?: return; lifecycleScope.launch { try { withContext(Dispatchers.IO) { backend.setState(tunnel, Tunnel.State.UP, cfg) }; status.text = "Статус: подключено" } catch (e: Exception) { status.text = "Ошибка подключения: ${e.message}" } } }
}
