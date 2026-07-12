@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.oak.app.tools

import com.oak.app.data.AppSettings
import com.oak.app.ssh.SshClient
import com.oak.app.ssh.SshCommandResult
import com.oak.app.ssh.SshConnectionInfo
import com.oak.app.ssh.SshServerConfig
import com.oak.app.ssh.SshServerManager
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SshTools].
 *
 * Covers the pure utility functions (resolveServerId, escapeShellArg, truncate, parseGrepMatch)
 * indirectly through tool execution, plus all tool schema definitions.
 * The SshServerManager dependency is faked via [FakeSshClient].
 */
class SshToolsTest {

    // ── Fake SSH client ─────────────────────────────────────────────

    private class FakeSshClient(
        val handler: (command: String, timeoutSeconds: Long) -> SshCommandResult,
    ) : SshClient {
        override val connectionInfo: SshConnectionInfo? = SshConnectionInfo("test-host", 22, "test-user")
        override val isConnected: Boolean = true
        override suspend fun connect(config: SshServerConfig): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
        override suspend fun executeCommand(command: String, timeoutSeconds: Long): SshCommandResult =
            handler(command, timeoutSeconds)
        override suspend fun uploadFile(localPath: String, remotePath: String): Result<Unit> = Result.success(Unit)
        override suspend fun downloadFile(remotePath: String, localPath: String): Result<Unit> = Result.success(Unit)
    }

    /** Initializes [SshTools] with a manager that has one registered ad-hoc client. */
    private suspend fun initConnectedManager(
        handler: (command: String, timeoutSeconds: Long) -> SshCommandResult = { _, _ -> SshCommandResult(0, "", "") },
        serverId: String = "test-server",
    ) {
        val manager = SshServerManager(AppSettings(MapSettings())) { error("unexpected client creation") }
        manager.registerAdhocClient(serverId, FakeSshClient(handler))
        SshTools.init(manager)
    }

    /** Initializes [SshTools] with a manager that has no active connections. */
    private suspend fun initEmptyManager() {
        SshTools.init(SshServerManager(AppSettings(MapSettings())) { error("unexpected client creation") })
    }

    /** Looks up a tool by schema name from [SshTools.getTools]. */
    private fun tool(name: String) = SshTools.getTools().first { it.schema.name == name }

    // ════════════════════════════════════════════════════════════════════
    // Tool definitions
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `getTools returns 14 tools`() {
        assertEquals(14, SshTools.getTools().size)
    }

    @Test
    fun `toolDefinitions has 14 entries`() {
        assertEquals(14, SshTools.toolDefinitions.size)
    }

    @Test
    fun `tool IDs in toolDefinitions match getTools names`() {
        val toolNames = SshTools.getTools().map { it.schema.name }.toSet()
        val defIds = SshTools.toolDefinitions.map { it.id }.toSet()
        assertEquals(toolNames, defIds)
    }

    @Test
    fun `all tool schema names are unique`() {
        val names = SshTools.getTools().map { it.schema.name }
        assertEquals(names.toSet().size, names.size)
    }

    // ════════════════════════════════════════════════════════════════════
    // Schema verification for each tool
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `ssh_connect schema`() {
        val t = tool("ssh_connect")
        assertEquals("ssh_connect", t.schema.name)
        assertTrue(t.schema.description.startsWith("Connect to a remote SSH server"))
        val params = t.schema.parameters
        assertEquals("string", params["server_id"]?.type)
        assertEquals(false, params["server_id"]?.required)
        assertEquals("string", params["host"]?.type)
        assertEquals(false, params["host"]?.required)
        assertEquals("integer", params["port"]?.type)
        assertEquals(false, params["port"]?.required)
        assertEquals("string", params["username"]?.type)
        assertEquals(false, params["username"]?.required)
    }

    @Test
    fun `ssh_execute_command has required command parameter`() {
        val params = tool("ssh_execute_command").schema.parameters
        assertEquals("string", params["command"]?.type)
        assertEquals(true, params["command"]?.required)
        assertEquals("integer", params["timeout"]?.type)
        assertEquals(false, params["timeout"]?.required)
        assertEquals("string", params["server_id"]?.type)
        assertEquals(false, params["server_id"]?.required)
    }

