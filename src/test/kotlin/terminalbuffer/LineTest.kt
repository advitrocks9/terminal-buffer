package terminalbuffer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LineTest {
    @Test
    fun `new line has all empty cells`() {
        val line = Line(5)
        for (col in 0..<5) {
            assertEquals(Cell.EMPTY, line[col])
        }
    }

    @Test
    fun `set and get cell at valid index`() {
        val line = Line(10)
        val cell = Cell('A', CellAttributes(foreground = Color.RED))
        line[3] = cell
        assertEquals(cell, line[3])
    }

    @Test
    fun `get cell at invalid index throws`() {
        val line = Line(5)
        assertFailsWith<IllegalArgumentException> { line[-1] }
        assertFailsWith<IllegalArgumentException> { line[5] }
    }

    @Test
    fun `clear resets all cells`() {
        val line = Line(3)
        line[0] = Cell('A')
        line[1] = Cell('B')
        line.clear()
        for (col in 0..<3) {
            assertEquals(Cell.EMPTY, line[col])
        }
    }

    @Test
    fun `clear with attributes uses those attributes`() {
        val attrs = CellAttributes(foreground = Color.GREEN)
        val line = Line(3)
        line.clear(attrs)
        for (col in 0..<3) {
            assertEquals(attrs, line[col].attributes)
            assertEquals(Cell.EMPTY_CHAR, line[col].char)
        }
    }

    @Test
    fun `fill sets all cells to given char and attrs`() {
        val attrs = CellAttributes(foreground = Color.BLUE)
        val line = Line(4)
        line.fill('#', attrs)
        for (col in 0..<4) {
            assertEquals('#', line[col].char)
            assertEquals(attrs, line[col].attributes)
        }
    }

    @Test
    fun `getText trims trailing spaces`() {
        val line = Line(10)
        line[0] = Cell('H')
        line[1] = Cell('i')
        assertEquals("Hi", line.getText())
    }

    @Test
    fun `getText preserves internal spaces`() {
        val line = Line(12)
        "hello world".forEachIndexed { i, c -> line[i] = Cell(c) }
        assertEquals("hello world", line.getText())
    }

    @Test
    fun `copyOf creates independent copy`() {
        val line = Line(5)
        line[0] = Cell('X')
        val copy = line.copyOf()
        assertEquals('X', copy[0].char)
        line[0] = Cell('Y')
        assertEquals('X', copy[0].char)
    }
}
