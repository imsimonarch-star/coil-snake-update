package com.coilsnake.game.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.coilsnake.game.game.GameConst
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue

sealed class NetEvent {
    data object Open : NetEvent()
    data object Close : NetEvent()
    data class Message(val line: String) : NetEvent()
    data class Error(val message: String) : NetEvent()
}

/**
 * Native Kotlin port of NetBridge.java: TCP socket host/guest link over a phone hotspot.
 * One phone listens (Host = P1), the other connects (Guest = P2).
 */
class HotspotNetManager(
    context: Context,
    private val onEvent: (NetEvent) -> Unit
) {
    private val appCtx = context.applicationContext
    private val lock = Any()
    private var gen = 0
    private var server: ServerSocket? = null
    private var sock: Socket? = null
    private var outQueue: LinkedBlockingQueue<String>? = null

    @Suppress("DEPRECATION")
    fun gateway(): String {
        try {
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = cm?.activeNetwork
            val lp = if (active != null) cm.getLinkProperties(active) else null
            if (lp != null) {
                for (route in lp.routes) {
                    val gw = route.gateway
                    if (route.isDefaultRoute && gw is Inet4Address && !gw.isLoopbackAddress) {
                        return gw.hostAddress ?: ""
                    }
                }
            }
            val wm = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val d = wm?.dhcpInfo
            if (d != null && d.gateway != 0) {
                val g = d.gateway
                return "${g and 0xff}.${(g shr 8) and 0xff}.${(g shr 16) and 0xff}.${(g shr 24) and 0xff}"
            }
        } catch (_: Exception) {
        }
        return ""
    }

    fun localIps(): String {
        val first = mutableListOf<String>()
        val rest = mutableListOf<String>()
        try {
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp || ni.isLoopback) continue
                val n = ni.name.lowercase()
                if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("tun") ||
                    n.startsWith("dummy") || n.startsWith("v4-") || n.startsWith("clat") ||
                    n.startsWith("p2p")
                ) continue
                for (a in Collections.list(ni.inetAddresses)) {
                    if (a !is Inet4Address || !a.isSiteLocalAddress) continue
                    val addr = a.hostAddress ?: continue
                    val wifi = n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("eth")
                    if (wifi) first += addr else rest += addr
                }
            }
        } catch (_: Exception) {
        }
        first.addAll(rest)
        return first.joinToString(",")
    }

    fun host(port: Int = GameConst.NET_PORT) {
        close()
        val g = synchronized(lock) { ++gen }
        Thread({
            var ss: ServerSocket? = null
            try {
                ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                synchronized(lock) {
                    if (g != gen) {
                        ss.close()
                        return@Thread
                    }
                    server = ss
                }
                while (true) {
                    val s = ss.accept()
                    val alreadyConnected = synchronized(lock) {
                        if (g != gen) {
                            s.close()
                            return@Thread
                        }
                        sock != null && sock?.isClosed == false
                    }
                    if (alreadyConnected) {
                        s.close()
                        continue
                    }
                    attach(s, g)
                }
            } catch (e: Exception) {
                synchronized(lock) {
                    if (g != gen) return@Thread
                }
                onEvent(NetEvent.Error("Could not host: ${e.message ?: "error"}"))
            }
        }, "net-accept").start()
    }

    fun join(ip: String, port: Int = GameConst.NET_PORT) {
        close()
        val g = synchronized(lock) { ++gen }
        Thread({
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 5000)
                synchronized(lock) {
                    if (g != gen) {
                        s.close()
                        return@Thread
                    }
                }
                attach(s, g)
            } catch (_: Exception) {
                synchronized(lock) {
                    if (g != gen) return@Thread
                }
                onEvent(NetEvent.Error("Could not reach $ip. Is the hotspot on and the host waiting?"))
            }
        }, "net-join").start()
    }

    fun send(line: String) {
        val q = synchronized(lock) { outQueue } ?: return
        if (q.size > 60 && line.contains("\"t\":\"s\"")) return
        q.offer(line)
    }

    fun close() {
        var ss: ServerSocket?
        var q: LinkedBlockingQueue<String>?
        synchronized(lock) {
            gen++
            ss = server
            server = null
            q = outQueue
            outQueue = null
            sock = null
        }
        try {
            ss?.close()
        } catch (_: Exception) {
        }
        q?.offer(BYE)
    }

    private fun attach(s: Socket, g: Int) {
        s.tcpNoDelay = true
        s.keepAlive = true
        val q = LinkedBlockingQueue<String>()
        synchronized(lock) {
            if (g != gen) {
                s.close()
                return
            }
            sock = s
            outQueue = q
        }
        onEvent(NetEvent.Open)

        Thread({
            try {
                val os: OutputStream = s.getOutputStream()
                while (true) {
                    val line = q.take()
                    if (line == BYE) break
                    os.write((line + "\n").toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                }
            } catch (_: Exception) {
            } finally {
                try {
                    s.close()
                } catch (_: Exception) {
                }
            }
        }, "net-write").start()

        Thread({
            try {
                val r = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8), 1 shl 16)
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    synchronized(lock) {
                        if (g != gen) return@Thread
                    }
                    line?.let { onEvent(NetEvent.Message(it)) }
                }
            } catch (_: Exception) {
            }
            val ours = synchronized(lock) {
                val isOurs = (g == gen) && (sock === s)
                if (isOurs) {
                    sock = null
                    outQueue = null
                }
                isOurs
            }
            try {
                s.close()
            } catch (_: Exception) {
            }
            q.offer(BYE)
            if (ours) onEvent(NetEvent.Close)
        }, "net-read").start()
    }

    companion object {
        private const val BYE = "\u0000bye"
    }
}
