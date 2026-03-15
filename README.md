# Terminal Text Buffer

A terminal text buffer implementation in Kotlin: the core data structure behind terminal emulators, responsible for storing, editing, and scrolling displayed text.

## Building & Testing

```bash
./gradlew build
./gradlew test
```

## Overview

The buffer models a terminal as a fixed-size grid of character cells, each holding a character, foreground/background color, and style flags. Text is written at a movable cursor position. When content scrolls off the top of the screen, it's preserved in a bounded scrollback history.

The main entry point is `TerminalBuffer(width, height, maxScrollbackSize)`.

### Supported Operations

- **Cursor movement**: absolute positioning, relative movement (up/down/left/right by N), clamped to screen bounds
- **Write**: overwrites content at cursor, advancing and wrapping at line end
- **Insert**: shifts existing content right, with overflow cascading to subsequent lines and triggering scroll if needed
- **Delete**: `deleteChar(n)` shifts content left from cursor, `eraseToEndOfLine()` blanks cursor to end, `deleteLine()` removes the row and shifts below up
- **Control characters**: `\r` (carriage return to col 0), `\b` (cursor left 1), `\t` (advance to next tab stop at multiple of 8), `\n` (next line)
- **Fill line**: fills the cursor's row with a character
- **Insert line**: pushes a blank line at the bottom, scrolling the top line into scrollback
- **Clear**: clear screen only, or clear screen + scrollback
- **Resize**: change screen dimensions, moving content to/from scrollback as needed
- **Content access**: individual cells (char + attributes), lines as strings, full screen/scrollback content

### Bonus Features

- **Wide character support**: CJK characters (and fullwidth forms) occupy two cells. The buffer tracks primary/extension cell pairs and handles orphan cleanup when wide characters are partially overwritten, split by inserts, or truncated by resize.
- **Resize**: shrinking height pushes top lines to scrollback; growing height pulls them back. Width changes truncate or pad content, with orphaned wide char pairs cleaned up at the boundary.

## Design Decisions

**Cell as a data class.** Each cell is an immutable value holding its character, attributes, and a flag for wide character extensions. Immutability keeps reasoning simple since cells are replaced rather than mutated.

**Line as a fixed-width array.** Each line is backed by a plain `Array<Cell>`. This gives O(1) random access for reads and writes, which matters because terminal operations are fundamentally cell-indexed. The tradeoff is that lines can't grow or shrink without copying, but terminal lines are fixed-width by definition so this fits naturally.

**ScrollbackBuffer with ArrayDeque.** Scrollback is a bounded FIFO. `ArrayDeque` gives O(1) append and O(1) eviction from the front, which is good enough for the expected scrollback sizes (hundreds to low thousands of lines). For very large scrollback (100k+ lines), a ring buffer backed by a flat array would avoid the overhead of deque node allocation, but that felt like premature optimisation here.

**Insert cascade.** Inserting text shifts content right, and any overflow at the end of the line cascades down to the next line. If the overflow reaches the bottom of the screen, it triggers a scroll. This mirrors how real terminal insert-character operations work. The cascade tracks wide character boundary splits to avoid leaving orphaned extension cells.

**Wide character handling.** Wide chars are stored as a primary cell followed by an extension cell. Any operation that would overwrite only half of a wide pair cleans up the other half. This includes: writing over the primary or extension cell, inserting at an extension cell, shifting content that splits a pair at the line boundary, and resizing width to truncate a pair. The approach is straightforward but adds checks in several code paths.

**Resize strategy.** On height shrink, the top rows go to scrollback (preserving the bottom of the screen, which is where the most recent output lives). On height grow, lines are pulled back from scrollback. Width changes copy as much content as fits. The cursor is clamped to the new bounds. This is a simplification compared to what full terminal emulators do (some try to rewrap content), but it handles the common cases and keeps the implementation tractable.

## Known Limitations / Possible Improvements

- **No rewrap on resize.** Changing width truncates or pads lines rather than reflowing text. Real terminals like xterm optionally rewrap, but this significantly complicates the resize logic and content tracking.
- **Limited control characters.** `\n`, `\r`, `\b`, and `\t` are handled. Escape sequences (ANSI CSI, OSC, etc.) are not parsed — a real terminal would need an ANSI parser sitting in front of the buffer.
- **BMP-only wide chars.** Kotlin's `Char` is 16-bit, so characters outside the Basic Multilingual Plane (emoji, supplementary CJK) would need surrogate pair handling. The current `CharWidths` only covers BMP ranges.
- **Overflow cascade growth.** When an insert cascade splits a wide character pair at the end of a line, the overflow grows by one cell. In pathological cases (every line ending with a wide char), this could compound across many lines. In practice this is unlikely to be an issue, but a production implementation would want to handle it more carefully.
- **Cursor position on height shrink.** When the screen shrinks and the cursor's row gets pushed to scrollback, the cursor is clamped to the new bounds rather than tracking which content it was on. Different terminals handle this differently; there isn't one right answer.
- **Scrollback ring buffer.** The TODO in `ScrollbackBuffer` notes that a ring buffer would give O(1) eviction without deque overhead. Worth doing for large scrollback limits.

## Project Structure

```
src/
  main/kotlin/terminalbuffer/
    Cell.kt               — single grid cell (char + attributes + wide flag)
    CellAttributes.kt     — foreground, background, styles
    CharWidths.kt         — CJK/wide character width lookup
    Color.kt              — 16 ANSI colors + default
    Line.kt               — fixed-width row of cells
    ScrollbackBuffer.kt   — bounded FIFO for scrollback history
    Style.kt              — bold, italic, underline
    TerminalBuffer.kt     — main buffer: screen + scrollback + cursor + editing
  test/kotlin/terminalbuffer/
    CellAttributesTest.kt
    LineTest.kt
    ScrollbackBufferTest.kt
    TerminalBufferTest.kt
```
