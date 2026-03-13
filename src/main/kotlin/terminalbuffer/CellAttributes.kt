package terminalbuffer

/**
 * Immutable set of visual attributes for a terminal cell.
 * Used both as "current attributes" on the buffer and stored per-cell.
 */
data class CellAttributes(
    val foreground: Color = Color.DEFAULT,
    val background: Color = Color.DEFAULT,
    val styles: Set<Style> = emptySet(),
) {
    companion object {
        val DEFAULT = CellAttributes()
    }
}
