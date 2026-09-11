package com.n8n.mobile.studio.terminal

/** Streamed terminal output event. */
sealed interface TerminalOutput {
    enum class Channel { STDOUT, STDERR, SYSTEM }

    data class Line(val text: String, val channel: Channel) : TerminalOutput

    data class Exited(val exitCode: Int) : TerminalOutput
}

/** Bounded in-memory history of [TerminalOutput.Line]s. */
class TerminalOutputBuffer(private val maxLines: Int = 2000) {
    private val _lines = mutableListOf<TerminalOutput.Line>()

    val lines: List<TerminalOutput.Line> get() = _lines.toList()

    fun add(output: TerminalOutput) {
        if (output is TerminalOutput.Line) {
            _lines.add(output)
            if (_lines.size > maxLines + 64) {
                _lines.subList(0, _lines.size - maxLines).clear()
            }
        }
    }

    fun clear() = _lines.clear()
}