package shapes.game

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

object AppFont {
    lateinit var extraLight: Typeface
    lateinit var light: Typeface
    lateinit var regular: Typeface
    lateinit var medium: Typeface
    lateinit var semiBold: Typeface
    lateinit var bold: Typeface
    lateinit var extraBold: Typeface

    lateinit var monoRegular: Typeface
    lateinit var monoMedium: Typeface

    lateinit var famExtraLight: FontFamily
    lateinit var famLight: FontFamily
    lateinit var famRegular: FontFamily
    lateinit var famMedium: FontFamily
    lateinit var famSemiBold: FontFamily
    lateinit var famBold: FontFamily
    lateinit var famExtraBold: FontFamily

    lateinit var famMonoRegular: FontFamily
    lateinit var famMonoMedium: FontFamily

    fun init(context: Context) {
        val assets = context.applicationContext.assets
        extraLight = Typeface.createFromAsset(assets, "font/manrope_extralight.ttf")!!
        famExtraLight = FontFamily(extraLight)
        light = Typeface.createFromAsset(assets, "font/manrope_light.ttf")!!
        famLight = FontFamily(light)
        regular = Typeface.createFromAsset(assets, "font/manrope_regular.ttf")!!
        famRegular = FontFamily(regular)
        medium = Typeface.createFromAsset(assets, "font/manrope_medium.ttf")!!
        famMedium = FontFamily(medium)
        semiBold = Typeface.createFromAsset(assets, "font/manrope_semibold.ttf")!!
        famSemiBold = FontFamily(semiBold)
        bold = Typeface.createFromAsset(assets, "font/manrope_bold.ttf")!!
        famBold = FontFamily(bold)
        extraBold = Typeface.createFromAsset(assets, "font/manrope_extrabold.ttf")!!
        famExtraBold = FontFamily(extraBold)

        monoRegular = Typeface.createFromAsset(assets, "font/DMMono-Regular.ttf")!!
        famMonoRegular = FontFamily(monoRegular)
        monoMedium = Typeface.createFromAsset(assets, "font/DMMono-Medium.ttf")!!
        famMonoMedium = FontFamily(monoMedium)
    }
}
