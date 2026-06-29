package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Modern Premium Enterprise 2026 Palette
val DeepTeal = Color(0xFF00695C)        // Primary Deep Teal
val Emerald = Color(0xFF00A884)         // Secondary Emerald
val AmberAccent = Color(0xFFF4B400)     // Accent Amber
val DesignBackground = Color(0xFFF8FAFB) // Premium light grey-blue background
val DesignSurface = Color(0xFFFFFFFF)    // Pure White Surface

val DesignTextPrimary = Color(0xFF1F2937)   // Slate 800 (Text Primary)
val DesignTextSecondary = Color(0xFF6B7280) // Slate 500 (Text Secondary)

val DesignSuccess = Color(0xFF16A34A)
val DesignWarning = Color(0xFFF59E0B)
val DesignError = Color(0xFFDC2626)

// Dark Theme Variants
val DeepTealDark = Color(0xFF4DB6AC)
val EmeraldDark = Color(0xFF4EF0C9)
val AmberAccentDark = Color(0xFFFFD54F)
val BackgroundDark = Color(0xFF0B111E)
val SurfaceDark = Color(0xFF162032)

// Map existing variables to keep backward compatibility and maintain layout consistency
val MutedTealGreen = DeepTeal
val MutedTealLight = Color(0xFFE0F2F1)
val NaturalAmber = AmberAccent
val LightAmberBg = Color(0xFFFFF8E1)
val SoftTealBackground = DesignBackground
val SoftWhiteSurface = DesignSurface
val TextPrimary = DesignTextPrimary
val TextSecondary = DesignTextSecondary
val BorderColor = Color(0xFFE5E7EB)

val PrimaryBlue = DeepTeal
val PrimaryBlueDark = DeepTealDark
val SecondaryAmber = AmberAccent
val SecondaryAmberDark = AmberAccentDark
val TertiaryEmerald = Emerald
val BackgroundLight = SoftTealBackground
val SurfaceLight = SoftWhiteSurface

val StatusDone = DesignSuccess
val StatusPending = DesignWarning
val StatusDelay = DesignError
