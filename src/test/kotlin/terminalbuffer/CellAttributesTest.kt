package terminalbuffer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CellAttributesTest {
    @Test
    fun `default attributes have DEFAULT colors and no styles`() {
        val attrs = CellAttributes.DEFAULT
        assertEquals(Color.DEFAULT, attrs.foreground)
        assertEquals(Color.DEFAULT, attrs.background)
        assertTrue(attrs.styles.isEmpty())
    }

    @Test
    fun `empty cell has space char and default attributes`() {
        val cell = Cell.EMPTY
        assertEquals(Cell.EMPTY_CHAR, cell.char)
        assertEquals(CellAttributes.DEFAULT, cell.attributes)
    }
}
