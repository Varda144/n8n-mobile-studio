# GUI Architecture

## Package layout

| Package                          | Purpose                                          |
|----------------------------------|--------------------------------------------------|
| `ui/theme`                       | Visual system — colors, typography, shapes        |
| `ui/components`                  | Reusable Compose components (buttons, cards, etc.) |
| `ui/navigation`                  | NavHost graph, route definitions, transitions      |
| `ui/screens`                     | Top-level screen composables (one per route)       |

## Visual system: Neo-Brutalist

The app uses a Neo-Brutalist design language defined in `StudioPalette`:

| Token    | Hex        | Usage                           |
|----------|------------|----------------------------------|
| Background | `#F8F8F3` | Screen / card backgrounds       |
| Primary  | `#000000`   | Text, borders, icons            |
| Muted    | `#737373`   | Secondary text, hints           |
| Grid     | `#D7D7D2`   | Dividers, grid lines            |
| Accent   | `#FFF5A8`   | Highlights, active states       |
| White    | `#FFFFFF`   | Inverted surfaces, text on dark |

Typography is sans-serif. Borders are hard-edged, no rounded corners. Shadows are offset or absent. The look is intentionally raw and high-contrast.

## Key components

- **RuntimeStatus** — Displays the current state of a runtime (stopped, starting, running, error) with a colored indicator dot and label.
- **TerminalView** — Renders terminal session output with stream-colored text and a scrollable viewport. Accepts a `TerminalSession` and polls for new output.
