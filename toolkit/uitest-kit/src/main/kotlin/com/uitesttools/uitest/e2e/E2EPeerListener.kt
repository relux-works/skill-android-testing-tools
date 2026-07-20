package com.uitesttools.uitest.e2e

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread

/**
 * Optional device-side TCP listener that lets the host bridge deliver peer
 * markers over a socket instead of (or in addition to) `adb push` of marker
 * files.
 *
 * ### When to use this
 *
 * The default, simplest transport is the **file channel** ([E2EMarkers]): the
 * host `adb pull`s a marker file from device A and `adb push`es it into device
 * B's marker dir. That needs no listener and is the recommended first path.
 *
 * This listener is the Android analog of the iOS peer-listener transport (there
 * usbmux/`iproxy`, here `adb forward`/`adb reverse`). Use it when you want
 * lower-latency delivery or a persistent bidirectional channel:
 *
 * ```
 * # host maps a local port to the device listener
 * adb -s <serial> forward tcp:8790 tcp:8790
 * # deliver a marker: one name per line
 * printf 'peer_detected\n' | nc localhost 8790
 * ```
 *
 * Each received line is treated as a marker name and written into the local
 * marker directory via [E2EMarkers.writeMarker]-equivalent semantics, so a
 * concurrent [E2EMarkers.waitForPeerMarker] observes it exactly as if the file
 * had been pushed.
 *
 * The listener runs on a daemon thread and is safe to [start] once per test and
 * [stop] in teardown. It intentionally has no auth and binds loopback by
 * default — it is a test-only channel reached through `adb forward`.
 */
class E2EPeerListener(
    private val context: Context,
    private val port: Int = DEFAULT_PORT,
    /** Bind address; loopback by default so only `adb forward` can reach it. */
    private val bindAddress: InetAddress = InetAddress.getByName("127.0.0.1"),
) {

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    @Volatile
    private var running = false

    /** Marker names received since [start], for assertions/inspection. */
    val received: MutableSet<String> = CopyOnWriteArraySet()

    /**
     * Start accepting connections. Non-blocking: accept runs on a daemon
     * thread. Calling [start] twice without [stop] is a no-op after the first.
     *
     * @return the actual bound port (equals [port] unless [port] was 0).
     */
    fun start(): Int {
        if (running) return serverSocket?.localPort ?: port
        val socket = ServerSocket(port, BACKLOG, bindAddress)
        serverSocket = socket
        running = true
        acceptThread = thread(isDaemon = true, name = "E2EPeerListener") {
            acceptLoop(socket)
        }
        Log.i(E2EMarkerFormat.TAG, "E2EPeerListener started on ${socket.localPort}")
        return socket.localPort
    }

    /** Stop the listener and release the port. Safe to call more than once. */
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
            // closing to interrupt accept(); ignore
        }
        serverSocket = null
        acceptThread = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                // socket closed by stop() — exit cleanly
                break
            }
            handleClient(client)
        }
    }

    private fun handleClient(client: Socket) {
        client.use { c ->
            val reader: BufferedReader = c.getInputStream().bufferedReader()
            while (running) {
                val line = reader.readLine() ?: break
                val name = line.trim()
                if (name.isEmpty()) continue
                if (!E2EMarkerFormat.isValidName(name)) {
                    Log.w(E2EMarkerFormat.TAG, "Ignoring invalid marker name over socket: '$name'")
                    continue
                }
                E2EMarkers.writeMarker(context, name)
                received.add(name)
            }
        }
    }

    companion object {
        /** Default port; forward it from the host with `adb forward tcp:8790 tcp:8790`. */
        const val DEFAULT_PORT: Int = 8790
        private const val BACKLOG = 4
    }
}
