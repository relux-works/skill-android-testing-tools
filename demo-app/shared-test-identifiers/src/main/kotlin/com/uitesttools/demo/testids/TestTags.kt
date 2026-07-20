package com.uitesttools.demo.testids

/**
 * Shared UI test tags — the BEM single source of truth.
 *
 * Naming pattern: {Module}_{Screen}_{Element}_{Type}  (underscores, resource-id friendly).
 * Applied on UI via `Modifier.testTag(...)` / `android:tag`, queried in tests via
 * `onNodeWithTag(...)` / `By.res(...)` / `withTagValue(...)`.
 *
 * Compiled into both the app (`implementation`) and the androidTest source set
 * (inherited transitively) so there is exactly one definition of every tag string.
 *
 * Android gotcha: UIAutomator (`By.res(tag)`) cannot see a Compose `testTag` unless
 * the Compose root sets `Modifier.semantics { testTagsAsResourceId = true }`.
 */
object TestTags {

    // ===== Main Module (demo) =====
    object Main {
        const val SCREEN = "Main_Home_Screen_container"
        const val TITLE = "Main_Home_Title_text"
        const val COUNTER = "Main_Home_Counter_text"
        const val INCREMENT_BUTTON = "Main_Home_Increment_button"
        const val DECREMENT_BUTTON = "Main_Home_Decrement_button"
        const val RESET_BUTTON = "Main_Home_Reset_button"
    }

    // ===== Index helpers stay idiomatic (list / repeated elements) =====
    /** Tag for the Nth item in a repeated list, e.g. `Main_Home_Item_3_card`. */
    fun homeItemCard(index: Int) = "Main_Home_Item_${index}_card"
}
