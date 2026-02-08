package io.github.c1921.namingdict.ui

import androidx.compose.ui.text.font.FontFamily

internal val HanziFontFamily = FontFamily.Serif

internal fun formatPinyinList(values: List<String>): String {
    if (values.isEmpty()) {
        return "-"
    }
    return values.joinToString(", ")
}

internal fun formatIntList(values: List<Int>): String {
    if (values.isEmpty()) {
        return "-"
    }
    return values.joinToString(" ")
}
