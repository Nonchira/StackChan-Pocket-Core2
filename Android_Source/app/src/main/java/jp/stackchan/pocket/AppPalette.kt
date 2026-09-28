package jp.stackchan.pocket

import android.content.SharedPreferences
import android.graphics.Color

data class AppPalette(val id:String,val label:String,val style:Int,val bg:Int,val surface:Int,val ink:Int,val muted:Int,val accent:Int,val control:Int,val userBg:Int,val userCaption:Int)

object AppPalettes {
    val all=listOf(
        AppPalette("classic","チャコール",R.style.PocketThemeClassic,Color.rgb(27,28,35),Color.rgb(32,34,42),Color.rgb(228,230,235),Color.rgb(162,167,180),Color.rgb(123,201,193),Color.rgb(53,59,70),Color.rgb(38,62,87),Color.rgb(182,212,243)),
        AppPalette("orange","オレンジ",R.style.PocketThemeOrange,Color.parseColor("#1C1612"),Color.parseColor("#2B211A"),Color.parseColor("#FFF4E8"),Color.parseColor("#CAB5A1"),Color.parseColor("#FFA448"),Color.parseColor("#452F1F"),Color.parseColor("#4E2E18"),Color.parseColor("#FFC68F")),
        AppPalette("mint","ミント",R.style.PocketThemeMint,Color.parseColor("#101817"),Color.parseColor("#1B2926"),Color.parseColor("#ECF4F0"),Color.parseColor("#A8BBB2"),Color.parseColor("#A8E5CD"),Color.parseColor("#2E413B"),Color.parseColor("#264036"),Color.parseColor("#ADD2BF")),
        AppPalette("blue","ブルー",R.style.PocketThemeBlue,Color.parseColor("#141923"),Color.parseColor("#202B3B"),Color.parseColor("#EDF3FF"),Color.parseColor("#AFBCD2"),Color.parseColor("#91BDFF"),Color.parseColor("#30425D"),Color.parseColor("#293F60"),Color.parseColor("#B5D2FF")),
        AppPalette("rose","レッド",R.style.PocketThemeRose,Color.parseColor("#221313"),Color.parseColor("#352020"),Color.parseColor("#FFF1EF"),Color.parseColor("#D0B3AF"),Color.parseColor("#FF625C"),Color.parseColor("#58302C"),Color.parseColor("#612A24"),Color.parseColor("#FFAEA5"))
        ,AppPalette("gray","グレー",R.style.PocketThemeGray,Color.parseColor("#191919"),Color.parseColor("#262626"),Color.parseColor("#F0F0F0"),Color.parseColor("#B8B8B8"),Color.parseColor("#C8C8C8"),Color.parseColor("#454545"),Color.parseColor("#383838"),Color.parseColor("#D0D0D0"))
    )
    fun selected(settings:SharedPreferences)=all.firstOrNull { it.id==settings.getString("ui_palette","classic") } ?: all.first()
}
