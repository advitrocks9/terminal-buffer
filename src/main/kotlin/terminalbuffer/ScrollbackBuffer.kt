package terminalbuffer

/**
 * Bounded buffer for scrollback lines.
 * Oldest lines are evicted when [maxSize] is reached.
 *
 * TODO: use a ring buffer for O(1) eviction in large scrollback.
 */
internal class ScrollbackBuffer(private val maxSize: Int) {
    private val lines = ArrayDeque<Line>()

    val size: Int get() = lines.size

    fun add(line: Line) {
        if (maxSize <= 0) return
        lines.addLast(line)
        if (lines.size > maxSize) {
            lines.removeFirst()
        }
    }

    operator fun get(index: Int): Line = lines[index]

    fun removeLast(): Line? = if (lines.isEmpty()) null else lines.removeLast()

    fun clear() = lines.clear()

    fun isEmpty(): Boolean = lines.isEmpty()

    fun asSequence(): Sequence<Line> = lines.asSequence()
}
