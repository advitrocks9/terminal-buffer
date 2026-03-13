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

    override fun toString(): String = getScreenContent()
}
