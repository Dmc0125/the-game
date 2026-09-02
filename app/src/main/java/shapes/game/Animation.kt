package shapes.game

class Keyframe(
    val until: Float,
    val from: Float,
    val to: Float,
    val easing: AnimationEasing,
)

fun keyframeCurrent(anim: Anim, frames: Array<Keyframe>): Float {
    val progress = animCurrent(anim, 0f, 1f, ::lerp)
    var elapsed = 0f

    for (k in frames) {
        if (progress <= k.until) {
            val currentDuration = k.until - elapsed
            val currentProgress = (progress - elapsed) / currentDuration
            val t = easeFunctions[k.easing]?.invoke(currentProgress) ?: currentProgress
            return lerp(k.from, k.to, t)
        } else {
            elapsed = k.until
        }
    }

    throw IllegalArgumentException("progress $progress is out of bounds for animation")
}

val shrinkKeyframe = arrayOf(
    Keyframe(0.2f, 1f, 0.98f, AnimationEasing.EaseInSquared),
    Keyframe(0.8f, 0.98f, 1.005f, AnimationEasing.EaseInSquared),
    Keyframe(1f, 1.005f, 1f, AnimationEasing.EaseInSquared),
)

val popKeyframe = arrayOf(
    Keyframe(0.2f, 1f, 1.2f, AnimationEasing.EaseInSquared),
    Keyframe(0.8f, 1.2f, 0.9f, AnimationEasing.EaseOutSquared),
    Keyframe(1f, 0.9f, 1f, AnimationEasing.EaseOutSquared),
)

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

enum class AnimState {
    None,
    Delay,
    Running,
}

class Anim {
    var state = AnimState.None
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

    if (delay > 0f) {
        anim.state = AnimState.Delay
    } else {
        anim.state = AnimState.Running
    }
}

fun animUpdate(anim: Anim, elapsedTime: Float): Boolean {
    if (!anim.running) {
        return false
    }

    var elapsed = elapsedTime - anim.startTime
    if (elapsed < anim.delay) {
        anim.state = AnimState.Delay
        return true
    }

    elapsed -= anim.delay
    if (elapsed >= anim.duration) {
        anim.current = 1f
        anim.running = false
        anim.state = AnimState.None
        return false
    }

    anim.state = AnimState.Running
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
