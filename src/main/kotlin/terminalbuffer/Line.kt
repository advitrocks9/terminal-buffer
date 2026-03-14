package terminalbuffer

/**
 * A single row in the terminal grid: a fixed-width array of [Cell]s.
 */
internal class Line(val width: Int) {
    private val cells = Array(width) { Cell.EMPTY }

    operator fun get(col: Int): Cell {
        require(col in 0..<width) { "Column $col out of bounds (width=$width)" }
        return cells[col]
    }

    operator fun set(
        col: Int,
        cell: Cell,
    ) {
        require(col in 0..<width) { "Column $col out of bounds (width=$width)" }
        cells[col] = cell
    }

    fun clear(attributes: CellAttributes = CellAttributes.DEFAULT) {
        for (i in cells.indices) {
            cells[i] = Cell(attributes = attributes)
        }
    }

    fun fill(
        char: Char,
        attributes: CellAttributes,
    ) {
        for (i in cells.indices) {
            cells[i] = Cell(char, attributes)
        }
    }

    /** Returns line text with trailing spaces trimmed, skipping wide extension cells. */
    fun getText(): String =
        buildString {
            for (cell in cells) {
                if (!cell.isWideExtension) append(cell.char)
            }
        }.trimEnd()

    /** Returns full line text without trimming (useful for testing). */
    fun getTextRaw(): String =
        buildString {
            for (cell in cells) {
                if (!cell.isWideExtension) append(cell.char)
            }
        }

    fun copyOf(): Line {
        val copy = Line(width)
        for (i in cells.indices) {
            copy.cells[i] = cells[i]
        }
        return copy
    }
}
