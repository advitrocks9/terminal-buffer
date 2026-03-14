package terminalbuffer

/**
 * A single cell in the terminal grid.
 * [char] is the displayed character, or [EMPTY_CHAR] for a blank cell.
 *
 * Wide characters (CJK, etc.) occupy two cells: the primary cell holds the
 * character and the next cell is a "wide extension" marker.
 */
data class Cell(
    val char: Char = EMPTY_CHAR,
    val attributes: CellAttributes = CellAttributes.DEFAULT,
    val isWideExtension: Boolean = false,
) {
    companion object {
        const val EMPTY_CHAR = ' '
        val EMPTY = Cell()
    }
}
