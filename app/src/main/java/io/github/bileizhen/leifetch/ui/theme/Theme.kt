package io.github.bileizhen.leifetch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isInDarkTheme(): Boolean = LocalDarkTheme.current
