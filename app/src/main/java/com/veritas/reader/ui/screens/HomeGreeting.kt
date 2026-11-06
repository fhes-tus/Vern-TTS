package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import com.veritas.reader.R

@Composable
internal fun localizedHomeGreeting(name: String, hour: Int, welcomeBack: Boolean): String {
    val greeting = menuString(when {
        welcomeBack -> R.string.greeting_welcome
        hour in 0..3 -> R.string.greeting_see_you
        hour in 4..5 -> R.string.greeting_new_day
        hour == 6 -> R.string.greeting_rise
        hour in 7..11 -> R.string.greeting_morning
        hour in 12..16 -> R.string.greeting_afternoon
        hour in 17..20 -> R.string.greeting_evening
        else -> R.string.greeting_night
    })
    val displayName = name.trim().lineSequence().firstOrNull().orEmpty()
    return if (displayName.isBlank()) greeting else menuString(R.string.greeting_named, greeting, displayName)
}

/** Stable for an hour; recomposition never randomly changes the greeting. */
internal fun homeGreeting(name: String, hour: Int, welcomeBack: Boolean = false): String {
    val greeting = when {
        welcomeBack -> "Welcome back"
        hour in 0..3 -> "Good to see you"
        hour in 4..5 -> "Welcome to a new day"
        hour == 6 -> "Rise and shine"
        hour in 7..11 -> "Good morning"
        hour in 12..16 -> "Good afternoon"
        hour in 17..20 -> "Good evening"
        else -> "Good night"
    }
    val displayName = name.trim().lineSequence().firstOrNull().orEmpty()
    return if (displayName.isBlank()) greeting else "$greeting, $displayName"
}
