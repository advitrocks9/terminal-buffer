package terminalbuffer

/**
 * A single cell in the terminal grid.
 * [char] is the displayed character, or [EMPTY_CHAR] for a blank cell.
 */
data class Cell(
    val char: Char = EMPTY_CHAR,
    val attributes: CellAttributes = CellAttributes.DEFAULT,
) {
    companion object {
        const val EMPTY_CHAR = ' '
        val EMPTY = Cell()
    }
}
