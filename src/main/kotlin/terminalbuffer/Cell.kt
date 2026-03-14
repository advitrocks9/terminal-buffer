package terminalbuffer

/** A single cell in the terminal grid. */
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
