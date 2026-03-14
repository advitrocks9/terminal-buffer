package terminalbuffer

/** Visual attributes for a terminal cell: colors and styles. */
data class CellAttributes(
    val foreground: Color = Color.DEFAULT,
    val background: Color = Color.DEFAULT,
    val styles: Set<Style> = emptySet(),
) {
    companion object {
        val DEFAULT = CellAttributes()
    }
}