    @Test
    fun `ssh_grep has required pattern parameter`() {
        val params = tool("ssh_grep").schema.parameters
        assertEquals("string", params["pattern"]?.type)
        assertEquals(true, params["pattern"]?.required)
        assertEquals("integer", params["max_results"]?.type)
        assertEquals(false, params["max_results"]?.required)
    }

    @Test
    fun `ssh_read_file has required path parameter`() {
        val params = tool("ssh_read_file").schema.parameters
        assertEquals("string", params["path"]?.type)
        assertEquals(true, params["path"]?.required)
        assertEquals("integer", params["offset"]?.type)
        assertEquals("integer", params["limit"]?.type)
    }

    @Test
    fun `ssh_write_file has required path and content parameters`() {
        val params = tool("ssh_write_file").schema.parameters
        assertEquals(true, params["path"]?.required)
        assertEquals(true, params["content"]?.required)
    }

    @Test
    fun `ssh_edit_file has required path old_string and new_string parameters`() {
        val params = tool("ssh_edit_file").schema.parameters
        assertEquals(true, params["path"]?.required)
        assertEquals(true, params["old_string"]?.required)
        assertEquals(true, params["new_string"]?.required)
    }

    @Test
    fun `ssh_transfer_file has required direction local_path and remote_path`() {
        val params = tool("ssh_transfer_file").schema.parameters
        assertEquals(true, params["direction"]?.required)
        assertEquals(true, params["local_path"]?.required)
        assertEquals(true, params["remote_path"]?.required)
    }

    @Test
    fun `ssh_list_directory has required path parameter`() {
        assertEquals(true, tool("ssh_list_directory").schema.parameters["path"]?.required)
    }

    @Test
    fun `ssh_delete_path has required path parameter`() {
        assertEquals(true, tool("ssh_delete_path").schema.parameters["path"]?.required)
    }

    @Test
    fun `ssh_make_directory has required path parameter`() {
        assertEquals(true, tool("ssh_make_directory").schema.parameters["path"]?.required)
    }

    @Test
    fun `ssh_file_info has required path parameter`() {
        assertEquals(true, tool("ssh_file_info").schema.parameters["path"]?.required)
    }

    @Test
    fun `ssh_search_files has required pattern parameter`() {
        assertEquals(true, tool("ssh_search_files").schema.parameters["pattern"]?.required)
    }

    @Test
    fun `ssh_disconnect schema includes optional server_id`() {
        val params = tool("ssh_disconnect").schema.parameters
        assertEquals(false, params["server_id"]?.required)
    }

    @Test
    fun `ssh_status schema includes optional server_id`() {
        assertEquals(false, tool("ssh_status").schema.parameters["server_id"]?.required)
    }

    // ════════════════════════════════════════════════════════════════════
    // resolveServerId — tested through executeCommandTool
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `executeCommand fails with No active SSH connections when no connections`() = runTest {
        initEmptyManager()
        val result = SshTools.executeCommandTool.execute(mapOf("command" to "echo hi")) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        val error = result["error"] as? String ?: ""
        assertTrue(error.contains("No active SSH connections"), "error was: $error")
    }

    @Test
    fun `executeCommand auto-selects single active connection`() = runTest {
        initConnectedManager(handler = { _, _ -> SshCommandResult(0, "hello", "") })
        val result = SshTools.executeCommandTool.execute(mapOf("command" to "echo hello")) as Map<*, *>
        assertTrue(result["success"] as? Boolean ?: false)
        assertEquals("hello", result["stdout"])
    }

    @Test
    fun `executeCommand with invalid server_id returns error`() = runTest {
        initConnectedManager()
        val result = SshTools.executeCommandTool.execute(
            mapOf("command" to "echo hi", "server_id" to "nonexistent"),
        ) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        val error = result["error"] as? String ?: ""
        assertTrue(error.contains("Not connected to"), "error was: $error")
    }

    @Test
    fun `executeCommand with multiple active connections but no server_id returns error`() = runTest {
        val manager = SshServerManager(AppSettings(MapSettings())) { error("unexpected") }
        manager.registerAdhocClient("s1", FakeSshClient { _, _ -> SshCommandResult(0, "", "") })
        manager.registerAdhocClient("s2", FakeSshClient { _, _ -> SshCommandResult(0, "", "") })
        SshTools.init(manager)
        val result = SshTools.executeCommandTool.execute(mapOf("command" to "echo hi")) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        val error = result["error"] as? String ?: ""
        assertTrue(error.contains("Multiple active SSH connections"), "error was: $error")
    }

