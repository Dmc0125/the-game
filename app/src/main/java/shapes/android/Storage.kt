package shapes.android

import android.content.Context
import android.content.SharedPreferences

const val STORAGE_NAME = "game"
const val STORAGE_HIGH_SCORE = "high_score"

object Storage {
    lateinit var preferences: SharedPreferences

    fun init(context: Context) {
        preferences = context.getSharedPreferences(
            STORAGE_NAME,
            Context.MODE_PRIVATE,
        )
    }

    fun saveHighScore(score: Int) {
        assert(::preferences.isInitialized)
        preferences.edit().putInt(STORAGE_HIGH_SCORE, score).apply()
    }

    fun highScore(): Int {
        assert(::preferences.isInitialized)
        return preferences.getInt(STORAGE_HIGH_SCORE, 0)
    }
}
