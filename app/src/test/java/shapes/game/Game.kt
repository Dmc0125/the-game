package shapes.game

import org.junit.Test
import org.junit.Assert.*

// TODO: must not import android.* or androidx.* in game

class ShapesBagTest {
    @Test
    fun testShuffleInit() {
        val bag = ShapesBag()
        val bagSize = shapes.size / 4
        val expected = IntArray(bagSize * 2)

        for (bag in 0..<2) {
            for (shape in 0..<bagSize) {
                expected[bag * bagSize + shape] = shape
            }
        }

        assertArrayEquals(expected, bag.indexes)
    }

    @Test
    fun `test first next`() {
        val bag = ShapesBag()
        val result = bag.next()
        val expected = bag.indexes[0]
        assertEquals(expected, result)
    }

    @Test
    fun `test shuffle`() {
        val bag = ShapesBag()
        val bagSize = shapes.size / 4

        // first bag
        val firstRound = mutableListOf<Int>()

        firstRound.add(bag.next())
        val initialShuffle = bag.indexes.sliceArray(0..<bagSize)

        for (i in 1..<bagSize) {
            firstRound.add(bag.next())
        }

        for (idx in firstRound.indices) {
            val el = firstRound[idx]
            assertEquals(initialShuffle[idx], el)

            // test uniqueness
            for ((elIdx2, el2) in firstRound.withIndex()) {
                if (elIdx2 != idx) {
                    assertNotEquals(idx, elIdx2)
                }
            }
        }

        // second bag

    }
}
