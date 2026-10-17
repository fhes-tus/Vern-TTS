package com.veritas.reader.ui.screens

/** Stable for an hour; recomposition never randomly changes the greeting. */
internal fun homeGreeting(name: String, hour: Int, welcomeBack: Boolean = false): String {
    val greeting = when {
        welcomeBack -> "Welcome back"
        hour in 0..3 -> "Night owl"
        hour in 4..5 -> "Dawn owl"
        hour == 6 -> "Early bird"
        hour in 7..11 -> "Good morning"
        hour in 12..16 -> "Good afternoon"
        hour in 17..20 -> "Good evening"
        else -> "Good night"
    }
    val displayName = name.trim().lineSequence().firstOrNull().orEmpty()
    return if (displayName.isBlank()) greeting else "$greeting, $displayName"
}
