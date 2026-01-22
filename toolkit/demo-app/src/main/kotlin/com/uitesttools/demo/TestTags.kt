package com.uitesttools.demo

/**
 * Shared test tags for UI testing.
 * Pattern: {Module}_{Screen}_{Element}_{Type}
 */
object TestTags {
    object Main {
        const val SCREEN = "Main_Home_Screen_container"
        const val TITLE = "Main_Home_Title_text"
        const val COUNTER = "Main_Home_Counter_text"
        const val INCREMENT_BUTTON = "Main_Home_Increment_button"
        const val DECREMENT_BUTTON = "Main_Home_Decrement_button"
        const val RESET_BUTTON = "Main_Home_Reset_button"
    }
}
