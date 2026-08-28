package shapes.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapesBagTest {
    @Test
    fun `first shape is available without initializing the bag`() {
        val bag = ShapesBag()

        val shape = shapesBagNext(bag)

        assertValidShape(shape)
        assertEquals(1, bag.next)
    }

    @Test
    fun `each bag contains the expected shape roles`() {
        val bag = ShapesBag()
        val expectedRoles = listOf(
            ShapeRole.Recovery,
            ShapeRole.Normal,
            ShapeRole.Recovery,
            ShapeRole.Hard,
            ShapeRole.Recovery,
            ShapeRole.Normal,
            ShapeRole.Recovery,
            ShapeRole.Hard,
            ShapeRole.Recovery,
            ShapeRole.Normal,
        )

        val bagOne = (0 until 10).map { shapesBagNext(bag).role }
        assertEquals(expectedRoles, bagOne)

        val bagTwo = (10 until 20).map { shapesBagNext(bag).role }
        assertEquals(expectedRoles, bagTwo)
    }

    @Test
    fun `next shape is available when the current bag is exhausted`() {
        val bag = ShapesBag()

        repeat(10) { shapesBagNext(bag) }
        val firstShapeFromNextBag = shapesBagNext(bag)

        assertValidShape(firstShapeFromNextBag)
        assertEquals(11, bag.next)
    }

    @Test
    fun `next shape remains available across repeated bag rollovers`() {
        val bag = ShapesBag()

        repeat(100) {
            assertValidShape(shapesBagNext(bag))
        }
    }

    private fun assertValidShape(shape: Shape) {
        val roleShapes = SHAPES[shape.role]
        assertTrue("Unknown shape role: ${shape.role}", roleShapes != null)
        assertTrue("Invalid shape index: ${shape.idx}", shape.idx in roleShapes!!.indices)
    }
}
