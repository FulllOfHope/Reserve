package com.app.reserve.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.app.reserve.R

/** Inter is bundled, so the app never downloads a font (and never needs network). */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    // Only Regular, Medium and Semibold are bundled; heavier weights resolve
    // to Semibold instead of downloading a font at runtime.
    Font(R.font.inter_semibold, FontWeight.Bold),
    Font(R.font.inter_semibold, FontWeight.ExtraBold)
)

// Define Ente typography scale explicitly
val H1_Nunito = TextStyle(
    fontFamily = InterFamily, 
    fontWeight = FontWeight.ExtraBold,
    fontSize = 24.sp,
    lineHeight = 28.sp
)

val H1_Bold = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 28.sp
)

val H2 = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp
)

val LargeText = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 20.sp
)

val BodyText = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

val MiniText = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

val TinyText = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 12.sp
)

// Map to Material Typography for compatibility, but we should use the exact styles when building UI.
val ReserveTypography = Typography(
    displayLarge = H1_Nunito,
    headlineMedium = H1_Bold,
    headlineLarge = H1_Bold,
    titleLarge = H2,
    headlineSmall = H2,
    titleMedium = LargeText,
    bodyLarge = LargeText.copy(fontWeight = FontWeight.Medium),
    bodyMedium = BodyText,
    labelLarge = LargeText,
    labelMedium = MiniText,
    labelSmall = TinyText
)

/** Every money value uses tabular figures so digits never shift. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
