package com.n8n.mobile.studio.terminal

import com.n8n.mobile.studio.runtime.EmbeddedComponent
import com.n8n.mobile.studio.runtime.EmbeddedComponentStatus
import com.n8n.mobile.studio.runtime.LogTailer
import com.n8n.mobile.studio.runtime.NodeRuntime
import com.n8n.mobile.studio.runtime.RuntimePaths
import com.n8n.mobile.studio.runtime.RuntimeProcess
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lifecycle hooks the terminal uses to drive the same runtimes the GUI drives. */
interface RuntimeControl {
    fun status(component: EmbeddedComponent): EmbeddedComponentStatus

    suspend fun start(component: EmbeddedComponent): String

    suspend fun stop(component: EmbeddedComponent): String

    suspend fun restart(component: EmbeddedComponent): String
}

/**
 * The terminal front-end's command interpreter.
 *
 * It is intentionally a *fixed command set over the app-owned filesystem*, not a
 * shell: every command is implemented in Kotlin, every path is validated to stay
 * inside the runtime root, and the runtime control commands call the exact same
 * service methods the GUI buttons call. That is what "GUI and terminal talk to
 * the same local runtime" means in practice.
 */
class TerminalEngine(
    private val paths: RuntimePaths,
    private val node: NodeRuntime,
    private val control: RuntimeControl,
    private val environment: TerminalEnvironment = TerminalEnvironment(paths, node),
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun execute(
        input: String,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit = {},
    ): TerminalResult = withContext(Dispatchers.IO) {
        val command = TerminalParser.parse(input)
        emit(session, onEvent, "$ ${input.trim()}", TerminalOutput.Channel.SYSTEM)
        if (command == null) {
            val result = TerminalResult(0)
            session.state.record(result)
            return@withContext result
        }

        val result = try {
            dispatch(command, session, onEvent)
        } catch (t: Throwable) {
            emit(session, onEvent, "${command.name}: ${t.message ?: t.javaClass.simpleName}", TerminalOutput.Channel.STDERR)
            TerminalResult(EXIT_FAILURE)
        }
        session.state.record(result)
        result
    }

    private suspend fun dispatch(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult = when (command.name) {
        "help", "?" -> help(session, onEvent)
        "pwd" -> print(session, onEvent, session.state.displayCwd())
        "cd" -> cd(command, session, onEvent)
        "ls", "ll", "dir" -> ls(command, session, onEvent)
        "cat" -> cat(command, session, onEvent)
        "head" -> head(command, session, onEvent)
        "mkdir" -> mkdir(command, session, onEvent)
        "rm" -> rm(command, session, onEvent)
        "cp" -> copy(command, session, onEvent, move = false)
        "mv" -> copy(command, session, onEvent, move = true)
        "touch" -> touch(command, session, onEvent)
        "echo" -> print(session, onEvent, command.args.joinToString(" "))
        "df" -> df(session, onEvent)
        "env" -> env(command, session, onEvent)
        "ps" -> ps(session, onEvent)
        "clear" -> TerminalResult(0)
        "date" -> print(session, onEvent, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now())))
        "node" -> node(command, session, onEvent)
        "n8n" -> runtimeCommand(EmbeddedComponent.N8N, command, session, onEvent)
        "opencode" -> runtimeCommand(EmbeddedComponent.OPENCODE, command, session, onEvent)
        "exit" -> {
            emit(session, onEvent, "Runtime processes keep running in the background.", TerminalOutput.Channel.SYSTEM)
            session.finish()
            TerminalResult(0)
        }
        else -> {
            emit(
                session,
                onEvent,
                "${command.name}: command not found (type 'help'; this terminal runs a fixed command set, not /bin/sh)",
                TerminalOutput.Channel.STDERR,
            )
            TerminalResult(EXIT_NOT_FOUND)
        }
    }

    // ---------------------------------------------------------------- commands

    private fun help(session: TerminalSession, onEvent: (TerminalOutput) -> Unit): TerminalResult {
        val lines = listOf(
            "N8N Mobile Studio terminal — commands operate on ${paths.root.absolutePath}",
            "",
            "files     ls [-l] [path]   cd <dir>   pwd   cat <file>   head [-n N] <file>",
            "          mkdir [-p] <dir>  rm [-rf] <path>  cp <src> <dst>  mv <src> <dst>  touch <file>",
            "system    df   env [name]   ps   date   clear   help   exit",
            "runtime   node [--version]",
            "          n8n      [start|stop|restart|status|logs [n]]",
            "          opencode [start|stop|restart|status|logs [n]]",
            "",
            "Paths are confined to the app-owned runtime directory; there is no shell piping.",
        )
        lines.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
        return TerminalResult(0, lines)
    }

    private fun cd(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val target = command.operands.firstOrNull()?.let { resolve(session, it) } ?: paths.root
        if (!target.isDirectory) {
            emit(session, onEvent, "cd: ${target.name}: not a directory", TerminalOutput.Channel.STDERR)
            return TerminalResult(EXIT_FAILURE)
        }
        session.state.cd(target)
        return TerminalResult(0)
    }

    private fun ls(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val long = "-l" in command.flags || "--long" in command.flags || command.name == "ll"
        val target = command.operands.firstOrNull()?.let { resolve(session, it) } ?: session.state.cwd
        if (!target.exists()) {
            emit(session, onEvent, "ls: ${command.operands.firstOrNull() ?: target.name}: no such file", TerminalOutput.Channel.STDERR)
            return TerminalResult(EXIT_FAILURE)
        }
        if (target.isFile) {
            val line = formatEntry(target, long)
            emit(session, onEvent, line, TerminalOutput.Channel.STDOUT)
            return TerminalResult(0, listOf(line))
        }
        val children = target.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
        val lines = children.map { formatEntry(it, long) }
        lines.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
        return TerminalResult(0, lines)
    }

    private fun cat(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val path = command.operands.firstOrNull()
            ?: return fail(session, onEvent, "cat: missing file operand")
        val file = resolve(session, path)
        if (!file.isFile) return fail(session, onEvent, "cat: $path: not a file")
        if (file.length() > MAX_CAT_BYTES) {
            return fail(
                session,
                onEvent,
                "cat: $path is ${file.length() / 1024} KiB; use 'head' for large files",
            )
        }
        val lines = file.readLines()
        lines.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
        return TerminalResult(0, lines)
    }

    private fun head(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val operands = command.operands
        val count = command.args.indexOfFirst { it == "-n" }
            .takeIf { it >= 0 }
            ?.let { index -> command.args.getOrNull(index + 1)?.toIntOrNull() }
            ?: DEFAULT_HEAD_LINES
        val path = operands.firstOrNull() ?: return fail(session, onEvent, "head: missing file operand")
        val file = resolve(session, path)
        if (!file.isFile) return fail(session, onEvent, "head: $path: not a file")
        val lines = file.readLines().take(count)
        lines.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
        return TerminalResult(0, lines)
    }

    private fun mkdir(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        if (command.operands.isEmpty()) return fail(session, onEvent, "mkdir: missing operand")
        command.operands.forEach { operand ->
            val dir = resolve(session, operand)
            val created = if ("-p" in command.flags) dir.mkdirs() || dir.isDirectory else dir.mkdir()
            if (!created) return fail(session, onEvent, "mkdir: cannot create ${dir.name}")
        }
        return TerminalResult(0)
    }

    private fun rm(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        if (command.operands.isEmpty()) return fail(session, onEvent, "rm: missing operand")
        val recursive = "-r" in command.flags || "-R" in command.flags || "--recursive" in command.flags
        val force = "-f" in command.flags
        command.operands.forEach { operand ->
            val target = resolve(session, operand)
            if (target == paths.root || target == paths.projects || target == paths.payloads) {
                return fail(session, onEvent, "rm: refusing to delete protected directory ${target.name}")
            }
            if (!target.exists()) {
                if (!force) return fail(session, onEvent, "rm: $operand: no such file")
                return@forEach
            }
            if (target.isDirectory && !recursive) {
                return fail(session, onEvent, "rm: $operand is a directory (use -r)")
            }
            if (!target.deleteRecursively()) return fail(session, onEvent, "rm: cannot delete $operand")
        }
        return TerminalResult(0)
    }

    private fun copy(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
        move: Boolean,
    ): TerminalResult {
        val name = if (move) "mv" else "cp"
        if (command.operands.size < 2) return fail(session, onEvent, "$name: source and destination required")
        val source = resolve(session, command.operands[0])
        val destination = resolve(session, command.operands[1])
        if (!source.exists()) return fail(session, onEvent, "$name: ${command.operands[0]}: no such file")
        val target = if (destination.isDirectory) File(destination, source.name) else destination
        target.parentFile?.mkdirs()
        val ok = if (move) {
            source.renameTo(target)
        } else if (source.isDirectory) {
            source.copyRecursively(target, overwrite = true)
        } else {
            runCatching { source.copyTo(target, overwrite = true) }.isSuccess
        }
        if (!ok) return fail(session, onEvent, "$name: failed to ${if (move) "move" else "copy"} ${source.name}")
        if (move && source.isDirectory) source.deleteRecursively()
        return TerminalResult(0)
    }

    private fun touch(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val path = command.operands.firstOrNull() ?: return fail(session, onEvent, "touch: missing file operand")
        val file = resolve(session, path)
        file.parentFile?.mkdirs()
        val ok = file.exists() || runCatching { file.createNewFile() }.getOrDefault(false)
        return if (ok) TerminalResult(0) else fail(session, onEvent, "touch: cannot create ${file.name}")
    }

    private fun df(session: TerminalSession, onEvent: (TerminalOutput) -> Unit): TerminalResult {
        val lines = listOf(
            "runtime root : ${paths.root.absolutePath}",
            "free space   : ${humanBytes(paths.usableSpaceBytes())}",
            "payloads     : ${humanBytes(directorySize(paths.payloads))}",
            "component data: ${humanBytes(directorySize(paths.data))}",
            "logs         : ${humanBytes(directorySize(paths.logs))}",
        )
        lines.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
        return TerminalResult(0, lines)
    }

    private fun env(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val component = when (command.operands.firstOrNull()) {
            "opencode" -> EmbeddedComponent.OPENCODE
            else -> EmbeddedComponent.N8N
        }
        val values = environment.forComponent(component, heapMb = 256, workingDir = session.state.cwd)
            .toSortedMap()
            .map { (key, value) -> "$key=${mask(key, value)}" }
        values.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
        return TerminalResult(0, values)
    }

    private fun ps(session: TerminalSession, onEvent: (TerminalOutput) -> Unit): TerminalResult {
        val lines = EmbeddedComponent.entries.map { component ->
            val status = control.status(component)
            val alive = status.pid?.let { pid -> RuntimeProcess.isAlive(pid) } ?: false
            buildString {
                append(component.id.padEnd(9))
                append(status.state.name.lowercase().padEnd(14))
                append("pid=")
                append((status.pid?.toString() ?: "-").padEnd(8))
                append(if (alive) "alive " else "      ")
                append(status.endpoint ?: "-")
            }
        }
        lines.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
        return TerminalResult(0, lines)
    }

    private suspend fun node(
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val version = node.probeVersion()
        val text = version.fold(
            onSuccess = { "node v$it (embedded, ${node.abi() ?: "unknown ABI"})" },
            onFailure = { "node: ${it.message}" },
        )
        emit(session, onEvent, text, if (version.isSuccess) TerminalOutput.Channel.STDOUT else TerminalOutput.Channel.STDERR)
        return TerminalResult(if (version.isSuccess) 0 else EXIT_FAILURE, listOf(text))
    }

    private suspend fun runtimeCommand(
        component: EmbeddedComponent,
        command: ParsedCommand,
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
    ): TerminalResult {
        val action = command.operands.firstOrNull() ?: "status"
        return when (action) {
            "start" -> report(onEvent, session, control.start(component))
            "stop" -> report(onEvent, session, control.stop(component))
            "restart" -> report(onEvent, session, control.restart(component))
            "status" -> {
                val status = control.status(component)
                report(
                    onEvent,
                    session,
                    listOf(
                        "${component.label}: ${status.state.name.lowercase()}",
                        "  ${status.message}",
                        "  endpoint : ${status.endpoint ?: "-"}",
                        "  pid      : ${status.pid ?: "-"}",
                        "  version  : ${status.version ?: "-"}",
                    ).joinToString("\n"),
                )
            }
            "logs" -> {
                val count = command.operands.getOrNull(1)?.toIntOrNull() ?: DEFAULT_LOG_LINES
                val lines = LogTailer().tail(paths.logFile(component), count)
                if (lines.isEmpty()) {
                    report(onEvent, session, "${component.label}: log is empty (${paths.logFile(component).name})")
                } else {
                    lines.forEach { emit(session, onEvent, it, TerminalOutput.Channel.STDOUT) }
                    TerminalResult(0, lines)
                }
            }
            "files" -> report(onEvent, session, "${component.label} data: ${paths.componentData(component)}")
            else -> fail(session, onEvent, "${component.id}: unknown action '$action' (start|stop|restart|status|logs|files)")
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun report(
        onEvent: (TerminalOutput) -> Unit,
        session: TerminalSession,
        message: String,
    ): TerminalResult {
        message.lines().forEach { emit(session, onEvent, it, TerminalOutput.Channel.SYSTEM) }
        return TerminalResult(0, message.lines())
    }

    private fun fail(
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
        message: String,
    ): TerminalResult {
        emit(session, onEvent, message, TerminalOutput.Channel.STDERR)
        return TerminalResult(EXIT_FAILURE, listOf(message))
    }

    private fun print(
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
        text: String,
    ): TerminalResult {
        emit(session, onEvent, text, TerminalOutput.Channel.STDOUT)
        return TerminalResult(0, listOf(text))
    }

    /**
     * Resolve a user-supplied path, refusing anything outside the app-owned
     * runtime root. The terminal cannot read the rest of the device.
     */
    internal fun resolve(session: TerminalSession, argument: String): File {
        val candidate = if (argument.startsWith("/")) {
            File(argument)
        } else {
            File(session.state.cwd, argument)
        }
        val root = paths.root.canonicalFile
        val target = candidate.canonicalFile
        if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) {
            throw IllegalArgumentException("path escapes the runtime sandbox: $argument")
        }
        return target
    }

    private fun formatEntry(file: File, long: Boolean): String = if (long) {
        val type = if (file.isDirectory) 'd' else '-'
        val size = humanBytes(if (file.isDirectory) directorySize(file) else file.length())
        val modified = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))
        "$type ${size.padStart(9)} $modified ${file.name}${if (file.isDirectory) "/" else ""}"
    } else {
        file.name + if (file.isDirectory) "/" else ""
    }

    private fun directorySize(dir: File): Long =
        if (dir.isDirectory) dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else 0L

    private fun humanBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun mask(key: String, value: String): String =
        if (SECRET_HINTS.any { key.contains(it, ignoreCase = true) }) "********" else value

    private fun emit(
        session: TerminalSession,
        onEvent: (TerminalOutput) -> Unit,
        text: String,
        channel: TerminalOutput.Channel,
    ) {
        val line = TerminalOutput.Line(text, channel)
        session.output.add(line)
        onEvent(line)
    }

    private companion object {
        const val EXIT_FAILURE = 1
        const val EXIT_NOT_FOUND = 127
        const val MAX_CAT_BYTES = 256L * 1024
        const val DEFAULT_HEAD_LINES = 40
        const val DEFAULT_LOG_LINES = 40
        val SECRET_HINTS = listOf("KEY", "TOKEN", "SECRET", "PASSWORD")
    }
}
