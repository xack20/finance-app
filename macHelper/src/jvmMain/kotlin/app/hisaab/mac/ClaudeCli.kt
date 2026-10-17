package app.hisaab.mac

import app.hisaab.bridge.BridgeTurn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** What a finished process printed. */
data class ProcessResult(val exitCode: Int, val stdout: String, val timedOut: Boolean)

/**
 * One Claude Code headless call (`claude -p`) on the user's subscription — the phone's model endpoint when it
 * uses "Claude via my Mac". Runs in an empty folder with no tools, MCP servers, user/project settings (so no
 * hooks or CLAUDE.md) or saved session, and with API-key variables removed so the subscription login is used.
 */
class ClaudeCli(
    private val executable: () -> String? = ::findClaude,
    private val run: (List<String>, File, String, Map<String, String?>, Long) -> ProcessResult = ::runProcess,
    private val workDir: File = appSupportDir("claude-bridge"),
    /** Sonnet keeps bulk message reading well inside subscription limits. */
    private val model: String = "sonnet",
    private val timeoutMs: Long = 5 * 60_000L,
) {
    data class Reply(val text: String?, val error: String?, val limitReached: Boolean = false)

    fun isInstalled(): Boolean = executable() != null

    fun complete(system: String, turns: List<BridgeTurn>): Reply {
        val claude = executable() ?: return Reply(null, "Claude Code isn't installed on this Mac (no `claude` command)")
        workDir.mkdirs()
        val systemFile = File.createTempFile("system-", ".txt", workDir).apply { writeText(system) }
        try {
            val command = listOf(
                claude, "-p",
                "--output-format", "json",
                "--system-prompt-file", systemFile.absolutePath,
                "--tools", "",
                "--strict-mcp-config",
                "--setting-sources", "",
                "--no-session-persistence",
                "--model", model,
            )
            val env = mapOf("ANTHROPIC_API_KEY" to null, "ANTHROPIC_AUTH_TOKEN" to null)
            val result = run(command, workDir, transcript(turns), env, timeoutMs)
            if (result.timedOut) return Reply(null, "Claude Code took longer than ${timeoutMs / 60_000} min")
            return parse(result)
        } finally {
            systemFile.delete()
        }
    }

    /** `claude -p` takes one prompt, so earlier turns are replayed as a transcript. */
    private fun transcript(turns: List<BridgeTurn>): String = buildString {
        appendLine("Below is the conversation so far. Write ONLY the assistant's next message, nothing else.")
        turns.forEach { t ->
            appendLine()
            appendLine("<${t.role}>")
            appendLine(t.content)
            appendLine("</${t.role}>")
        }
    }

    private fun parse(result: ProcessResult): Reply {
        val root = runCatching { Json.parseToJsonElement(result.stdout).jsonObject }.getOrNull()
            ?: return Reply(null, "Claude Code failed (exit ${result.exitCode}): ${result.stdout.take(300)}")
        val text = root["result"]?.jsonPrimitive?.contentOrNull
        val isError = root["is_error"]?.jsonPrimitive?.booleanOrNull == true
        return when {
            isError && text.orEmpty().contains("limit", ignoreCase = true) -> Reply(null, text, limitReached = true)
            isError || text == null -> Reply(null, text ?: "Claude Code returned no reply")
            else -> Reply(text, null)
        }
    }
}

/** `claude` from a Finder-launched app: PATH is minimal there, so look in the usual install places first. */
fun findClaude(): String? {
    val home = System.getProperty("user.home")
    val candidates = listOf(
        "$home/.local/bin/claude", "$home/.claude/local/claude", "/opt/homebrew/bin/claude", "/usr/local/bin/claude",
    )
    candidates.firstOrNull { File(it).canExecute() }?.let { return it }
    return runCatching {
        val p = ProcessBuilder("/bin/zsh", "-lc", "command -v claude").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().trim().takeIf { p.waitFor(10, TimeUnit.SECONDS) && it.startsWith("/") }
    }.getOrNull()
}

/** Runs [command] with [stdin], [env] overrides (null = remove), killing it after [timeoutMs]. */
fun runProcess(command: List<String>, workDir: File, stdin: String, env: Map<String, String?>, timeoutMs: Long): ProcessResult {
    val builder = ProcessBuilder(command).directory(workDir).redirectError(ProcessBuilder.Redirect.DISCARD)
    env.forEach { (k, v) -> if (v == null) builder.environment().remove(k) else builder.environment()[k] = v }
    val process = builder.start()
    var stdout = ""
    val reader = thread(isDaemon = true) { stdout = process.inputStream.bufferedReader().readText() }
    process.outputStream.bufferedWriter().use { it.write(stdin) }
    if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        return ProcessResult(-1, "", timedOut = true)
    }
    reader.join(5_000)
    return ProcessResult(process.exitValue(), stdout, timedOut = false)
}

private fun appSupportDir(name: String): File =
    File(System.getProperty("user.home"), "Library/Application Support/HisaabCompanion/$name")
