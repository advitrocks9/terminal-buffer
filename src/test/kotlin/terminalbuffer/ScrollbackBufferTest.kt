package terminalbuffer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrollbackBufferTest {
    @Test
    fun `add and retrieve lines in order`() {
        val sb = ScrollbackBuffer(10)
        val line1 = Line(5).also { it[0] = Cell('A') }
        val line2 = Line(5).also { it[0] = Cell('B') }
        sb.add(line1)
        sb.add(line2)
        assertEquals(2, sb.size)
        assertEquals('A', sb[0][0].char)
        assertEquals('B', sb[1][0].char)
    }

    @Test
    fun `evicts oldest line when maxSize exceeded`() {
        val sb = ScrollbackBuffer(2)
        sb.add(Line(3).also { it[0] = Cell('A') })
        sb.add(Line(3).also { it[0] = Cell('B') })
        sb.add(Line(3).also { it[0] = Cell('C') })
        assertEquals(2, sb.size)
        assertEquals('B', sb[0][0].char)
        assertEquals('C', sb[1][0].char)
    }

    @Test
    fun `maxSize 0 discards all lines`() {
        val sb = ScrollbackBuffer(0)
        sb.add(Line(5).also { it[0] = Cell('X') })
        assertEquals(0, sb.size)
        assertTrue(sb.isEmpty())
    }

    @Test
    fun `removeLast returns last added line`() {
        val sb = ScrollbackBuffer(10)
        sb.add(Line(3).also { it[0] = Cell('A') })
        sb.add(Line(3).also { it[0] = Cell('B') })
        val removed = sb.removeLast()!!
        assertEquals('B', removed[0].char)
        assertEquals(1, sb.size)
        assertEquals('A', sb[0][0].char)
    }

    @Test
    fun `removeLast on empty returns null`() {
        val sb = ScrollbackBuffer(10)
        assertNull(sb.removeLast())
    }

    @Test
    fun `clear empties the buffer`() {
        val sb = ScrollbackBuffer(10)
        sb.add(Line(3))
        sb.add(Line(3))
        sb.clear()
        assertEquals(0, sb.size)
        assertTrue(sb.isEmpty())
    }

    @Test
    fun `asSequence iterates in insertion order`() {
        val sb = ScrollbackBuffer(10)
        sb.add(Line(3).also { it[0] = Cell('A') })
        sb.add(Line(3).also { it[0] = Cell('B') })
        sb.add(Line(3).also { it[0] = Cell('C') })
        val chars = sb.asSequence().map { it[0].char }.toList()
        assertEquals(listOf('A', 'B', 'C'), chars)
    }
}
