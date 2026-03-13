package terminalbuffer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerminalBufferTest {
    @Test
    fun `buffer initialises with correct dimensions`() {
        val buf = TerminalBuffer(80, 24)
        assertEquals(80, buf.width)
        assertEquals(24, buf.height)
    }

    @Test
    fun `invalid width throws`() {
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(0, 24) }
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(-1, 24) }
    }

    @Test
    fun `invalid height throws`() {
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(80, 0) }
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(80, -1) }
    }

    @Test
    fun `negative max scrollback throws`() {
        assertFailsWith<IllegalArgumentException> { TerminalBuffer(80, 24, -1) }
    }

    @Test
    fun `set cursor position works`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursorPosition(col = 10, row = 5)
        assertEquals(10, buf.cursorCol)
        assertEquals(5, buf.cursorRow)
    }

    @Test
    fun `set cursor position clamps to bounds`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(col = 100, row = 100)
        assertEquals(9, buf.cursorCol)
        assertEquals(4, buf.cursorRow)
        buf.setCursorPosition(col = -5, row = -5)
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `move cursor up`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursorPosition(col = 0, row = 10)
        buf.moveCursorUp(3)
        assertEquals(7, buf.cursorRow)
    }

    @Test
    fun `move cursor down`() {
        val buf = TerminalBuffer(80, 24)
        buf.moveCursorDown(5)
        assertEquals(5, buf.cursorRow)
    }

    @Test
    fun `move cursor left`() {
        val buf = TerminalBuffer(80, 24)
        buf.setCursorPosition(col = 10, row = 0)
        buf.moveCursorLeft(4)
        assertEquals(6, buf.cursorCol)
    }

    @Test
    fun `move cursor right`() {
        val buf = TerminalBuffer(80, 24)
        buf.moveCursorRight(15)
        assertEquals(15, buf.cursorCol)
    }

    @Test
    fun `move cursor clamped at top edge`() {
        val buf = TerminalBuffer(80, 24)
        buf.moveCursorUp(10)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `move cursor clamped at bottom edge`() {
        val buf = TerminalBuffer(80, 24)
        buf.moveCursorDown(100)
        assertEquals(23, buf.cursorRow)
    }

    @Test
    fun `move cursor clamped at left edge`() {
        val buf = TerminalBuffer(80, 24)
        buf.moveCursorLeft(5)
        assertEquals(0, buf.cursorCol)
    }

    @Test
    fun `move cursor clamped at right edge`() {
        val buf = TerminalBuffer(80, 24)
        buf.moveCursorRight(100)
        assertEquals(79, buf.cursorCol)
    }

    @Test
    fun `set attributes updates current attributes`() {
        val buf = TerminalBuffer(80, 24)
        buf.setAttributes(fg = Color.RED, bg = Color.BLUE, styles = setOf(Style.BOLD))
        assertEquals(Color.RED, buf.currentAttributes.foreground)
        assertEquals(Color.BLUE, buf.currentAttributes.background)
        assertEquals(setOf(Style.BOLD), buf.currentAttributes.styles)
    }

    @Test
    fun `write single character at origin`() {
        val buf = TerminalBuffer(10, 5)
        buf.write("A")
        assertEquals('A', buf.getCell(0, 0).char)
        assertEquals(1, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `write string and cursor advances`() {
        val buf = TerminalBuffer(20, 5)
        buf.write("Hello")
        assertEquals("Hello", buf.getScreenLine(0))
        assertEquals(5, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `write at end of line wraps to next line`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("ABCDE")
        // Cursor should have wrapped
        assertEquals(0, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
        buf.write("F")
        assertEquals("ABCDE", buf.getScreenLine(0))
        assertEquals("F", buf.getScreenLine(1))
    }

    @Test
    fun `write past bottom of screen triggers scroll`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA") // fills row 0, wraps to row 1
        buf.write("BBBBB") // fills row 1, wraps -> scroll
        // After scroll: row 0 should have "BBBBB", row 1 empty
        // The original "AAAAA" line scrolled into scrollback
        assertEquals("BBBBB", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
    }

    @Test
    fun `write uses current attributes`() {
        val buf = TerminalBuffer(10, 5)
        val attrs = CellAttributes(foreground = Color.RED, styles = setOf(Style.BOLD))
        buf.setAttributes(attrs)
        buf.write("X")
        assertEquals(attrs, buf.getCell(0, 0).attributes)
    }

    @Test
    fun `write with newline moves to next line`() {
        val buf = TerminalBuffer(10, 5)
        buf.write("AB\nCD")
        assertEquals("AB", buf.getScreenLine(0))
        assertEquals("CD", buf.getScreenLine(1))
        assertEquals(2, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
    }

    @Test
    fun `insert shifts existing content right`() {
        val buf = TerminalBuffer(10, 5)
        buf.write("BCD")
        buf.setCursorPosition(col = 0, row = 0)
        buf.insert("A")
        assertEquals("ABCD", buf.getScreenLine(0))
    }

    @Test
    fun `insert pushes content off right edge`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("ABCDE")
        buf.setCursorPosition(col = 0, row = 0)
        buf.insert("X")
        // "XABCD" — the E was pushed off
        assertEquals("XABCD", buf.getScreenLine(0))
    }

    @Test
    fun `insert uses current attributes`() {
        val buf = TerminalBuffer(10, 5)
        buf.setAttributes(fg = Color.CYAN)
        buf.insert("Z")
        assertEquals(Color.CYAN, buf.getCell(0, 0).attributes.foreground)
    }

    @Test
    fun `insert advances cursor`() {
        val buf = TerminalBuffer(10, 5)
        buf.insert("AB")
        assertEquals(2, buf.cursorCol)
    }

    @Test
    fun `fill line with character`() {
        val buf = TerminalBuffer(5, 3)
        buf.fillLine('#')
        assertEquals("#####", buf.getScreenLine(0))
    }

    @Test
    fun `fill line with default clears it`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("Hello")
        buf.setCursorPosition(col = 0, row = 0)
        buf.fillLine()
        assertEquals("", buf.getScreenLine(0))
    }

    @Test
    fun `fill line uses current attributes`() {
        val buf = TerminalBuffer(5, 3)
        val attrs = CellAttributes(background = Color.BLUE)
        buf.setAttributes(attrs)
        buf.fillLine('X')
        for (col in 0..<5) {
            assertEquals(attrs, buf.getCell(col, 0).attributes)
        }
    }

    @Test
    fun `fill line does not move cursor`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(col = 3, row = 2)
        buf.fillLine('*')
        assertEquals(3, buf.cursorCol)
        assertEquals(2, buf.cursorRow)
    }
}
