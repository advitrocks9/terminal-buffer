package terminalbuffer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals(0, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
        buf.write("F")
        assertEquals("ABCDE", buf.getScreenLine(0))
        assertEquals("F", buf.getScreenLine(1))
    }

    @Test
    fun `write past bottom of screen triggers scroll`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
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
    fun `insert cascades overflow to next line`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("ABCDE")
        buf.setCursorPosition(col = 0, row = 0)
        buf.insert("X")
        assertEquals("XABCD", buf.getScreenLine(0))
        assertEquals("E", buf.getScreenLine(1))
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

    @Test
    fun `writing past screen bottom moves top line to scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        assertEquals(1, buf.getScrollbackSize())
        assertEquals("AAAAA", buf.getScrollbackLine(0))
    }

    @Test
    fun `scrollback preserves line content and attributes`() {
        val buf = TerminalBuffer(5, 2)
        buf.setAttributes(fg = Color.RED)
        buf.write("HELLO")
        buf.setAttributes(fg = Color.DEFAULT)
        buf.write("WORLD")
        assertEquals("HELLO", buf.getScrollbackLine(0))
        assertEquals(Color.RED, buf.getScrollbackCell(0, 0).attributes.foreground)
    }

    @Test
    fun `scrollback evicts oldest line when max size exceeded`() {
        val buf = TerminalBuffer(3, 1, maxScrollbackSize = 2)
        buf.write("AAA")
        buf.write("BBB")
        buf.write("CCC")
        assertEquals(2, buf.getScrollbackSize())
        assertEquals("BBB", buf.getScrollbackLine(0))
        assertEquals("CCC", buf.getScrollbackLine(1))
    }

    @Test
    fun `scrollback with max size 0 discards all scrolled-off lines`() {
        val buf = TerminalBuffer(5, 2, maxScrollbackSize = 0)
        buf.write("AAAAA")
        buf.write("BBBBB")
        assertEquals(0, buf.getScrollbackSize())
    }

    @Test
    fun `insert line scrolls top line to scrollback`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("FIRST")
        buf.insertLine()
        assertEquals(1, buf.getScrollbackSize())
        assertEquals("FIRST", buf.getScrollbackLine(0))
    }

    @Test
    fun `insert line clears bottom line`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.setCursorPosition(col = 0, row = 1)
        buf.write("BBBBB")
        buf.insertLine()
        assertEquals("", buf.getScreenLine(1))
    }

    @Test
    fun `insert line shifts screen content up`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("AAA")
        buf.setCursorPosition(col = 0, row = 1)
        buf.write("BBB")
        buf.setCursorPosition(col = 0, row = 2)
        buf.write("CCC")
        buf.insertLine()
        assertEquals("BBB", buf.getScreenLine(0))
        assertEquals("CCC", buf.getScreenLine(1))
        assertEquals("", buf.getScreenLine(2))
    }

    @Test
    fun `clear screen empties screen but preserves scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        assertEquals(1, buf.getScrollbackSize())
        buf.clearScreen()
        assertEquals("", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
        assertEquals(1, buf.getScrollbackSize())
    }

    @Test
    fun `clear screen resets cursor`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(col = 5, row = 3)
        buf.clearScreen()
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `clear all empties everything`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        buf.clearAll()
        assertEquals("", buf.getScreenLine(0))
        assertEquals(0, buf.getScrollbackSize())
    }

    @Test
    fun `get scrollback line returns correct content`() {
        val buf = TerminalBuffer(5, 1)
        buf.write("FIRST")
        buf.write("SECND")
        assertEquals("FIRST", buf.getScrollbackLine(0))
    }

    @Test
    fun `get scrollback cell returns correct cell`() {
        val buf = TerminalBuffer(3, 1)
        buf.write("ABC")
        buf.write("DEF")
        assertEquals('A', buf.getScrollbackCell(0, 0).char)
        assertEquals('B', buf.getScrollbackCell(1, 0).char)
    }

    @Test
    fun `get full content includes scrollback and screen`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        val content = buf.getFullContent()
        assertEquals("AAAAA\nBBBBB\n", content)
    }

    @Test
    fun `1x1 buffer write single char scrolls immediately`() {
        val buf = TerminalBuffer(1, 1)
        buf.write("A")
        assertEquals("", buf.getScreenLine(0))
        assertEquals(1, buf.getScrollbackSize())
        assertEquals("A", buf.getScrollbackLine(0))
    }

    @Test
    fun `1x1 buffer write two chars scrolls both`() {
        val buf = TerminalBuffer(1, 1)
        buf.write("AB")
        assertEquals("", buf.getScreenLine(0))
        assertEquals(2, buf.getScrollbackSize())
        assertEquals("A", buf.getScrollbackLine(0))
        assertEquals("B", buf.getScrollbackLine(1))
    }

    @Test
    fun `wide buffer write long string`() {
        val buf = TerminalBuffer(200, 3)
        val text = "X".repeat(200)
        buf.write(text)
        assertEquals(text, buf.getScreenLine(0))
        assertEquals(0, buf.cursorCol)
        assertEquals(1, buf.cursorRow)
    }

    @Test
    fun `maxScrollbackSize 1 keeps only one line`() {
        val buf = TerminalBuffer(3, 1, maxScrollbackSize = 1)
        buf.write("AAA")
        buf.write("BBB")
        buf.write("CCC")
        assertEquals(1, buf.getScrollbackSize())
        assertEquals("CCC", buf.getScrollbackLine(0))
    }

    @Test
    fun `write string longer than entire screen`() {
        val buf = TerminalBuffer(3, 2)
        buf.write("ABCDEFGHIJKL")
        assertEquals("JKL", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
    }

    @Test
    fun `write after setting cursor to middle of screen`() {
        val buf = TerminalBuffer(10, 5)
        buf.setCursorPosition(col = 3, row = 2)
        buf.write("Hi")
        assertEquals(5, buf.cursorCol)
        assertEquals(2, buf.cursorRow)
        assertEquals("Hi", buf.getScreenLine(2).trim())
    }

    @Test
    fun `multiple writes accumulate correctly`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("Hello")
        buf.write(" World")
        assertEquals("Hello Worl", buf.getScreenLine(0))
        assertEquals("d", buf.getScreenLine(1))
    }

    @Test
    fun `attributes change between writes and cells preserve their own`() {
        val buf = TerminalBuffer(10, 3)
        buf.setAttributes(fg = Color.RED)
        buf.write("A")
        buf.setAttributes(fg = Color.BLUE)
        buf.write("B")
        assertEquals(Color.RED, buf.getCell(0, 0).attributes.foreground)
        assertEquals(Color.BLUE, buf.getCell(1, 0).attributes.foreground)
    }

    @Test
    fun `getScreenContent with mixed trailing spaces`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("Hello")
        buf.setCursorPosition(col = 0, row = 1)
        buf.write("Hi   there")
        assertEquals("Hello\nHi   there\n", buf.getScreenContent())
    }

    @Test
    fun `scrollback is not affected by subsequent screen writes`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        assertEquals("AAAAA", buf.getScrollbackLine(0))
        buf.setCursorPosition(col = 0, row = 0)
        buf.write("XXXXX")
        assertEquals("AAAAA", buf.getScrollbackLine(0))
    }

    @Test
    fun `write CJK character occupies 2 cells`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("\u4e16")
        assertEquals('\u4e16', buf.getCell(0, 0).char)
        assertTrue(buf.getCell(1, 0).isWideExtension)
    }

    @Test
    fun `cursor advances by 2 after writing wide char`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("\u4e16")
        assertEquals(2, buf.cursorCol)
    }

    @Test
    fun `getText returns wide char once not twice`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("\u4e16")
        assertEquals("\u4e16", buf.getScreenLine(0))
    }

    @Test
    fun `wide char at end of line with only 1 cell left wraps`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("ABCD")
        buf.write("\u4e16")
        assertEquals("ABCD", buf.getScreenLine(0))
        assertEquals("\u4e16", buf.getScreenLine(1))
    }

    @Test
    fun `overwrite first cell of wide char clears extension`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("\u4e16")
        buf.setCursorPosition(col = 0, row = 0)
        buf.write("X")
        assertEquals('X', buf.getCell(0, 0).char)
        assertFalse(buf.getCell(1, 0).isWideExtension)
    }

    @Test
    fun `overwrite extension cell of wide char clears primary`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("\u4e16")
        buf.setCursorPosition(col = 1, row = 0)
        buf.write("Y")
        assertEquals(Cell.EMPTY_CHAR, buf.getCell(0, 0).char)
        assertEquals('Y', buf.getCell(1, 0).char)
    }

    @Test
    fun `mix of normal and wide characters`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("A\u4e16B")
        assertEquals("A\u4e16B", buf.getScreenLine(0))
        assertEquals(4, buf.cursorCol)
    }

    @Test
    fun `shrink height moves lines to scrollback`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("AAA")
        buf.setCursorPosition(col = 0, row = 1)
        buf.write("BBB")
        buf.setCursorPosition(col = 0, row = 2)
        buf.write("CCC")
        buf.resize(5, 2)
        assertEquals(2, buf.height)
        assertEquals(1, buf.getScrollbackSize())
        assertEquals("BBB", buf.getScreenLine(0))
        assertEquals("CCC", buf.getScreenLine(1))
    }

    @Test
    fun `grow height adds empty lines at top`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAA")
        buf.setCursorPosition(col = 0, row = 1)
        buf.write("BBB")
        buf.resize(5, 4)
        assertEquals(4, buf.height)
        assertEquals("AAA", buf.getScreenLine(2))
        assertEquals("BBB", buf.getScreenLine(3))
        assertEquals("", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
    }

    @Test
    fun `shrink width truncates content`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("Hello World")
        buf.resize(5, 3)
        assertEquals(5, buf.width)
        assertEquals("Hello", buf.getScreenLine(0))
    }

    @Test
    fun `grow width pads with empty cells`() {
        val buf = TerminalBuffer(5, 3)
        buf.write("Hi")
        buf.resize(10, 3)
        assertEquals(10, buf.width)
        assertEquals("Hi", buf.getScreenLine(0))
        assertEquals(Cell.EMPTY_CHAR, buf.getCell(5, 0).char)
    }

    @Test
    fun `cursor clamped after resize`() {
        val buf = TerminalBuffer(10, 10)
        buf.setCursorPosition(col = 8, row = 8)
        buf.resize(5, 5)
        assertEquals(4, buf.cursorCol)
        assertEquals(4, buf.cursorRow)
    }

    @Test
    fun `resize to same dimensions is a no-op`() {
        val buf = TerminalBuffer(10, 5)
        buf.write("Hello")
        buf.resize(10, 5)
        assertEquals("Hello", buf.getScreenLine(0))
    }

    @Test
    fun `resize to 1x1`() {
        val buf = TerminalBuffer(10, 5)
        buf.write("Hello")
        buf.resize(1, 1)
        assertEquals(1, buf.width)
        assertEquals(1, buf.height)
        assertEquals(0, buf.cursorCol)
        assertEquals(0, buf.cursorRow)
    }

    @Test
    fun `resize taller pulls lines from scrollback`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        assertEquals(1, buf.getScrollbackSize())
        buf.resize(5, 3)
        assertEquals(0, buf.getScrollbackSize())
        assertEquals("AAAAA", buf.getScreenLine(0))
        assertEquals("BBBBB", buf.getScreenLine(1))
    }

    @Test
    fun `resize taller with empty scrollback gets empty lines`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("Hello")
        buf.resize(5, 4)
        assertEquals("", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
        assertEquals("Hello", buf.getScreenLine(2))
    }

    @Test
    fun `resize taller pulls only available scrollback lines`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        assertEquals(1, buf.getScrollbackSize())
        buf.resize(5, 5)
        assertEquals(0, buf.getScrollbackSize())
        assertEquals("AAAAA", buf.getScreenLine(0))
        assertEquals("", buf.getScreenLine(1))
        assertEquals("", buf.getScreenLine(2))
        assertEquals("BBBBB", buf.getScreenLine(3))
    }

    @Test
    fun `resize taller handles different width scrollback lines`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.write("BBBBB")
        buf.resize(3, 3)
        assertEquals("AAA", buf.getScreenLine(0))
        assertEquals("BBB", buf.getScreenLine(1))
    }

    @Test
    fun `insert causes multi-line cascade`() {
        val buf = TerminalBuffer(3, 4)
        buf.write("ABC")
        buf.setCursorPosition(col = 0, row = 1)
        buf.write("DEF")
        buf.setCursorPosition(col = 0, row = 2)
        buf.write("GHI")
        buf.setCursorPosition(col = 0, row = 0)
        buf.insert("X")
        assertEquals("XAB", buf.getScreenLine(0))
        assertEquals("CDE", buf.getScreenLine(1))
        assertEquals("FGH", buf.getScreenLine(2))
        assertEquals("I", buf.getScreenLine(3))
    }

    @Test
    fun `insert cascade causes scrollback`() {
        val buf = TerminalBuffer(3, 2)
        buf.write("ABC")
        buf.setCursorPosition(0, 1)
        buf.fillLine('D')
        buf.setCursorPosition(0, 0)
        buf.insert("X")
        assertEquals(1, buf.getScrollbackSize())
        assertEquals("XAB", buf.getScrollbackLine(0))
        assertEquals("CDD", buf.getScreenLine(0))
        assertEquals("D", buf.getScreenLine(1))
    }

    @Test
    fun `insert on last screen line causing scroll`() {
        val buf = TerminalBuffer(5, 2)
        buf.write("AAAAA")
        buf.setCursorPosition(0, 1)
        buf.fillLine('B')
        buf.setCursorPosition(0, 1)
        buf.insert("X")
        assertEquals(1, buf.getScrollbackSize())
        assertEquals("AAAAA", buf.getScrollbackLine(0))
        assertEquals("XBBBB", buf.getScreenLine(0))
        assertEquals("B", buf.getScreenLine(1))
    }

    @Test
    fun `insert wide char at start of line`() {
        val buf = TerminalBuffer(10, 3)
        buf.write("ABCDE")
        buf.setCursorPosition(col = 0, row = 0)
        buf.insert("\u4e16")
        assertEquals('\u4e16', buf.getCell(0, 0).char)
        assertTrue(buf.getCell(1, 0).isWideExtension)
        assertEquals('A', buf.getCell(2, 0).char)
        assertEquals('B', buf.getCell(3, 0).char)
        assertEquals(2, buf.cursorCol)
    }

    @Test
    fun `insert wide char in middle pushing content right`() {
        val buf = TerminalBuffer(8, 3)
        buf.write("ABCDE")
        buf.setCursorPosition(col = 2, row = 0)
        buf.insert("\u4e16")
        assertEquals('A', buf.getCell(0, 0).char)
        assertEquals('B', buf.getCell(1, 0).char)
        assertEquals('\u4e16', buf.getCell(2, 0).char)
        assertTrue(buf.getCell(3, 0).isWideExtension)
        assertEquals('C', buf.getCell(4, 0).char)
        assertEquals('D', buf.getCell(5, 0).char)
        assertEquals('E', buf.getCell(6, 0).char)
    }

    @Test
    fun `insert wide char at last column wraps to next line`() {
        val buf = TerminalBuffer(5, 3)
        buf.setCursorPosition(col = 4, row = 0)
        buf.insert("\u4e16")
        assertEquals(1, buf.cursorRow)
        assertEquals(2, buf.cursorCol)
        assertEquals('\u4e16', buf.getCell(0, 1).char)
        assertTrue(buf.getCell(1, 1).isWideExtension)
    }

    @Test
    fun `insert wide char splits existing wide pair at boundary clears orphan`() {
        val buf = TerminalBuffer(6, 3)
        buf.write("\u4e16\u4e16\u4e16")
        buf.setCursorPosition(col = 0, row = 0)
        buf.insert("\u5730")
        assertEquals('\u5730', buf.getCell(0, 0).char)
        assertTrue(buf.getCell(1, 0).isWideExtension)
        assertEquals('\u4e16', buf.getCell(2, 0).char)
        assertTrue(buf.getCell(3, 0).isWideExtension)
        assertEquals('\u4e16', buf.getCell(4, 0).char)
        assertTrue(buf.getCell(5, 0).isWideExtension)
    }

    @Test
    fun `fillLine with wide char on even-width line fills in pairs`() {
        val buf = TerminalBuffer(6, 3)
        buf.fillLine('\u4e16')
        assertEquals('\u4e16', buf.getCell(0, 0).char)
        assertTrue(buf.getCell(1, 0).isWideExtension)
        assertEquals('\u4e16', buf.getCell(2, 0).char)
        assertTrue(buf.getCell(3, 0).isWideExtension)
        assertEquals('\u4e16', buf.getCell(4, 0).char)
        assertTrue(buf.getCell(5, 0).isWideExtension)
    }

    @Test
    fun `fillLine with wide char on odd-width line last cell is space`() {
        val buf = TerminalBuffer(5, 3)
        buf.fillLine('\u4e16')
        assertEquals('\u4e16', buf.getCell(0, 0).char)
        assertTrue(buf.getCell(1, 0).isWideExtension)
        assertEquals('\u4e16', buf.getCell(2, 0).char)
        assertTrue(buf.getCell(3, 0).isWideExtension)
        assertEquals(Cell.EMPTY_CHAR, buf.getCell(4, 0).char)
        assertFalse(buf.getCell(4, 0).isWideExtension)
    }

    @Test
    fun `fillLine with normal char regression`() {
        val buf = TerminalBuffer(5, 3)
        buf.fillLine('#')
        for (col in 0..<5) {
            assertEquals('#', buf.getCell(col, 0).char)
            assertFalse(buf.getCell(col, 0).isWideExtension)
        }
    }

    @Test
    fun `getCell out of row bounds throws`() {
        val buf = TerminalBuffer(10, 5)
        val ex1 = assertFailsWith<IllegalArgumentException> { buf.getCell(0, -1) }
        assertTrue(ex1.message!!.contains("height=5"))
        val ex2 = assertFailsWith<IllegalArgumentException> { buf.getCell(0, 5) }
        assertTrue(ex2.message!!.contains("height=5"))
    }

    @Test
    fun `getCell out of col bounds throws`() {
        val buf = TerminalBuffer(10, 5)
        val ex1 = assertFailsWith<IllegalArgumentException> { buf.getCell(-1, 0) }
        assertTrue(ex1.message!!.contains("width=10"))
        val ex2 = assertFailsWith<IllegalArgumentException> { buf.getCell(10, 0) }
        assertTrue(ex2.message!!.contains("width=10"))
    }

    @Test
    fun `getScreenLine out of bounds throws`() {
        val buf = TerminalBuffer(10, 5)
        val ex1 = assertFailsWith<IllegalArgumentException> { buf.getScreenLine(-1) }
        assertTrue(ex1.message!!.contains("height=5"))
        val ex2 = assertFailsWith<IllegalArgumentException> { buf.getScreenLine(5) }
        assertTrue(ex2.message!!.contains("height=5"))
    }

    @Test
    fun `getScrollbackLine out of bounds throws`() {
        val buf = TerminalBuffer(5, 1)
        buf.write("AAAAA")
        val ex = assertFailsWith<IllegalArgumentException> { buf.getScrollbackLine(1) }
        assertTrue(ex.message!!.contains("size=1"))
    }

    @Test
    fun `getScrollbackCell out of bounds throws`() {
        val buf = TerminalBuffer(5, 1)
        buf.write("AAAAA")
        val exRow = assertFailsWith<IllegalArgumentException> { buf.getScrollbackCell(0, 1) }
        assertTrue(exRow.message!!.contains("size=1"))
        val exCol = assertFailsWith<IllegalArgumentException> { buf.getScrollbackCell(5, 0) }
        assertTrue(exCol.message!!.contains("width=5"))
    }
}
