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
}