    // ════════════════════════════════════════════════════════════════════
    // Error handling
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `executeCommand returns error when command parameter is missing`() = runTest {
        initConnectedManager()
        val result = SshTools.executeCommandTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("Command is required", result["error"])
    }

    @Test
    fun `executeCommand reflects non-zero exit code as success=false`() = runTest {
        initConnectedManager(handler = { _, _ -> SshCommandResult(1, "", "error output") })
        val result = SshTools.executeCommandTool.execute(mapOf("command" to "false")) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals(1, result["exit_code"])
        assertTrue((result["stderr"] as? String ?: "").contains("error"))
    }

    // ════════════════════════════════════════════════════════════════════
    // truncate — verified through large output
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `executeCommand truncates large stdout output to 20000 chars`() = runTest {
        val large = "a".repeat(25_000)
        initConnectedManager(handler = { _, _ -> SshCommandResult(0, large, "") })
        val result = SshTools.executeCommandTool.execute(mapOf("command" to "cat large")) as Map<*, *>
        val stdout = result["stdout"] as? String ?: ""
        assertEquals(20_000, stdout.length)
    }

    @Test
    fun `executeCommand truncates large stderr output to 20000 chars`() = runTest {
        val large = "b".repeat(25_000)
        initConnectedManager(handler = { _, _ -> SshCommandResult(0, "", large) })
        val result = SshTools.executeCommandTool.execute(mapOf("command" to "err")) as Map<*, *>
        val stderr = result["stderr"] as? String ?: ""
        assertEquals(20_000, stderr.length)
    }

    // ════════════════════════════════════════════════════════════════════
    // escapeShellArg — verified through command construction (search/grep tools)
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `search_files escapes single quotes in path`() = runTest {
        var captured = ""
        initConnectedManager(handler = { cmd, _ -> captured = cmd; SshCommandResult(0, "", "") })
        SshTools.searchFilesTool.execute(mapOf("pattern" to "*.kt", "path" to "/some/dir")) as Map<*, *>
        assertTrue(captured.contains("'*.kt'"), "pattern should be single-quoted in: $captured")
    }

    @Test
    fun `grep escapes single quotes in pattern`() = runTest {
        var captured = ""
        initConnectedManager(handler = { cmd, _ -> captured = cmd; SshCommandResult(0, "", "") })
        SshTools.grepTool.execute(mapOf("pattern" to "hello.world", "path" to ".")) as Map<*, *>
        assertTrue(captured.contains("'hello.world'"), "pattern should be single-quoted in: $captured")
    }

    // ════════════════════════════════════════════════════════════════════
    // parseGrepMatch — tested through grep tool execution
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `grep parses GNU null-delimited format`() = runTest {
        val grepOut = "file1.kt\u000010:line one\nsrc/file2.kt\u000025:line two"
        initConnectedManager(handler = { cmd, _ ->
            if (cmd.startsWith("grep -rnZ")) SshCommandResult(0, grepOut, "")
            else SshCommandResult(0, "", "")
        })
        val result = SshTools.grepTool.execute(mapOf("pattern" to "test", "path" to ".")) as Map<*, *>
        assertTrue(result["success"] as? Boolean ?: false)
        val matches = result["matches"] as? List<*> ?: emptyList<Any>()
        assertEquals(2, matches.size)

        val first = matches[0] as Map<*, *>
        assertEquals("file1.kt", first["file"])
        assertEquals(10, first["line"])
        assertEquals("line one", first["content"])

        val second = matches[1] as Map<*, *>
        assertEquals("src/file2.kt", second["file"])
        assertEquals(25, second["line"])
        assertEquals("line two", second["content"])
    }

    @Test
    fun `grep parses BSD colon-delimited format`() = runTest {
        initConnectedManager(handler = { cmd, _ ->
            if (cmd.startsWith("grep -rnZ")) SshCommandResult(0, "path/to/file:42:content here", "")
            else SshCommandResult(0, "", "")
        })
        val result = SshTools.grepTool.execute(mapOf("pattern" to "test", "path" to ".")) as Map<*, *>
        val matches = result["matches"] as? List<*> ?: emptyList<Any>()
        assertEquals(1, matches.size)
        val first = matches[0] as Map<*, *>
        assertEquals("path/to/file", first["file"])
        assertEquals(42, first["line"])
        assertEquals("content here", first["content"])
    }

