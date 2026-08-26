package shapes.game

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

class Anim<T>(var current: T, var lerp: (start: T, end: T, progress: Float) -> T) {
    var from = current
    var to = current
    var startTime: Float = 0f

    var running = false
    var duration = 0f
    var delay = 0f
    var easing = AnimationEasing.Linear
}

fun <T> animBegin(
    anim: Anim<T>,
    current: T,
    to: T = anim.to,
    easing: AnimationEasing = anim.easing,
    delay: Float = 0f,
    duration: Float = anim.duration,
    lerp: (start: T, end: T, progress: Float) -> T = anim.lerp,
    elapsedTime: Float,
) {
    anim.running = true
    anim.current = current
    anim.from = current
    anim.to = to
    anim.easing = easing
    anim.delay = delay
    anim.startTime = elapsedTime
}

fun <T> animUpdate(anim: Anim<T>, elapsedTime: Float) {
    if (!anim.running) {
        return
    }

    val time = elapsedTime - anim.startTime
    if (time < anim.delay) {
        return
    }

    val progress = (time - anim.delay) / anim.duration
    if (progress >= 1f) {
        anim.current = anim.to
        anim.running = false
        return
    }

    val t = easeFunctions[anim.easing]?.invoke(progress) ?: progress
    anim.current = anim.lerp(anim.from, anim.to, t)
}
