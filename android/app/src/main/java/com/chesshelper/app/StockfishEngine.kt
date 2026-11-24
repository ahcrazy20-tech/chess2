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
import java.util.concurrent.TimeUnit

/**
 * Local Stockfish over UCI. Fixes two things versus the iOS-era scaffold:
 *
 *  1. The binary is loaded from `nativeLibraryDir/libstockfish.so` (packed as a
 *     JNI library with legacy packaging so it is extracted with exec permission).
 *     Since Android 10, binaries in the writable app-data dir cannot be
 *     executed when targeting API 29+, so the old `filesDir/stockfish` approach
 *     no longer works on modern devices — we keep it only as a last resort.
 *  2. UCI scores are converted to WHITE point-of-view at ingestion time
 *     (UCI reports from the side-to-move's POV), so the whole app can use one
 *     consistent convention (see TopMove).
 *
 * If the binary is missing or crashes, [isAvailable] is false and
 * EngineViewModel falls back to [FallbackEngine] automatically.
 */
object StockfishEngine : ChessEngine {

    override val name: String get() = "Stockfish 19"

    private const val TAG = "StockfishEngine"
    private const val LIB_NAME = "libstockfish.so"

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerThread: Thread? = null
    private val lineListeners = Collections.synchronizedList(mutableListOf<(String) -> Unit>())
    private val pendingLock = Any()
    private var pendingAnalysis: CompletableFuture<EngineAnalysis>? = null
    private val mvBuckets = Collections.synchronizedMap(mutableMapOf<Int, TopMove>())
    private val pvBuckets = Collections.synchronizedMap(mutableMapOf<Int, String>())
    private var currentDepth = 0
    private var pendingSideToMoveWhite = true
    private var binaryPath: String? = null

