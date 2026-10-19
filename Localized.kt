package com.novelreader.app.ui

import java.text.NumberFormat
import java.util.Locale

/** Locale-aware number formatting (Persian digits when the UI language is Persian). */
object Localized {
    fun number(n: Int): String = NumberFormat.getInstance(Locale.getDefault()).format(n)

    fun percent(n: Int): String = NumberFormat.getPercentInstance(Locale.getDefault()).format(n / 100.0)

    fun decimal(v: Double, digits: Int = 1): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(v)
}
