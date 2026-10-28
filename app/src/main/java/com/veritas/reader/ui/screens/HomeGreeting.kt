package com.veritas.reader.ui.screens

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