    /** Locate (and if needed extract) the engine binary. */
    fun init(context: Context) {
        val native = File(context.applicationInfo.nativeLibraryDir, LIB_NAME)
        val legacy = File(context.filesDir, "stockfish")
        binaryPath = when {
            native.exists() && native.canExecute() -> native.absolutePath
            native.exists() -> {
                native.setExecutable(true, false)
                native.absolutePath
            }
            legacy.exists() -> {
                legacy.setExecutable(true, false)
                legacy.absolutePath
            }
            else -> {
                // Last resort: copy from assets (legacy layout).
                try {
                    context.assets.open("stockfish").use { input ->
                        FileOutputStream(legacy).use { out -> input.copyTo(out) }
                    }
                    legacy.setExecutable(true, false)
                    legacy.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /** True when a usable engine binary exists on this device. */
    fun isAvailable(): Boolean {
        val p = binaryPath ?: return false
        return File(p).exists()
    }

    private fun ensureStarted(threads: Int, hashMb: Int) {
        synchronized(pendingLock) {
            if (process != null) return
            val path = binaryPath ?: throw IllegalStateException("engine binary not found")
            val proc = ProcessBuilder(path)
                .redirectErrorStream(true)
                .start()
            process = proc
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream, "UTF-8"))

            val t = Thread({
                try {
                    val rd = BufferedReader(InputStreamReader(proc.inputStream, "UTF-8"))
                    var line: String?
                    while (rd.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        synchronized(pendingLock) {
                            handleLine(trimmed)
                            lineListeners.toList().forEach { it(trimmed) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "stockfish reader ended: ${e.message}")
                }
            }, "stockfish-reader")
            t.isDaemon = true
            t.start()
            readerThread = t

            send("uci")
            waitFor("uciok", 5000)
            send("isready")
            waitFor("readyok", 5000)
            send("setoption name Threads value ${threads.coerceIn(1, 8)}")
            send("setoption name Hash value ${hashMb.coerceIn(16, 512)}")
            send("isready")
            waitFor("readyok", 5000)
            Log.i(TAG, "started from $path (threads=$threads hash=$hashMb)")
        }
    }

    private fun handleLine(line: String) {
        if (line.startsWith("bestmove")) {
            val parts = line.split(Regex("\\s+"))
            val bestUci = parts.getOrElse(1) { "" }
            val ponder = if (parts.size >= 4 && parts[2] == "ponder") parts[3] else ""
            val sorted = mvBuckets.entries.sortedWith(Cmp.by { it.key })
            val topMoves = sorted.mapIndexed { i, kv ->
                kv.value.copy(rank = i + 1, pv = pvBuckets[kv.key] ?: kv.value.pv)
            }
            // Make sure the engine's chosen move is present even if no info arrived.
            val finalMoves = if (topMoves.isEmpty() && bestUci != "(none)") {
                listOf(TopMove(1, bestUci, bestUci, 0, null, currentDepth, ponder))
            } else {
                topMoves.map {
                    if (it.uci == bestUci && ponder.isNotEmpty() && it.pv.isEmpty())
                        it.copy(pv = ponder) else it
                }
            }
            val analysis = EngineAnalysis(finalMoves, finalMoves.firstOrNull(), currentDepth, ponder)
            val pending = pendingAnalysis
            pendingAnalysis = null
            mvBuckets.clear()
            pvBuckets.clear()
            currentDepth = 0
            pending?.complete(analysis)
        } else if (line.startsWith("info") && line.contains(" pv ")) {
            val multipv = intAfter("multipv", line) ?: 1
            val depth = intAfter("depth", line) ?: 0
            if (depth > currentDepth) currentDepth = depth
            val pvText = line.substring(line.indexOf(" pv ") + 4).trim()
            val firstMove = pvText.split(Regex("\\s+")).firstOrNull() ?: ""
            if (firstMove.isEmpty() || firstMove == "(none)") return

            val cp = intAfter("score cp", line)
            val mate = intAfter("score mate", line)
            // UCI reports from the side to move — convert to white POV.
            val sign = if (pendingSideToMoveWhite) 1 else -1
            mvBuckets[multipv] = TopMove(
                rank = multipv,
                uci = firstMove,
                san = firstMove,
                evaluationCp = cp?.let { it * sign },
                mateIn = mate?.let { it * sign },
                depth = currentDepth
            )
            pvBuckets[multipv] = pvText
        }
    }

    private fun intAfter(prefix: String, line: String): Int? {
        val idx = line.indexOf(prefix)
        if (idx < 0) return null
        val rest = line.substring(idx + prefix.length).trimStart()
        val m = Regex("^(-?\\d+)").find(rest)
        return m?.groups?.get(1)?.value?.toIntOrNull()
    }

    private fun send(command: String) {
        synchronized(pendingLock) {
            val w = writer ?: return
            try {
                w.write(command)
                w.newLine()
                w.flush()
            } catch (e: Exception) {
                Log.w(TAG, "send failed: ${e.message}")
            }
        }
    }

    private fun waitFor(expected: String, timeoutMs: Long): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        val temp: (String) -> Unit = { l -> if (l.contains(expected)) latch.countDown() }
        lineListeners.add(temp)
        val ok = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            lineListeners.remove(temp)
        }
        return ok
    }

    override fun analyze(
        fen: String,
        multiPV: Int,
        depth: Int,
        movetimeMs: Int,
        onResult: (EngineAnalysis) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        Thread({
            try {
                val cfg = ConfigHolder
                ensureStarted(cfg.threads, cfg.hashMb)
                val future: CompletableFuture<EngineAnalysis>
                synchronized(pendingLock) {
                    // Abandon any previous analysis.
                    pendingAnalysis?.complete(EngineAnalysis(emptyList(), null, 0))
                    future = CompletableFuture()
                    pendingAnalysis = future
                    mvBuckets.clear()
                    pvBuckets.clear()
                    currentDepth = 0
                    // FEN ends with move counters, not with the side field —
                    // parse it properly or every black-to-move eval is flipped.
                    pendingSideToMoveWhite =
                        fen.trim().split(Regex("\\s+")).getOrElse(1) { "w" } != "b"
                }
                send("stop")
                send("setoption name MultiPV value ${multiPV.coerceIn(1, 5)}")
                send("position fen $fen")
                send("go depth ${depth.coerceIn(1, 30)} movetime ${movetimeMs.coerceIn(50, 15000)}")
                val analysis = future.get(30, TimeUnit.SECONDS)
                onResult(analysis)
            } catch (e: Exception) {
                onError(e)
            }
        }, "stockfish-analyze").apply { isDaemon = true; start() }
    }

    override fun stop() {
        send("stop")
    }

    override fun destroy() {
        synchronized(pendingLock) {
            try { send("quit") } catch (_: Exception) {}
            readerThread?.interrupt()
            readerThread = null
            try { writer?.close() } catch (_: Exception) {}
            writer = null
            process?.destroyForcibly()
            process = null
            pendingAnalysis?.complete(EngineAnalysis(emptyList(), null, 0))
            pendingAnalysis = null
            mvBuckets.clear()
            pvBuckets.clear()
        }
    }

    /** Engine thread/hash settings, filled in by the view model from preferences. */
    object ConfigHolder {
        @Volatile var threads: Int = 3
        @Volatile var hashMb: Int = 64
    }
}
