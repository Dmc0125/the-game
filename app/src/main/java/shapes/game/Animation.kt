package shapes.game

fun lerpColor(start: Int, end: Int, progress: Float): Int {
    val a = (start shr 24) and 0xFF
    val r = (start shr 16) and 0xFF
    val g = (start shr 8) and 0xFF
    val b = start and 0xFF

    val targetA = (end shr 24) and 0xFF
    val targetR = (end shr 16) and 0xFF
    val targetG = (end shr 8) and 0xFF
    val targetB = end and 0xFF

    return Color.argb(
        lerp(a, targetA, progress),
        lerp(r, targetR, progress),
        lerp(g, targetG, progress),
        lerp(b, targetB, progress),
    )
}

fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress
fun lerp(start: Vec2, end: Vec2, progress: Float): Vec2 = start + (end - start) * progress
fun lerp(start: Int, end: Int, progress: Float): Int = (start + (end - start) * progress).toInt()

fun easeInSquared(progress: Float): Float = progress * progress
fun easeOutSquared(progress: Float): Float = 1f - easeInSquared(1f - progress)

fun easeInCubic(progress: Float): Float = progress * progress * progress
fun easeOutCubic(progress: Float): Float = 1f - easeInCubic(1f - progress)

enum class AnimationEasing {
    Linear,
    EaseInSquared,
    EaseOutSquared,
    EaseInCubic,
    EaseOutCubic,
}

val easeFunctions = mapOf(
    AnimationEasing.Linear to { progress: Float -> progress },
    AnimationEasing.EaseInSquared to ::easeInSquared,
    AnimationEasing.EaseOutSquared to ::easeOutSquared,
    AnimationEasing.EaseInCubic to ::easeInCubic,
    AnimationEasing.EaseOutCubic to ::easeOutCubic,
)

class Anim {
    var running = false
    var startTime = 0f
    var duration = 0f
    var delay = 0f
    var current = 0f
}

fun animBegin(anim: Anim, duration: Float, elapsedTime: Float, delay: Float = 0f) {
    anim.running = true
    anim.startTime = elapsedTime
    anim.duration = duration
    anim.delay = delay
    anim.current = 0f
}

fun animUpdate(anim: Anim, elapsedTime: Float): Boolean {
    if (!anim.running) {
        return false
    }

    var elapsed = elapsedTime - anim.startTime
    if (elapsed < anim.delay) {
        return true
    }

    elapsed -= anim.delay
    if (elapsed >= anim.duration) {
        anim.current = 1f
        anim.running = false
        return false
    }

    anim.current = elapsed / anim.duration
    return true
}

fun <T> animCurrent(
    anim: Anim,
    from: T,
    to: T,
    lerp: (start: T, end: T, progress: Float) -> T,
    easing: AnimationEasing = AnimationEasing.Linear,
    reversed: Boolean = false
): T {
    val progress = if (reversed) {
        1f - anim.current
    } else {
        anim.current
    }
    var t = easeFunctions[easing]?.invoke(progress) ?: progress
    return lerp(from, to, t)
}
