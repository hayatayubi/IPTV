package com.hayat.lumen.ui.home

import com.hayat.lumen.ui.home.GridMove.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridMoveTest {
    // 10 cards, 4 across:  0 1 2 3 / 4 5 6 7 / 8 9
    private fun target(from: Int, d: Direction) = GridMove.target(from, size = 10, columns = 4, direction = d)

    @Test
    fun leftAndRightFollowReadingOrder() {
        assertEquals(4, target(3, Direction.RIGHT))
        assertEquals(3, target(4, Direction.LEFT))
        assertNull(target(0, Direction.LEFT))
        assertNull(target(9, Direction.RIGHT))
    }

    @Test
    fun upAndDownJumpARow() {
        assertEquals(1, target(5, Direction.UP))
        assertEquals(9, target(5, Direction.DOWN))
        assertNull(target(2, Direction.UP))
        assertNull(target(8, Direction.DOWN))
    }

    @Test
    fun downOntoAShortLastRowLandsOnTheLastCard() {
        assertEquals(9, target(7, Direction.DOWN))
        assertEquals(9, target(6, Direction.DOWN))
    }

    @Test
    fun moveTakesOutAndReinserts() {
        assertEquals(listOf("b", "c", "a", "d"), GridMove.move(listOf("a", "b", "c", "d"), 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), GridMove.move(listOf("a", "b", "c", "d"), 3, 0))
    }
}
