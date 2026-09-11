# Terminal

## Overview

The terminal subsystem provides command execution and output display within the app, bridging to runtime processes.

## Data models

- **TerminalCommand** — Represents a command to execute (command string, arguments, working directory).
- **TerminalOutput** — A chunk of stdout/stderr output from a command execution (text, stream type, timestamp).
- **TerminalEnvironment** — Environment variables set for a command session (key-value pairs).
- **TerminalSession** — A bounded session containing a sequence of commands and their outputs.
- **TerminalEngine** — The backend engine that actually executes commands (e.g., the local process runtime or OpenCode terminal bridge).

## Channel colors

Each output stream has a distinct color for visual separation:

- **stdout** — White (`#FFFFFF`)
- **stderr** — Accent (`#FFF5A8`)
- **system** — Muted (`#737373`)

## Output capture

Terminal output is captured using **polling-based** read calls rather than event-driven streaming. The app reads from the process's stdout/stderr file descriptors at a regular interval and appends new content to the session output buffer.
