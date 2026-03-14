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
    val width: Int,
    val height: Int,
    val maxScrollbackSize: Int = 1000,
) {
    init {
        require(width > 0) { "Width must be positive, got $width" }
        require(height > 0) { "Height must be positive, got $height" }
        require(maxScrollbackSize >= 0) { "Max scrollback size must be non-negative" }
    }

    internal val screen = Array(height) { Line(width) }
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
            screen[cursorRow][cursorCol] = Cell(c, currentAttributes)
            cursorCol++
            if (cursorCol >= width) {
                advanceCursorToNextLine()
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
            val line = screen[cursorRow]
            for (i in (width - 1) downTo (cursorCol + 1)) {
                line[i] = line[i - 1]
            }
            line[cursorCol] = Cell(c, currentAttributes)
            cursorCol++
            if (cursorCol >= width) {
                advanceCursorToNextLine()
            }
        }
    }

    /**
     * Fills the current cursor's row with [char] using [currentAttributes].
     * Cursor position does not change.
     */
    fun fillLine(char: Char = Cell.EMPTY_CHAR) {
        screen[cursorRow].fill(char, currentAttributes)
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

    fun getCell(
        col: Int,
        row: Int,
    ): Cell = screen[row][col]

    fun getScreenLine(row: Int): String = screen[row].getText()

    fun getScreenContent(): String =
        buildString {
            for (row in 0..<height) {
                if (row > 0) append('\n')
                append(screen[row].getText())
            }
        }

    fun getScrollbackLine(row: Int): String = scrollback[row].getText()

    fun getScrollbackCell(
        col: Int,
        row: Int,
    ): Cell = scrollback[row][col]

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
