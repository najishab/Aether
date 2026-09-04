package com.najishab.aether.core

import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject

data class ScoutResult(
    val endpoint: String,
    val ip: String,
    val working: Boolean,
    val endpointPingMs: Double,
    val tunPingMs: Double,
    val tunPingMeasured: Boolean,
    val lossPct: Double,
    val seenAs: String,
    val seenAsIso: String,
    val node: String,
    val nodeLocation: String,
)

data class ScoutReport(
    val proto: String,
    val workingCount: Int,
    val probedCount: Int,
    val results: List<ScoutResult>,
)

data class ScoutOptions(
    val protocol: ScoutProtocol,
    val mode: ScoutMode,
    val ipVersion: ScoutIpVersion,
    val port: Int? = null,
    val genI1: ScoutI1Profile? = null,
    val timeoutSec: Int = 5,
    val parallelJobs: Int = 50,
)

enum class ScoutProtocol { WIREGUARD, AMNEZIAWG, MASQUE, MASQUE_H2 }
enum class ScoutI1Profile { QUIC, DNS, SIP, STUN, RANDOM }
enum class ScoutMode { STANDARD, DURABLE, FULL }
enum class ScoutIpVersion { V4, V6, BOTH }

sealed class ScoutOutcome {
    data class Progress(val line: String) : ScoutOutcome()
    data class Done(val report: ScoutReport) : ScoutOutcome()
    data class Failed(val message: String) : ScoutOutcome()
}

