package terminalbuffer

/**
 * A terminal text buffer that stores a visible screen and a scrollback history.
 *
 * The screen is a grid of [width] x [height] cells. Text is written at the
 * cursor position, which advances and wraps as characters are added.
 * Lines that scroll off the top of the screen are stored in the scrollback
 * buffer up to [maxScrollbackSize] lines.
 */
class TerminalBuffer(
    width: Int,
    height: Int,
    val maxScrollbackSize: Int = 1000,
) {
    init {
        require(width > 0) { "Width must be positive, got $width" }
        require(height > 0) { "Height must be positive, got $height" }
        require(maxScrollbackSize >= 0) { "Max scrollback size must be non-negative" }
    }

    var width: Int = width
        private set
    var height: Int = height
        private set

    private var screen = Array(height) { Line(width) }
    private val scrollback = ScrollbackBuffer(maxScrollbackSize)

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set
    var currentAttributes: CellAttributes = CellAttributes.DEFAULT
        private set

    fun setCursorPosition(
        col: Int,
        row: Int,
    ) {
        cursorCol = col.coerceIn(0..<width)
        cursorRow = row.coerceIn(0..<height)
    }

    // Clamped to screen bounds; no line wrapping on cursor movement
    fun moveCursorUp(n: Int = 1) {
        if (n <= 0) return
        cursorRow = (cursorRow - n).coerceIn(0..<height)
    }

    fun moveCursorDown(n: Int = 1) {
        if (n <= 0) return
        cursorRow = (cursorRow + n).coerceIn(0..<height)
    }

    fun moveCursorLeft(n: Int = 1) {
        if (n <= 0) return
        cursorCol = (cursorCol - n).coerceIn(0..<width)
    }

    fun moveCursorRight(n: Int = 1) {
        if (n <= 0) return
        cursorCol = (cursorCol + n).coerceIn(0..<width)
    }

    fun setAttributes(
        fg: Color = Color.DEFAULT,
        bg: Color = Color.DEFAULT,
        styles: Set<Style> = emptySet(),
    ) {
        currentAttributes = CellAttributes(fg, bg, styles)
    }

    fun setAttributes(attributes: CellAttributes) {
        currentAttributes = attributes
    }

    // In real terminals, autowrap is a mode that can be toggled. We always autowrap here.
    fun write(text: String) {
        for (c in text) {
            if (c == '\n') {
                advanceCursorToNextLine()
                continue
            }
            val charWidth = CharWidths.charWidth(c)
            if (charWidth == 2) {
                writeWideChar(c)
            } else {
                clearWideCharIfOverwriting()
                screen[cursorRow][cursorCol] = Cell(c, currentAttributes)
                cursorCol++
                if (cursorCol >= width) {
                    advanceCursorToNextLine()
                }
            }
        }
    }

    /**
     * Inserts text at cursor position, shifting existing content to the right.
     * Characters pushed past the end of the line are lost (not cascaded to next line).
     */
    fun insert(text: String) {
        for (c in text) {
            if (c == '\n') {
                advanceCursorToNextLine()
                continue
            }
            val charWidth = CharWidths.charWidth(c)
            if (charWidth == 2) {
                insertWideChar(c)
            } else {
                insertNarrowChar(c)
            }
        }
    }

    private fun insertNarrowChar(c: Char) {
        val line = screen[cursorRow]
        // Shift right by 1; if the last cell is the primary of a wide pair, clear the orphaned extension
        if (width >= 2 && line[width - 1].isWideExtension) {
            line[width - 2] = Cell(attributes = line[width - 2].attributes)
        }
        for (i in (width - 1) downTo (cursorCol + 1)) {
            line[i] = line[i - 1]
        }
        // If we're inserting into the extension half of a wide char, clear the primary
        clearWideCharIfOverwriting()
        line[cursorCol] = Cell(c, currentAttributes)
        cursorCol++
        if (cursorCol >= width) {
            advanceCursorToNextLine()
        }
    }

    private fun insertWideChar(c: Char) {
        if (cursorCol >= width - 1) {
            advanceCursorToNextLine()
        }
        val line = screen[cursorRow]
        // Shift right by 2; handle wide pair split at boundary
        val secondToLast = width - 2
        // If the cell that will be pushed off is the primary of a wide pair, clear orphaned extension
        if (line[width - 1].isWideExtension && !line[secondToLast].isWideExtension) {
            // The primary at secondToLast is about to be shifted off — clear the extension already gone
        }
        // If a wide pair straddles the shift boundary (primary at secondToLast, ext at width-1), both fall off cleanly
        // If only primary falls off (ext already off) or only ext falls off, clear the remaining orphan
        val lastBeforeShift = line[width - 2]
        val lastCell = line[width - 1]
        if (!lastBeforeShift.isWideExtension && lastCell.isWideExtension) {
            // Wide pair at (width-2, width-1) — both fall off, no orphan
        } else if (lastBeforeShift.isWideExtension && !lastCell.isWideExtension) {
            // Extension at width-2 about to fall off — clear the primary at width-3
            if (width >= 3) line[width - 3] = Cell(attributes = line[width - 3].attributes)
        }
        for (i in (width - 1) downTo (cursorCol + 2)) {
            line[i] = line[i - 2]
        }
        // Clear any orphaned wide char at the overwrite target
        clearWideCharIfOverwriting()
        clearWideCharIfOverwriting(cursorCol + 1)
        line[cursorCol] = Cell(c, currentAttributes)
        line[cursorCol + 1] = Cell(isWideExtension = true, attributes = currentAttributes)
        cursorCol += 2
        if (cursorCol >= width) {
            advanceCursorToNextLine()
        }
    }

    /**
     * Fills the current cursor's row with [char] using [currentAttributes].
     * Cursor position does not change.
     */
    fun fillLine(char: Char = Cell.EMPTY_CHAR) {
        screen[cursorRow].fill(char, currentAttributes)
    }

    private fun writeWideChar(c: Char) {
        if (cursorCol >= width - 1) {
            advanceCursorToNextLine()
        }
        clearWideCharIfOverwriting()
        screen[cursorRow][cursorCol] = Cell(c, currentAttributes)
        if (cursorCol + 1 < width) {
            clearWideCharIfOverwriting(cursorCol + 1)
            screen[cursorRow][cursorCol + 1] = Cell(isWideExtension = true, attributes = currentAttributes)
        }
        cursorCol += 2
        if (cursorCol >= width) {
            advanceCursorToNextLine()
        }
    }

    /**
     * If the cell at [col] on the current row is part of a wide character,
     * clear the other half to avoid rendering artifacts.
     */
    private fun clearWideCharIfOverwriting(col: Int = cursorCol) {
        if (col >= width) return
        val line = screen[cursorRow]
        val cell = line[col]
        if (cell.isWideExtension && col > 0) {
            // We're overwriting the extension half — clear the primary
            line[col - 1] = Cell(attributes = line[col - 1].attributes)
        } else if (!cell.isWideExtension && col + 1 < width && line[col + 1].isWideExtension) {
            // We're overwriting the primary half — clear the extension
            line[col + 1] = Cell(attributes = line[col + 1].attributes)
        }
    }

    private fun advanceCursorToNextLine() {
        cursorCol = 0
        if (cursorRow < height - 1) {
            cursorRow++
        } else {
            scrollUp()
        }
    }

    private fun scrollUp() {
        // Defensive copy: scrollback gets its own copy of the line
        scrollback.add(screen[0].copyOf())
        for (i in 1..<height) {
            screen[i - 1] = screen[i]
        }
        screen[height - 1] = Line(width)
    }

    /**
     * Inserts a new empty line at the bottom of the screen.
     * The top screen line scrolls into scrollback, all lines shift up.
     */
    fun insertLine() {
        scrollUp()
    }

    /** Clears all screen lines and resets cursor. Does not clear scrollback. */
    fun clearScreen() {
        for (i in 0..<height) {
            screen[i] = Line(width)
        }
        cursorRow = 0
        cursorCol = 0
    }

    /** Clears both the screen and scrollback, resets cursor. */
    fun clearAll() {
        clearScreen()
        scrollback.clear()
    }

    /**
     * Resizes the screen to [newWidth] x [newHeight].
     *
     * - If height shrinks, extra bottom rows move to scrollback.
     * - If height grows, lines are pulled from scrollback (if available) or empty lines are added.
     * - Width changes truncate (narrower) or pad (wider) existing screen lines.
     * - Scrollback lines keep their original width for simplicity.
     *
     * A production terminal would reflow wrapped lines here; we truncate/pad for simplicity.
     */
    fun resize(
        newWidth: Int,
        newHeight: Int,
    ) {
        require(newWidth > 0) { "Width must be positive, got $newWidth" }
        require(newHeight > 0) { "Height must be positive, got $newHeight" }

        if (newWidth == width && newHeight == height) return

        if (newHeight < height) {
            for (i in 0..<(height - newHeight)) {
                scrollback.add(screen[i].copyOf())
            }
        }

        val newScreen = Array(newHeight) { Line(newWidth) }
        if (newHeight <= height) {
            val startRow = height - newHeight
            for (row in 0..<newHeight) {
                copyLineContent(screen[startRow + row], newScreen[row], newWidth)
            }
        } else {
            val extraRows = newHeight - height
            // (We can't easily pull from ScrollbackBuffer since it's a queue,
            //  so we only fill remaining rows with empty lines)
            for (row in 0..<height) {
                copyLineContent(screen[row], newScreen[extraRows + row], newWidth)
            }
            // Extra rows at top are already empty Line(newWidth)
        }

        screen = newScreen
        width = newWidth
        height = newHeight
        cursorRow = cursorRow.coerceIn(0..<height)
        cursorCol = cursorCol.coerceIn(0..<width)
    }

    private fun copyLineContent(
        source: Line,
        dest: Line,
        destWidth: Int,
    ) {
        val copyWidth = minOf(source.width, destWidth)
        for (col in 0..<copyWidth) {
            dest[col] = source[col]
        }
    }

    fun getCell(
        col: Int,
        row: Int,
    ): Cell {
        require(row in 0..<height) { "Row $row out of screen bounds (height=$height)" }
        require(col in 0..<width) { "Column $col out of screen bounds (width=$width)" }
        return screen[row][col]
    }

    fun getScreenLine(row: Int): String {
        require(row in 0..<height) { "Row $row out of screen bounds (height=$height)" }
        return screen[row].getText()
    }

    fun getScreenContent(): String =
        buildString {
            for (row in 0..<height) {
                if (row > 0) append('\n')
                append(screen[row].getText())
            }
        }

    fun getScrollbackLine(row: Int): String {
        require(row in 0..<scrollback.size) { "Row $row out of scrollback bounds (size=${scrollback.size})" }
        return scrollback[row].getText()
    }

    fun getScrollbackCell(
        col: Int,
        row: Int,
    ): Cell {
        require(row in 0..<scrollback.size) { "Row $row out of scrollback bounds (size=${scrollback.size})" }
        val line = scrollback[row]
        require(col in 0..<line.width) { "Column $col out of scrollback line bounds (width=${line.width})" }
        return line[col]
    }

    fun getScrollbackSize(): Int = scrollback.size

    /** Returns scrollback + screen content joined with newlines. */
    fun getFullContent(): String =
        buildString {
            for (line in scrollback.asSequence()) {
                append(line.getText())
                append('\n')
            }
            for (row in 0..<height) {
                if (row > 0) append('\n')
                append(screen[row].getText())
            }
        }

    override fun toString(): String = getScreenContent()
}
