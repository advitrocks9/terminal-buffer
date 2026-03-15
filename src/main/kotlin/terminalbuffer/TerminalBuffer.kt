package terminalbuffer

/**
 * A terminal text buffer with a [width] x [height] screen and scrollback history.
 * Text is written at the cursor and wraps at line end.
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

    /** Writes text at cursor, overwriting existing content. Wraps at line end. */
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
     * Overflow cascades to the next line; scrolls up if it reaches the bottom.
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
        val row = cursorRow
        val line = screen[row]
        clearOrphanedWidePair(line, cursorCol)
        val overflow = shiftLineRight(line, cursorCol, 1)
        line[cursorCol] = Cell(c, currentAttributes)
        cursorCol++
        if (cursorCol >= width) {
            advanceCursorToNextLine()
        }
        cascadeOverflow(row + 1, overflow)
    }

    private fun insertWideChar(c: Char) {
        if (width < 2) {
            insertNarrowChar(c)
            return
        }
        if (cursorCol >= width - 1) {
            advanceCursorToNextLine()
        }
        val row = cursorRow
        val line = screen[row]
        clearOrphanedWidePair(line, cursorCol)
        val overflow = shiftLineRight(line, cursorCol, 2)
        line[cursorCol] = Cell(c, currentAttributes)
        line[cursorCol + 1] = Cell(isWideExtension = true, attributes = currentAttributes)
        cursorCol += 2
        if (cursorCol >= width) {
            advanceCursorToNextLine()
        }
        cascadeOverflow(row + 1, overflow)
    }

    private fun shiftLineRight(
        line: Line,
        fromCol: Int,
        shiftBy: Int,
    ): List<Cell> {
        if (shiftBy <= 0) return emptyList()

        val overflowStart = width - shiftBy
        if (overflowStart <= fromCol) {
            return (fromCol..<width).map { line[it] }
        }

        var actualOverflowStart = overflowStart
        if (line[overflowStart].isWideExtension && !line[overflowStart - 1].isWideExtension) {
            actualOverflowStart = overflowStart - 1
        }

        val overflow = (actualOverflowStart..<width).map { line[it] }

        for (i in (width - 1) downTo (fromCol + shiftBy)) {
            line[i] = line[i - shiftBy]
        }

        val extra = overflow.size - shiftBy
        for (i in (width - extra)..<width) {
            line[i] = Cell(attributes = line[i].attributes)
        }

        return overflow
    }

    private fun cascadeOverflow(
        targetRow: Int,
        overflow: List<Cell>,
    ) {
        var currentOverflow = overflow
        var currentTarget = targetRow

        while (currentOverflow.isNotEmpty()) {
            if (currentOverflow.all { it.char == Cell.EMPTY_CHAR && !it.isWideExtension }) return

            val row =
                if (currentTarget >= height) {
                    scrollUp()
                    height - 1
                } else {
                    currentTarget
                }

            val line = screen[row]
            val newOverflow = shiftLineRight(line, 0, currentOverflow.size)

            for (i in currentOverflow.indices) {
                if (i < width) line[i] = currentOverflow[i]
            }

            currentOverflow = newOverflow
            currentTarget = row + 1
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
        if (width < 2) {
            clearWideCharIfOverwriting()
            screen[cursorRow][cursorCol] = Cell(c, currentAttributes)
            cursorCol++
            if (cursorCol >= width) {
                advanceCursorToNextLine()
            }
            return
        }
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

    private fun clearOrphanedWidePair(
        line: Line,
        col: Int,
    ) {
        if (line[col].isWideExtension && col > 0) {
            line[col - 1] = Cell(attributes = line[col - 1].attributes)
            line[col] = Cell(attributes = line[col].attributes)
        }
    }

    private fun clearWideCharIfOverwriting(col: Int = cursorCol) {
        if (col >= width) return
        val line = screen[cursorRow]
        val cell = line[col]
        if (cell.isWideExtension && col > 0) {
            line[col - 1] = Cell(attributes = line[col - 1].attributes)
        } else if (!cell.isWideExtension && col + 1 < width && line[col + 1].isWideExtension) {
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

    /** Resizes the screen to [newWidth] x [newHeight]. */
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
            val pulled = mutableListOf<Line>()
            repeat(extraRows) {
                val line = scrollback.removeLast() ?: return@repeat
                pulled.add(line)
            }
            pulled.reverse()
            for (i in pulled.indices) {
                copyLineContent(pulled[i], newScreen[i], newWidth)
            }
            for (row in 0..<height) {
                copyLineContent(screen[row], newScreen[extraRows + row], newWidth)
            }
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
        if (copyWidth in 1..<source.width && source[copyWidth].isWideExtension) {
            dest[copyWidth - 1] = Cell(attributes = dest[copyWidth - 1].attributes)
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
