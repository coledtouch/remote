package com.curbscript.tvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.Collections

/** A device found on the LAN. type = vizio | onn | samsung. */
data class Discovered(val type: String, val ip: String, val name: String, val room: String)

/**
 * Best-effort LAN discovery: SSDP (Vizio SmartCast, Samsung) + mDNS (onn Android TV
 * Remote service). Rooms are auto-assigned by device type.
 */
class DiscoveryManager(context: Context) {
    private val app = context.applicationContext

    suspend fun scan(): List<Discovered> = withContext(Dispatchers.IO) {
        val lock = acquireMulticastLock()
        try {
            val merged = LinkedHashMap<String, Discovered>()
            (ssdp() + mdns("_androidtvremote2._tcp.", "onn")).forEach {
                merged.putIfAbsent(it.type + "|" + it.ip, it)
            }
            merged.values.toList()
        } finally {
            try { lock?.release() } catch (_: Exception) {}
        }
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = try {
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.createMulticastLock("curb-remote-scan")?.apply { setReferenceCounted(true); acquire() }
    } catch (_: Exception) { null }

    private fun ssdp(): List<Discovered> {
        val found = LinkedHashMap<String, Discovered>()
        try {
            val socket = MulticastSocket()
            socket.soTimeout = 1200
            val group = InetAddress.getByName("239.255.255.250")
            val msg = (
                "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: ssdp:all\r\n\r\n"
                ).toByteArray()
            repeat(2) { socket.send(DatagramPacket(msg, msg.size, group, 1900)) }
            val deadline = System.currentTimeMillis() + 3500
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val ip = pkt.address?.hostAddress ?: continue
                val text = String(pkt.data, 0, pkt.length).lowercase()
                val type = when {
                    text.contains("vizio") || text.contains("kinoma") -> "vizio"
                    text.contains("samsung") -> "samsung"
                    else -> null
                }
                if (type != null) found.putIfAbsent(ip, Discovered(type, ip, nameFor(type), roomFor(type)))
            }
            socket.close()
        } catch (_: Exception) {
        }
        return found.values.toList()
    }

    @Suppress("DEPRECATION")
    private suspend fun mdns(serviceType: String, type: String): List<Discovered> =
        withContext(Dispatchers.IO) {
            val nsd = app.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return@withContext emptyList()
            val results = Collections.synchronizedList(mutableListOf<Discovered>())
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(s: String?, e: Int) {}
                override fun onStopDiscoveryFailed(s: String?, e: Int) {}
                override fun onDiscoveryStarted(s: String?) {}
                override fun onDiscoveryStopped(s: String?) {}
                override fun onServiceLost(info: NsdServiceInfo?) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    try {
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(i: NsdServiceInfo?, e: Int) {}
                            override fun onServiceResolved(si: NsdServiceInfo) {
                                val ip = si.host?.hostAddress ?: return
                                results.add(Discovered(type, ip, si.serviceName ?: nameFor(type), roomFor(type)))
                            }
                        })
                    } catch (_: Exception) {
                    }
                }
            }
            try {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                delay(4000)
            } catch (_: Exception) {
            } finally {
                try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
            }
            results.toList()
        }

    private fun nameFor(t: String): String = when (t) {
        "vizio" -> "Vizio TV"
        "onn" -> "onn 4K Pro"
        "samsung" -> "Samsung monitor"
        else -> t
    }

    private fun roomFor(t: String): String = if (t == "samsung") "bedroom" else "living"
}
