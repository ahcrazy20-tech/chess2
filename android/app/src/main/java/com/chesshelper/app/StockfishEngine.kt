package com.chesshelper.app

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local Stockfish (19) over UCI, bundled as an arm64 asset.
 * Port of StockfishEngine.swift — runs entirely on-device, zero network.
 */
object StockfishEngine {
    private const val TAG = "StockfishEngine"
    private const val ASSET_NAME = "stockfish"
    private const val UCI_OK = "uciok"
    private const val READY_OK = "readyok"

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var readerThread: Thread? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val lineListeners = Collections.synchronizedList(mutableListOf<(String) -> Unit>())
    private var pendingAnalysis: CompletableFuture<EngineAnalysis>? = null
    private val mvBuckets = Collections.synchronizedMap(mutableMapOf<Int, TopMove>())
    private var currentDepth = 0
    private val lock = Any()

    private var contextRef: Context? = null

    /** Copy the stockfish binary from assets into the app's private files dir and chmod +x. */
    fun init(context: Context) {
        contextRef = context
        val bin = File(context.filesDir, ASSET_NAME)
        if (!bin.exists()) {
            context.assets.open(ASSET_NAME).use { input ->
                FileOutputStream(bin).use { output -> input.copyTo(output) }
            }
            bin.setExecutable(true, false)
        }
    }

    fun start(onLine: (String) -> Unit) {
        stopInternal()
        val ctx = contextRef ?: throw IllegalStateException("StockfishEngine.init(Context) not called")
        val bin = File(ctx.filesDir, ASSET_NAME)
        require(bin.exists()) { "Stockfish binary missing at ${bin.absolutePath} — see BUILD-ANDROID.md" }

        process = ProcessBuilder(bin.absolutePath).redirectErrorStream(true).start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream, "UTF-8"))
        reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))

        readerThread = Thread({
            try {
                var line: String?
                while { line = reader!!.readLine(); line != null } do {
                    val trimmed = line.trim()
                    Log.d(TAG, "< $trimmed")
                    synchronized(lock) {
                        lineListeners.forEach { it(trimmed) }
                        handleLine(trimmed)
                    }
                }
            } catch (e: Exception) {
                if (!(e is InterruptedException)) Log.w(TAG, "stockfish reader ended", e)
            }
        }, "stockfish-reader").apply { isDaemon = true; start() }

        lineListeners.add(onLine)
        send("uci")
        waitFor(UCI_OK, 5000)
        send("isready")
        waitFor(READY_OK, 5000)
        send("setoption name Threads value 3")
        send("setoption name Hash value 64")
        send("setoption name MultiPV value 3")
    }

    private fun handleLine(line: String) {
        if (line.startsWith("bestmove")) {
            val rest = line.substringAfter("bestmove").trim()
            val bestUci = rest.split("\\s+".toRegex()).firstOrNull() ?: ""
            val sorted = mvBuckets.entries.sortedBy { it.key }
            val topMoves = sorted.mapIndexed { i, kv ->
                TopMove(
                    rank = i + 1,
                    uci = kv.value.uci,
                    san = kv.value.san,
                    evaluationCp = kv.value.evaluationCp,
                    mateIn = kv.value.mateIn,
                    depth = kv.value.depth
                )
            }
            val analysis = EngineAnalysis(topMoves, topMoves.firstOrNull(), currentDepth)
            pendingAnalysis?.complete(analysis)
            pendingAnalysis = null
            mvBuckets.clear()
            currentDepth = 0
        } else if (line.startsWith("info")) {
            val multipv = intAfter("multipv", line) ?: 1
            currentDepth = maxOf(currentDepth, intAfter("depth", line) ?: 0)
            val firstMove = tokenAfter("pv", line) ?: ""
            val cp = intAfter("score cp", line)
            val mate = intAfter("score mate", line)
            if (firstMove.isNotEmpty()) {
                mvBuckets[multipv] = TopMove(
                    rank = multipv, uci = firstMove, san = firstMove,
                    evaluationCp = cp, mateIn = mate, depth = currentDepth
                )
            }
        }
    }

    private fun intAfter(prefix: String, line: String): Int? {
        val idx = line.indexOf(prefix)
        if (idx < 0) return null
        val m = Regex("^(-?\\d+)").find(line.substring(idx + prefix.length).trimStart())
        return m?.groups?.get(1)?.value?.toIntOrNull()
    }

    private fun tokenAfter(prefix: String, line: String): String? {
        val idx = line.indexOf(prefix)
        if (idx < 0) return null
        val m = Regex("^([a-h][1-8][a-h][1-8][qrbn]?)").find(line.substring(idx + prefix.length).trimStart())
        return m?.groups?.get(1)?.value
    }

    fun send(command: String) {
        Log.d(TAG, "> $command")
        synchronized(lock) {
            writer?.write(command)
            writer?.newLine()
            writer?.flush()
        }
    }

    private fun waitFor(expected: String, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        var hit = false
        val temp: (String) -> Unit = { line ->
            if (line.contains(expected)) { hit = true; latch.countDown() }
        }
        synchronized(lock) { lineListeners.add(temp) }
        val ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        synchronized(lock) { lineListeners.remove(temp) }
        return ok
    }

    fun analyze(
        fen: String,
        multiPV: Int = 3,
        depth: Int = 20,
        movetimeMs: Int = 2000,
        onResult: (EngineAnalysis) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        executor.execute {
            try {
                synchronized(lock) {
                    pendingAnalysis = CompletableFuture()
                    mvBuckets.clear()
                    currentDepth = 0
                }
                send("position fen $fen")
                send("go depth $depth movetime $movetimeMs")
                val analysis = pendingAnalysis?.get(30, TimeUnit.SECONDS)
                    ?: throw IllegalStateException("engine returned no analysis")
                onResult(analysis)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun stop() { synchronized(lock) { send("stop") } }

    fun destroy() {
        stop()
        synchronized(lock) {
            readerThread?.interrupt()
            readerThread = null
            try { writer?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            writer = null
            reader = null
            process?.destroyForcibly()
            process = null
        }
        pendingAnalysis = null
        mvBuckets.clear()
        currentDepth = 0
    }
}
