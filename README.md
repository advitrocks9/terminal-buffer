# Terminal Text Buffer

A terminal text buffer implementation in Kotlin -- the core data structure
that terminal emulators use to store and manipulate displayed text.

Built as part of a JetBrains internship application (Integration of an
external terminal emulator into the IntelliJ Terminal).

## Building and Running

```sh
./gradlew build        # compile + test
./gradlew test         # tests only
./gradlew ktlintCheck  # formatting check
```

Requires JDK 21+.

## Design Overview

The core abstraction is `TerminalBuffer`, which owns a **screen** (a fixed-size
array of `Line` objects) and a **scrollback** (a bounded buffer of lines that
have scrolled off the top). Each `Line` is a dense array of `Cell` objects,
where every cell holds a character and its visual attributes (foreground/background
colour, bold/italic/underline).

`Cell` and `CellAttributes` are immutable data classes, so they can be compared
by value and shared cheaply. The buffer itself is mutable: writing text advances
the cursor, wraps at line boundaries, and scrolls the screen when the cursor
moves past the bottom row. Lines that scroll off the top are defensively copied
into the scrollback buffer to prevent aliasing.

## Key Decisions and Tradeoffs

### Dense vs Sparse Line Storage

Each line is a fixed-width `Array<Cell>`. Terminal lines are bounded width
(typically 80--200 columns), usually mostly populated, and need random access
for cursor-addressed writes. A sparse representation (e.g. storing only
non-empty runs) would save memory for mostly-empty lines but adds complexity
for no real benefit at typical terminal widths.

### Scrollback Storage

The scrollback uses an `ArrayDeque` wrapped in a `ScrollbackBuffer` class.
`ArrayDeque` gives O(1) `addLast` and `removeFirst`, which is what we need
for a bounded FIFO. For very large scrollback histories a ring buffer backed
by a flat array would avoid the overhead of `ArrayDeque`'s internal resizing,
but for typical sizes (1000--10000 lines) this is fine.

### Defensive Copying on Scroll

Lines are copied when moved to scrollback. Without this, the scrollback would
hold a reference to the same `Line` object that the screen then overwrites,
corrupting the history. The alternative is move semantics (null out the screen
slot after moving), but copying is safer and simpler.

### Write vs Insert Semantics

`write()` overwrites cells in-place -- this is what normal terminal output does.
`insert()` shifts existing content to the right before placing each character;
characters pushed past the end of the line are lost rather than cascading to
the next line. Cascading would be more complete but significantly more complex
for a feature that's rarely used in practice.

### Cursor Clamping

All cursor movement commands clamp to screen bounds and never wrap. Character
output (`write`) does autowrap. This matches common terminal behaviour where
cursor movement sequences (CUU, CUD, CUF, CUB) clamp but character output
wraps to the next line.

### Wide Character Support

Wide characters (CJK, Hiragana, Katakana, fullwidth forms) occupy two cells
using a dual-cell approach: the first cell holds the character and the second
is marked as a "wide extension". When either half is overwritten, the other
half is cleared to prevent rendering artifacts.

Width detection is based on Unicode block ranges and doesn't handle full
Unicode (surrogate pairs, combining marks, emoji ZWJ sequences). A production
implementation would use a proper Unicode width table like `wcwidth`.

### Resize Strategy

Height changes move lines to/from scrollback. Width changes truncate (narrower)
or pad with empty cells (wider). Scrollback lines keep their original width
for simplicity. A production terminal would reflow wrapped lines when the
width changes, re-wrapping long lines across the new column count.

## What I'd Improve With More Time

- Line reflow on resize (re-wrap long lines to new width)
- Full Unicode width handling (wcwidth equivalent, surrogate pairs, ZWJ emoji)
- Alternate screen buffer (used by vim, less, etc.)
- Tab stop handling
- VT100/xterm escape sequence parsing layer on top of the buffer
- Memory-mapped scrollback for very large histories
- Benchmark suite for large-buffer operations
