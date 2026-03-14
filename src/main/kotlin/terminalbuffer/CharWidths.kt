package terminalbuffer

/** Terminal character width detection for CJK and common wide character ranges. */
internal object CharWidths {
    fun charWidth(c: Char): Int {
        val code = c.code
        return when {
            code in 0x3000..0x303F -> 2 // CJK Symbols and Punctuation
            code in 0x3040..0x309F -> 2 // Hiragana
            code in 0x30A0..0x30FF -> 2 // Katakana
            code in 0x3400..0x4DBF -> 2 // CJK Extension A
            code in 0x4E00..0x9FFF -> 2 // CJK Unified Ideographs
            code in 0xAC00..0xD7AF -> 2 // Hangul Syllables
            code in 0xF900..0xFAFF -> 2 // CJK Compatibility Ideographs
            code in 0xFF01..0xFF60 -> 2 // Fullwidth Forms
            else -> 1
        }
    }
}
