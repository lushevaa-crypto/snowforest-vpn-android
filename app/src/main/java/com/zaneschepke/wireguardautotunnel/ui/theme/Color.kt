package com.zaneschepke.wireguardautotunnel.ui.theme

import androidx.compose.ui.graphics.Color

val OffWhite = Color(0xFFF2F2F4)
val CoolGray = Color(0xFF8D9D9F)
val LightGrey = Color(0xFFECEDEF)
val Aqua = Color(0xFF76BEBD)
val Plantation = Color(0xFF2E3538)
val Shark = Color(0xFF21272A)
val BalticSea = Color(0xFF1C1B1F)

// Snow Forest VPN colors
val SnowForestPrimary = Color(0xFF00BCD4)       // Циан — кнопки, переключатели
val SnowForestBackground = Color(0xFF0A1628)    // Тёмно-синий фон
val SnowForestSurface = Color(0xFF081224)       // Фон карточек
val SnowForestOnPrimary = Color(0xFF0A1628)     // Текст поверх кнопок
val SnowForestAccent = Color(0xFF00E676)        // Зелёный акцент (статус, успех)

// amoled
val ElectricTeal = Color(0xFF4DD0E1)

// Status colors
val SilverTree = Color(0xFF6DB58B)
val AlertRed = Color(0xFFCF6679)
val Straw = Color(0xFFD4C483)

val Disabled = CoolGray.copy(alpha = 0.4f)

// Other colors
val ConfigHeaderColor = Color(0xFFBB86FC)
val ConfigKeyColor = Color(0xFF03DAC5)
val Heart = Color(0xFFDB61A2)

sealed class ThemeColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val onSurface: Color,
    val onBackground: Color,
    val outline: Color,
) {

    data object Light :
        ThemeColors(
            background = LightGrey.copy(alpha = 0.95f),
            surface = OffWhite,
            primary = SnowForestPrimary,
            secondary = LightGrey,
            onSurface = SnowForestBackground,
            outline = Plantation.copy(alpha = .75f),
            onBackground = SnowForestBackground,
        )

    data object Dark :
        ThemeColors(
            background = SnowForestBackground,
            surface = SnowForestSurface,
            primary = SnowForestPrimary,
            secondary = Color(0xFF0D1E35),
            onSurface = OffWhite,
            outline = CoolGray,
            onBackground = OffWhite,
        )
}
