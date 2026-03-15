# Terminal Text Buffer

A terminal text buffer in Kotlin — the data structure at the heart of any terminal emulator. It manages the grid of character cells that the shell writes to and the UI renders from.

## Building and Running

```sh
./gradlew build        # compile + test
./gradlew test         # tests only
./gradlew ktlintCheck  # lint
```

Needs JDK 21+.

## How It Works

The buffer is split into two parts: a **screen** (the visible grid, e.g. 80x24) and a **scrollback** (lines that scrolled off the top, kept around for history). Each row is a `Line` — a fixed-width array of `Cell` objects. Each cell stores a character plus its attributes (foreground color, background color, bold/italic/underline).

`Cell` and `CellAttributes` are immutable data classes. The buffer itself is mutable — writing text advances the cursor, wraps at line edges, and scrolls when you hit the bottom. Lines scrolled off the top get copied into the scrollback.

The main operations:

- `write(text)` — overwrites cells at the cursor position and advances the cursor. This is what normal terminal output does.
- `insert(text)` — inserts at the cursor, shifting existing content right. Overflow cascades to the next line (and scrolls if it reaches the bottom).
- `fillLine(char)` — fills the entire current row with a character.
- `setCursorPosition`, `moveCursorUp/Down/Left/Right` — cursor manipulation, clamped to screen bounds.
- `clearScreen()`, `clearAll()`, `insertLine()` — screen management.
- `resize(w, h)` — changes screen dimensions, moving lines to/from scrollback as needed.

Wide characters (CJK, fullwidth forms, etc.) are supported using a dual-cell approach where the first cell holds the character and the second is marked as a placeholder.

## Design Decisions

**Dense arrays for lines.** Terminal lines are short (80-200 cols) and mostly filled, so a flat array per line is simpler and faster than a sparse structure. Random access for cursor-addressed writes is O(1).

**ArrayDeque for scrollback.** It gives O(1) add/remove at both ends, which is what a bounded FIFO needs. A ring buffer over a flat array would be better for huge scrollback sizes, but ArrayDeque is fine for the typical 1000-10000 line range.

**Defensive copy on scroll.** When a line moves from screen to scrollback, it gets copied. Otherwise the scrollback would hold a reference to a `Line` that the screen then mutates. I considered move semantics (nulling out the screen slot) but copying is simpler and avoids a class of bugs.

**Cursor clamping vs wrapping.** Cursor movement commands (up/down/left/right) clamp to screen bounds. Character output autowraps. This matches how real terminals handle cursor-movement escape sequences vs normal character output.

**Insert cascading.** When `insert()` pushes content past the end of a line, the overflow gets cascaded to the next line rather than being dropped. If the overflow reaches the bottom of the screen, the top line scrolls into scrollback. This felt like the right behavior even though it adds complexity — dropping characters silently seemed worse.

**Wide character handling.** A wide character takes two cells: the real character in the first cell and a `isWideExtension = true` marker in the second. When either half gets overwritten, the other half is cleared to avoid rendering artifacts. Width detection covers CJK unified ideographs, hiragana, katakana, hangul, fullwidth forms, and a few other Unicode blocks. It doesn't handle surrogate pairs or emoji ZWJ sequences — that would need something like a proper `wcwidth` table.

**Resize strategy.** Shrinking the height pushes top lines to scrollback. Growing pulls them back. Width changes truncate or pad — no line reflow. Scrollback lines keep their original width. This is the simple approach; a production terminal would re-wrap lines when the width changes.

## What I'd Do Next

- **Line reflow on resize** — re-wrap long lines to the new width instead of just truncating. This is the biggest missing piece for a real terminal.
- **Full Unicode width handling** — use a proper width table instead of hardcoded ranges. Handle surrogate pairs and combining characters.
- **Alternate screen buffer** — the secondary buffer that programs like vim and less switch to, so they don't pollute scrollback.
- **Tab stops** — right now tabs aren't handled at all.
- **Escape sequence parsing** — a layer on top of the buffer that interprets VT100/xterm control sequences and translates them into buffer operations.
- **Memory-mapped scrollback** — for very large histories, swap old lines to disk instead of keeping everything in memory.
