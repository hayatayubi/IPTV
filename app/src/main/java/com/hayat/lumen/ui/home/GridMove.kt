package com.hayat.lumen.ui.home

/** Where a card being moved goes in a grid of [columns] across, for each D-pad direction. */
object GridMove {
    enum class Direction { LEFT, RIGHT, UP, DOWN }

    /**
     * The index the card at [from] moves to, or null if it's already at that edge.
     * Left/Right step through the list (so they wrap onto the row above or below, like
     * reading order); Up/Down jump a whole row.
     */
    fun target(from: Int, size: Int, columns: Int, direction: Direction): Int? {
        val to = when (direction) {
            Direction.LEFT -> from - 1
            Direction.RIGHT -> from + 1
            Direction.UP -> from - columns
            // Down from the last full row lands on the last card rather than nowhere.
            Direction.DOWN -> if (from + columns < size) from + columns else if (from / columns < (size - 1) / columns) size - 1 else -1
        }
        return to.takeIf { it in 0 until size && it != from }
    }

    /** [list] with the item at [from] taken out and put back in at [to]. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> =
        list.toMutableList().apply { add(to, removeAt(from)) }
}