class WarpScoutRunner(
    private val nativeLibDir: String,
    private val workingDir: File,
) {
    private val accountFile = File(workingDir, "warpscout-account.json")
    private var scanProcess: Process? = null

    private fun binary(): File {
        val bin = File(nativeLibDir, "libwarpscout.so")
        if (!bin.exists()) {
            throw IllegalStateException("ERR_BINARY_MISSING")
        }
        return bin
    }

    private fun newProcessBuilder(args: List<String>): ProcessBuilder {
        val command = mutableListOf(binary().absolutePath).apply { addAll(args) }
        return ProcessBuilder(command)
            .directory(workingDir)
            .also {
                it.environment()["HOME"] = workingDir.absolutePath
                it.environment()["TMPDIR"] = workingDir.absolutePath
            }
    }

    fun isRegistered(): Boolean = accountFile.exists() && accountFile.length() > 0L

    fun ensureRegistered(): Result<Unit> {
        if (isRegistered()) return Result.success(Unit)
        return runCatching {
            val args = listOf("register", "-plain", "-a", accountFile.absolutePath)
            val proc = newProcessBuilder(args).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            val exited = proc.waitFor(30, TimeUnit.SECONDS)
            DiagnosticsLog.i("scout", "register: $output")
            if (!exited) {
                proc.destroyForcibly()
                throw IllegalStateException("ERR_REG_TIMEOUT")
            }
            if (proc.exitValue() != 0 || !isRegistered()) {
                throw IllegalStateException("ERR_REG_FAILED:${output.trim().takeLast(200)}")
            }
        }
    }

    private fun buildArgs(opts: ScoutOptions): List<String> {
        val args = mutableListOf(
            "scan", "-json", "-plain",
            "-a", accountFile.absolutePath,
            "-p", when (opts.protocol) {
                ScoutProtocol.WIREGUARD -> "wg"
                ScoutProtocol.AMNEZIAWG -> "awg"
                ScoutProtocol.MASQUE -> "masque"
                ScoutProtocol.MASQUE_H2 -> "masque-h2"
            },
        )
        if (opts.protocol == ScoutProtocol.AMNEZIAWG && opts.genI1 != null) {
            args += listOf(
                "-gen-i1",
                when (opts.genI1) {
                    ScoutI1Profile.QUIC -> "quic"
                    ScoutI1Profile.DNS -> "dns"
                    ScoutI1Profile.SIP -> "sip"
                    ScoutI1Profile.STUN -> "stun"
                    ScoutI1Profile.RANDOM -> "random"
                },
            )
        }

        when (opts.mode) {
            ScoutMode.STANDARD -> Unit
            ScoutMode.DURABLE -> args += listOf("-P", "-tun-ping-count", "10")
            ScoutMode.FULL -> args += listOf("-f", "-P", "-tun-ping-count", "10")
        }

        if (opts.ipVersion == ScoutIpVersion.V6) {
            args += "-6"
        }

        opts.port?.let { args += listOf("-port", it.toString()) }

        if (opts.timeoutSec > 0) {
            args += listOf("-timeout", opts.timeoutSec.toString())
        }
        if (opts.parallelJobs > 0) {
            args += listOf("-tunnel-jobs", opts.parallelJobs.toString())
        }

        return args
    }

    private fun timeoutForMode(opts: ScoutOptions): Long {
        val base = when (opts.mode) {
            ScoutMode.STANDARD -> 60_000L
            ScoutMode.DURABLE -> 120_000L
            ScoutMode.FULL -> 300_000L
        }
        return base + (opts.timeoutSec * 1000L * 5)
    }

    fun cancel() {
        val proc = scanProcess ?: return
        scanProcess = null
        runCatching {
            proc.destroy()
            if (!proc.waitFor(300, TimeUnit.MILLISECONDS)) proc.destroyForcibly()
        }
    }

    /**
     * اجرای اسکن همراه با ارسال زنده درصد پیشرفت و سرورهای پیدا شده
     */
    fun scan(
        opts: ScoutOptions,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onEndpointFound: (ScoutResult) -> Unit = {},
    ): Result<ScoutReport> = runCatching {
        val args = buildArgs(opts)
        val proc = newProcessBuilder(args).start()
        scanProcess = proc
        DiagnosticsLog.i("scout", "scan args: ${args.joinToString(" ")}")

        val stderrLines = mutableListOf<String>()
        val stderrThread = Thread({
            runCatching {
                proc.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(stderrLines) { stderrLines.add(line) }
                        when {
                            // درصد پیشرفت زنده
                            line.startsWith("PROGRESS:") -> {
                                val parts = line.removePrefix("PROGRESS:").split(":")
                                if (parts.size >= 2) {
                                    val done = parts[0].toIntOrNull() ?: 0
                                    val total = parts[1].toIntOrNull() ?: 0
                                    onProgress(done, total)
                                }
                            }
                            // اضافه شدن زنده سرورها
                            line.startsWith("ENDPOINT:") -> {
                                val jsonStr = line.removePrefix("ENDPOINT:")
                                runCatching {
                                    val e = JSONObject(jsonStr)
                                    val res = ScoutResult(
                                        endpoint = e.getString("endpoint"),
                                        ip = e.optString("ip"),
                                        working = e.optString("status") == "working",
                                        endpointPingMs = e.optDouble("endpointPingMs", 0.0),
                                        tunPingMs = e.optDouble("tunPingMs", 0.0),
                                        tunPingMeasured = e.optBoolean("tunPingMeasured", false),
                                        lossPct = e.optDouble("lossPct", 0.0),
                                        seenAs = e.optString("seenAs"),
                                        seenAsIso = e.optString("seenAsIso"),
                                        node = e.optString("node"),
                                        nodeLocation = e.optString("nodeLocation"),
                                    )
                                    onEndpointFound(res)
                                }
                            }
                        }
                    }
                }
            }
        }, "warpscout-stderr").apply { isDaemon = true; start() }

        val stdout = proc.inputStream.bufferedReader().readText()
        val exited = proc.waitFor(timeoutForMode(opts), TimeUnit.MILLISECONDS)
        stderrThread.join(1000)
        scanProcess = null

        val stderrText = synchronized(stderrLines) { stderrLines.joinToString("\n") }
        DiagnosticsLog.i(
            "scout",
            "scan exit=${if (exited) proc.exitValue() else "timeout"} stderr=$stderrText stdout=${stdout.take(500)}",
        )

        if (!exited) {
            proc.destroyForcibly()
            throw IllegalStateException("ERR_SCAN_TIMEOUT")
        }

        // شناسایی خطای فیلتر بودن UDP در پروتکل MASQUE
        if (stderrText.contains("no masque endpoint passed data", ignoreCase = true) ||
            stderrText.contains("block", ignoreCase = true)
        ) {
            throw IllegalStateException("ERR_UDP_BLOCKED")
        }

        if (proc.exitValue() != 0 && stdout.isBlank()) {
            val detail = stderrText.trim().takeLast(300).ifBlank { "no output" }
            throw IllegalStateException("ERR_SCAN_FAILED:$detail")
        }
        parseReport(stdout)
    }

    private fun parseReport(json: String): ScoutReport {
        val root = JSONObject(json)
        val endpoints = root.getJSONArray("endpoints")
        val results = buildList {
            for (i in 0 until endpoints.length()) {
                val e = endpoints.getJSONObject(i)
                add(
                    ScoutResult(
                        endpoint = e.getString("endpoint"),
                        ip = e.optString("ip"),
                        working = e.optString("status") == "working",
                        endpointPingMs = e.optDouble("endpointPingMs", 0.0),
                        tunPingMs = e.optDouble("tunPingMs", 0.0),
                        tunPingMeasured = e.optBoolean("tunPingMeasured", false),
                        lossPct = e.optDouble("lossPct", 0.0),
                        seenAs = e.optString("seenAs"),
                        seenAsIso = e.optString("seenAsIso"),
                        node = e.optString("node"),
                        nodeLocation = e.optString("nodeLocation"),
                    ),
                )
            }
        }
        return ScoutReport(
            proto = root.optString("proto"),
            workingCount = root.optInt("working"),
            probedCount = root.optInt("probed"),
            results = results,
        )
    }
}