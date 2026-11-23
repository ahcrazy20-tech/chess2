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
 * Local Stockfish (UCI) running fully on-device.
 *
 * The engine binary ships inside the APK as `jniLibs/arm64-v8a/libstockfish.so`
 * (that is the only way to get an executable file on the device's native library
 * directory — Android 10+ forbids exec'ing from the app's data dir). If the
 * native lib is missing we fall back to copying `assets/stockfish`, which is
 * useful for old API levels / local experiments.
 */
object StockfishEngine {
    private const val TAG = "StockfishEngine"
    private const val ASSET_NAME = "stockfish"
    private const val NATIVE_LIB = "libstockfish.so"
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

    /** Absolute path of the resolved engine executable, or null when unavailable. */
    var binaryPath: String? = null
        private set

    val isAvailable: Boolean get() = binaryPath != null

    /**
     * Locate the engine binary: prefer the extracted native lib, then an
     * executable copied from assets. Safe to call repeatedly.
     */
    fun init(context: Context) {
        contextRef = context
        binaryPath = resolveBinary(context)
        Log.i(TAG, "engine binary = ${binaryPath ?: "<missing>"}")
    }

    private fun resolveBinary(context: Context): String? {
        // 1) jniLibs payload extracted by the package manager.
        val native = File(context.applicationInfo.nativeLibraryDir, NATIVE_LIB)
        if (native.canExecute()) return native.absolutePath

        // 2) Optional APK asset (legacy / side-loaded testing path).
        val copied = File(context.filesDir, ASSET_NAME)
        try {
            if (!copied.exists()) {
                context.assets.open(ASSET_NAME).use { input ->
                    FileOutputStream(copied).use { output -> input.copyTo(output) }
                }
            }
            copied.setExecutable(true, false)
            if (copied.canExecute() && copied.length() > 0) return copied.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "no usable stockfish asset", e)
        }
        return null
    }

    fun start(onLine: (String) -> Unit) {
        stopInternal()
        val path = binaryPath ?: throw IllegalStateException(
            "Stockfish binary missing — it should be packaged as libstockfish.so"
        )
        val bin = File(path)
        require(bin.exists()) { "Stockfish binary missing at ${bin.absolutePath} — see BUILD-ANDROID.md" }

        process = ProcessBuilder(bin.absolutePath).redirectErrorStream(true).start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream, "UTF-8"))
        reader = BufferedReader(InputStreamReader(process!!.inputStream, "UTF-8"))

        readerThread = Thread({
            try {
                while (true) {
                    val raw = reader?.readLine() ?: break
                    val trimmed = raw.trim()
                    Log.d(TAG, "< $trimmed")
                    synchronized(lock) {
                        lineListeners.toList().forEach { it(trimmed) }
                        handleLine(trimmed)
                    }
                }
            } catch (e: Exception) {
                if (e !is InterruptedException) Log.w(TAG, "stockfish reader ended", e)
            }
        }, "stockfish-reader").apply { isDaemon = true; start() }

        synchronized(lock) { lineListeners.add(onLine) }
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
            val pending = pendingAnalysis
            pending?.complete(analysis)
            pendingAnalysis = null
            mvBuckets.clear()
            currentDepth = 0
        } else if (line.startsWith("info")) {
            val depth = intAfter("depth", line) ?: 0
            if (depth > currentDepth) currentDepth = depth
            val multipv = intAfter("multipv", line) ?: 1
            val firstMove = tokenAfter("pv", line) ?: return
            val cp = intAfter("score cp", line)
            val mate = intAfter("score mate", line)
            mvBuckets[multipv] = TopMove(
                rank = multipv,
                uci = firstMove,
                san = firstMove,
                evaluationCp = cp,
                mateIn = mate,
                depth = depth
            )
        }
    }

    private fun intAfter(prefix: String, line: String): Int? {
        val idx = line.indexOf(prefix)
        if (idx < 0) return null
        val m = Regex("^(-?\\d+)").find(line.substring(idx + prefix.length).trimStart())
        return m?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun tokenAfter(prefix: String, line: String): String? {
        val idx = line.indexOf(prefix)
        if (idx < 0) return null
        val m = Regex("^([a-h][1-8][a-h][1-8][qrbn]?)").find(line.substring(idx + prefix.length).trimStart())
        return m?.groupValues?.get(1)
    }

    fun send(command: String) {
        Log.d(TAG, "> $command")
        synchronized(lock) {
            try {
                writer?.write(command)
                writer?.newLine()
                writer?.flush()
            } catch (e: Exception) {
                Log.w(TAG, "failed to send '$command'", e)
            }
        }
    }

    private fun waitFor(expected: String, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        val temp: (String) -> Unit = { line -> if (line.contains(expected)) latch.countDown() }
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
                    mvBuckets.clear()
                    currentDepth = 0
                    pendingAnalysis = CompletableFuture()
                }
                send("setoption name MultiPV value $multiPV")
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

    fun stop() = send("stop")

    fun destroy() {
        stopInternal()
        synchronized(lock) {
            try { writer?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            writer = null
            reader = null
            process?.destroy()
            process = null
            lineListeners.clear()
        }
        pendingAnalysis = null
        mvBuckets.clear()
        currentDepth = 0
    }

    private fun stopInternal() {
        synchronized(lock) {
            try {
                writer?.write("quit")
                writer?.newLine()
                writer?.flush()
            } catch (_: Exception) {}
            readerThread?.interrupt()
            readerThread = null
            process?.let { if (it.isAlive) it.destroy() }
            process = null
            try { writer?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            writer = null
            reader = null
        }
    }
}
