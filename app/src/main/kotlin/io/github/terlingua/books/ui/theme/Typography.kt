package io.github.terlingua.books.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.em

private val Base = Typography()

/** Default Material 3 type scale with roomier line height for long-form reading. */
val TerlinguaTypography = Base.copy(
    bodyLarge = Base.bodyLarge.copy(lineHeight = 1.6.em),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 1.55.em),
)
