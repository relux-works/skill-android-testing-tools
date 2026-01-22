package com.uitesttools.uitest.extensions

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput

/**
 * Extension functions for Compose UI Test.
 *
 * Provides convenience methods for testing Compose UI.
 */

/**
 * Wait for a node with test tag to exist.
 *
 * @param tag The test tag
 * @param timeout Maximum time to wait in milliseconds
 * @return The node interaction
 */
fun ComposeTestRule.waitForTag(
    tag: String,
    timeout: Long = 5000
): SemanticsNodeInteraction {
    waitUntil(timeout) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    return onNodeWithTag(tag)
}

/**
 * Wait for a node with text to exist.
 */
fun ComposeTestRule.waitForText(
    text: String,
    timeout: Long = 5000
): SemanticsNodeInteraction {
    waitUntil(timeout) {
        try {
            onNodeWithText(text).assertExists()
            true
        } catch (e: AssertionError) {
            false
        }
    }
    return onNodeWithText(text)
}

/**
 * Wait for a node to disappear.
 */
fun ComposeTestRule.waitUntilTagGone(
    tag: String,
    timeout: Long = 5000
) {
    waitUntil(timeout) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }
}

/**
 * Check if a node with tag exists.
 */
fun ComposeTestRule.hasNodeWithTag(tag: String): Boolean {
    return onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}

/**
 * Click at a specific offset within a node.
 *
 * Useful for clicking specific parts of a composable (like a Switch toggle).
 *
 * @param percentX Horizontal offset (0.0 = left, 0.5 = center, 1.0 = right)
 * @param percentY Vertical offset (0.0 = top, 0.5 = center, 1.0 = bottom)
 */
fun SemanticsNodeInteraction.clickAtOffset(
    percentX: Float,
    percentY: Float
): SemanticsNodeInteraction {
    return performTouchInput {
        click(
            position = androidx.compose.ui.geometry.Offset(
                x = width * percentX,
                y = height * percentY
            )
        )
    }
}

/**
 * Long click at a specific offset.
 */
fun SemanticsNodeInteraction.longClickAtOffset(
    percentX: Float,
    percentY: Float
): SemanticsNodeInteraction {
    return performTouchInput {
        longClick(
            position = androidx.compose.ui.geometry.Offset(
                x = width * percentX,
                y = height * percentY
            )
        )
    }
}

/**
 * Clear text field and enter new text.
 */
fun SemanticsNodeInteraction.replaceText(text: String): SemanticsNodeInteraction {
    performTextClearance()
    performTextInput(text)
    return this
}

/**
 * Scroll to this node and then click.
 */
fun SemanticsNodeInteraction.scrollToAndClick(): SemanticsNodeInteraction {
    return performScrollTo().performClick()
}

/**
 * Assert that the node is visible and enabled.
 */
fun SemanticsNodeInteraction.assertIsReady(): SemanticsNodeInteraction {
    return assertIsDisplayed().assertIsEnabled()
}

/**
 * Assert that the node is hidden (not displayed).
 */
fun SemanticsNodeInteraction.assertIsHidden(): SemanticsNodeInteraction {
    return assertIsNotDisplayed()
}

/**
 * Assert that the node is visible but disabled.
 */
fun SemanticsNodeInteraction.assertIsDisabled(): SemanticsNodeInteraction {
    return assertIsDisplayed().assertIsNotEnabled()
}

/**
 * Get the count of nodes matching a tag.
 */
fun SemanticsNodeInteractionCollection.count(): Int {
    return fetchSemanticsNodes().size
}

/**
 * Check if the collection is empty.
 */
fun SemanticsNodeInteractionCollection.isEmpty(): Boolean {
    return fetchSemanticsNodes().isEmpty()
}

/**
 * Check if the collection is not empty.
 */
fun SemanticsNodeInteractionCollection.isNotEmpty(): Boolean {
    return fetchSemanticsNodes().isNotEmpty()
}
