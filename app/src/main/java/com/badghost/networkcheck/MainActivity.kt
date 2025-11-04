package com.badghost.networkcheck

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.badghost.networkcheck.R.*
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var isStarting = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val status = findViewById<TextView>(id.contentCheckStatus)
        val ip = findViewById<TextView>(id.contentCheckIP)
        val switch = findViewById<SwitchMaterial>(id.networkCheckSwitch)

        switch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "以太网检查已开启", Toast.LENGTH_SHORT).show()
                isStarting = true
                startEthernetMonitoring(status, ip)
            } else {
                Toast.makeText(this, "以太网检查已关闭", Toast.LENGTH_SHORT).show()
                isStarting = false
                stopEthernetMonitoring()
            }
        }
    }

    private fun startEthernetMonitoring(status: TextView, ip: TextView) {
        scope.launch {
            while (isActive && isStarting) {
                try {
                    val ethernetInfo = withContext(Dispatchers.IO) {
                        EthernetUtils.getEthernetInfo()
                    }

                    updateNetworkUI(status, ip, ethernetInfo)

                    delay(500)

                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(1000)
                }
            }
        }
    }

    private fun updateNetworkUI(status: TextView, ip: TextView, ethernetInfo: EthernetUtils.EthernetInfo) {
        if (ethernetInfo.isConnected) {
            status.setText(string.connected)
            status.setTextColor(ContextCompat.getColor(this, color.green))

            val ipAddressesText = if (ethernetInfo.ipAddresses.isNotEmpty()) {
                ethernetInfo.ipAddresses.joinToString(", ")
            } else {
                "未知IP"
            }
            ip.text = "${getString(string.IP)} $ipAddressesText"

        } else {
            status.setText(string.disconnected)
            status.setTextColor(ContextCompat.getColor(this, color.red))
            ip.setText(string.noIP)
        }
    }

    private fun stopEthernetMonitoring() {
        scope.coroutineContext.cancelChildren()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

object EthernetUtils {
    fun getEthernetInfo(): EthernetInfo {
        return try {
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val ethernetIps = mutableListOf<String>()

            for (networkInterface in networkInterfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                if (isEthernetInterface(networkInterface)) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && !address.hostAddress.contains(":")) {
                            ethernetIps.add(address.hostAddress)
                        }
                    }
                }
            }

            if (ethernetIps.isNotEmpty()) {
                EthernetInfo(true, ethernetIps)
            } else {
                EthernetInfo(false, emptyList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EthernetInfo(false, emptyList())
        }
    }

    private fun isEthernetInterface(networkInterface: NetworkInterface): Boolean {
        val interfaceName = networkInterface.name.lowercase(Locale.ROOT)

        return when {
            interfaceName.startsWith("eth") -> true
            interfaceName.startsWith("ethernet") -> true
            interfaceName.startsWith("wlan") -> false
            interfaceName.startsWith("rmnet") -> false
            else -> {
                val displayName = networkInterface.displayName?.lowercase(Locale.ROOT) ?: ""
                displayName.contains("ethernet") || displayName.contains("eth")
            }
        }
    }

    data class EthernetInfo(
        val isConnected: Boolean,
        val ipAddresses: List<String>
    )
}