package com.n8n.mobile.studio.terminal

/**
 * Tokenises a terminal line the way a POSIX shell would for the subset the
 * studio supports: whitespace separation, single quotes (literal), double quotes
 * (literal except `\"`), and backslash escapes outside quotes.
 *
 * Deliberately *no* operators: no `|`, `&&`, `;`, globbing, `$VAR`, or command
 * substitution — this terminal runs a fixed command set, not an interpreter, and
 * pretending otherwise would be the kind of fake runtime the project forbids.
 */
object TerminalParser {

    fun parse(line: String): ParsedCommand? {
        val tokens = tokenize(line)
        val name = tokens.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return ParsedCommand(name = name, args = tokens.drop(1), raw = line)
    }

    fun tokenize(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escaping = false
        var hasToken = false

        line.forEach { char ->
            when {
                escaping -> {
                    current.append(char)
                    hasToken = true
                    escaping = false
                }
                char == '\\' && !inSingle -> escaping = true
                char == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    hasToken = true
                }
                char == '"' && !inSingle -> {
                    inDouble = !inDouble
                    hasToken = true
                }
                char.isWhitespace() && !inSingle && !inDouble -> {
                    if (hasToken) {
                        tokens += current.toString()
                        current.setLength(0)
                        hasToken = false
                    }
                }
                else -> {
                    current.append(char)
                    hasToken = true
                }
            }
        }
        if (hasToken) tokens += current.toString()
        return tokens
    }
}