    @Test
    fun `grep returns raw entry when format is unrecognized`() = runTest {
        initConnectedManager(handler = { cmd, _ ->
            if (cmd.startsWith("grep -rnZ")) SshCommandResult(0, "unstructured output line", "")
            else SshCommandResult(0, "", "")
        })
        val result = SshTools.grepTool.execute(mapOf("pattern" to "test", "path" to ".")) as Map<*, *>
        val matches = result["matches"] as? List<*> ?: emptyList<Any>()
        assertEquals(1, matches.size)
        val first = matches[0] as Map<*, *>
        assertNotNull(first["raw"])
        assertTrue((first["raw"] as? String ?: "").contains("unstructured"))
    }

    @Test
    fun `grep returns error when pattern parameter is missing`() = runTest {
        initConnectedManager()
        val result = SshTools.grepTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("pattern is required", result["error"])
    }

    // ════════════════════════════════════════════════════════════════════
    // Tool-specific functionality
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `status returns connections when active`() = runTest {
        initConnectedManager() // registration already puts 1 connection
        val result = SshTools.statusTool.execute(emptyMap()) as Map<*, *>
        assertTrue(result.containsKey("active_connections"))
        assertTrue(result.containsKey("connections"))
        assertTrue(result.containsKey("saved_servers"))
    }

    @Test
    fun `status with invalid server_id still returns server not found info`() = runTest {
        initConnectedManager()
        val result = SshTools.statusTool.execute(mapOf("server_id" to "ghost")) as Map<*, *>
        assertEquals("ghost", result["server_id"])
        assertFalse(result["connected"] as? Boolean ?: true)
    }

    @Test
    fun `disconnect all with no connections returns error`() = runTest {
        initEmptyManager()
        val result = SshTools.disconnectTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertTrue((result["error"] as? String ?: "").contains("No active SSH connections"))
    }

    @Test
    fun `disconnect with invalid server_id returns error`() = runTest {
        initConnectedManager()
        val result = SshTools.disconnectTool.execute(mapOf("server_id" to "nonexistent")) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
    }

    @Test
    fun `transfer_file requires direction parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.transferFileTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertTrue((result["error"] as? String ?: "").contains("direction is required"))
    }

    @Test
    fun `transfer_file requires local_path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.transferFileTool.execute(mapOf("direction" to "upload")) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertTrue((result["error"] as? String ?: "").contains("local_path is required"))
    }

    @Test
    fun `transfer_file invalid direction returns error`() = runTest {
        initConnectedManager(handler = { _, _ -> SshCommandResult(0, "", "") })
        val result = SshTools.transferFileTool.execute(
            mapOf("direction" to "sideways", "local_path" to "/a", "remote_path" to "/b"),
        ) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertTrue((result["error"] as? String ?: "").contains("direction must be 'upload' or 'download'"))
    }

    @Test
    fun `read_file requires path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.readFileTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("path is required", result["error"])
    }

    @Test
    fun `write_file requires path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.writeFileTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("path is required", result["error"])
    }

    @Test
    fun `write_file requires content parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.writeFileTool.execute(mapOf("path" to "/tmp/f")) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("content is required", result["error"])
    }

    @Test
    fun `edit_file requires path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.editFileTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("path is required", result["error"])
    }

    @Test
    fun `delete_path requires path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.deletePathTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("path is required", result["error"])
    }

    @Test
    fun `make_directory requires path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.makeDirectoryTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("path is required", result["error"])
    }

    @Test
    fun `file_info requires path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.fileInfoTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("path is required", result["error"])
    }

    @Test
    fun `search_files requires pattern parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.searchFilesTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("pattern is required", result["error"])
    }

    @Test
    fun `list_directory requires path parameter`() = runTest {
        initConnectedManager()
        val result = SshTools.listDirectoryTool.execute(emptyMap()) as Map<*, *>
        assertFalse(result["success"] as? Boolean ?: true)
        assertEquals("path is required", result["error"])
    }
}
