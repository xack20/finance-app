package app.hisaab.mac

import app.hisaab.bridge.BridgeTurn
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeCliTest {

    private class Recorder(private val output: String, private val exitCode: Int = 0) {
        var command: List<String> = emptyList()
        var env: Map<String, String?> = emptyMap()
        var stdin = ""
        var systemPrompt = ""

        fun run(command: List<String>, workDir: File, stdin: String, env: Map<String, String?>, timeoutMs: Long): ProcessResult {
            this.command = command
            this.env = env
            this.stdin = stdin
            val i = command.indexOf("--system-prompt-file")
            systemPrompt = File(command[i + 1]).readText()
            return ProcessResult(exitCode, output, timedOut = false)
        }
    }

    private fun cli(recorder: Recorder) = ClaudeCli(
        executable = { "/fake/claude" },
        run = recorder::run,
        workDir = kotlin.io.path.createTempDirectory("bridge-test").toFile(),
    )

    private val turns = listOf(
        BridgeTurn("user", "Messages: [1] Tk 500"),
        BridgeTurn("assistant", """{"action":{"tool":"list_accounts"}}"""),
        BridgeTurn("user", "[]"),
    )

    @Test
    fun `the call runs on the subscription with no tools, settings or saved session`() {
        val r = Recorder("""{"type":"result","is_error":false,"result":"ok"}""")
        cli(r).complete("SYSTEM", turns)

        assertEquals("/fake/claude", r.command.first())
        listOf("-p", "--strict-mcp-config", "--no-session-persistence").forEach { assertTrue(it in r.command, it) }
        assertEquals("json", r.command[r.command.indexOf("--output-format") + 1])
        assertEquals("", r.command[r.command.indexOf("--tools") + 1])
        assertEquals("", r.command[r.command.indexOf("--setting-sources") + 1])
        // An API key in the environment would bill it instead of the subscription.
        assertTrue(r.env.containsKey("ANTHROPIC_API_KEY") && r.env["ANTHROPIC_API_KEY"] == null)
        assertTrue(r.env.containsKey("ANTHROPIC_AUTH_TOKEN") && r.env["ANTHROPIC_AUTH_TOKEN"] == null)
        assertEquals("SYSTEM", r.systemPrompt)
    }

    @Test
    fun `the conversation is replayed in order and Claude is asked for the next reply only`() {
        val r = Recorder("""{"type":"result","is_error":false,"result":"ok"}""")
        cli(r).complete("SYSTEM", turns)

        val a = r.stdin.indexOf("Messages: [1] Tk 500")
        val b = r.stdin.indexOf("list_accounts")
        val c = r.stdin.lastIndexOf("[]")
        assertTrue(a in 0 until b && b < c, r.stdin)
        assertTrue("next message" in r.stdin.lowercase())
    }

    @Test
    fun `Claude's reply text comes back`() {
        val reply = cli(Recorder("""{"type":"result","is_error":false,"result":"{\"final\":{}}"}""")).complete("S", turns)
        assertEquals("""{"final":{}}""", reply.text)
        assertNull(reply.error)
    }

    @Test
    fun `hitting the subscription limit is reported as a limit`() {
        val reply = cli(Recorder("""{"type":"result","is_error":true,"result":"Claude AI usage limit reached|1760700000"}"""))
            .complete("S", turns)
        assertTrue(reply.limitReached)
        assertNull(reply.text)
    }

    @Test
    fun `a CLI failure is an error, not a crash`() {
        val reply = cli(Recorder("Invalid API key · Please run /login", exitCode = 1)).complete("S", turns)
        assertNotNull(reply.error)
        assertFalse(reply.limitReached)
    }

    @Test
    fun `no Claude Code installed is reported`() {
        val reply = ClaudeCli(executable = { null }, run = { _, _, _, _, _ -> error("must not run") }).complete("S", turns)
        assertTrue(reply.error.orEmpty().contains("Claude Code"))
    }
}
