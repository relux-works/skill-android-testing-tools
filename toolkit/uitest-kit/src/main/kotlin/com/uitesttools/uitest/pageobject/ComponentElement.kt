package com.uitesttools.uitest.pageobject

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

/**
 * Protocol for reusable UI components within pages.
 *
 * A ComponentElement represents a smaller, reusable piece of UI
 * that appears across multiple screens (e.g., navigation bar, search field, card).
 *
 * Unlike PageElement, ComponentElement:
 * - Has a parent element (the container)
 * - Doesn't navigate to other pages
 * - Can be instantiated multiple times on the same screen
 *
 * Usage:
 * ```kotlin
 * class PostCard(
 *     override val device: UiDevice,
 *     override val container: UiObject2
 * ) : ComponentElement {
 *
 *     val title: UiObject2?
 *         get() = container.findObject(By.res("Post_Card_Title_text"))
 *
 *     val likeButton: UiObject2?
 *         get() = container.findObject(By.res("Post_Card_Like_button"))
 *
 *     val likeCount: String?
 *         get() = container.findObject(By.res("Post_Card_LikeCount_text"))?.text
 *
 *     fun tapLike() {
 *         likeButton?.click()
 *     }
 * }
 *
 * // In a PageElement:
 * class FeedPage(override val device: UiDevice) : PageElement {
 *     override val readyMarker = "Feed_List_container"
 *
 *     fun getPostCard(index: Int): PostCard? {
 *         val cards = device.findObjects(By.res("Post_Card_container"))
 *         return cards.getOrNull(index)?.let { PostCard(device, it) }
 *     }
 *
 *     fun getAllPostCards(): List<PostCard> {
 *         return device.findObjects(By.res("Post_Card_container"))
 *             .map { PostCard(device, it) }
 *     }
 * }
 * ```
 */
interface ComponentElement {

    /**
     * The UiDevice instance for interacting with the UI.
     */
    val device: UiDevice

    /**
     * The container element that bounds this component.
     */
    val container: UiObject2

    /**
     * Check if this component is currently visible.
     */
    fun isVisible(): Boolean {
        return try {
            container.isEnabled // Will throw if element is gone
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Find an element within this component by test tag.
     */
    fun findByTag(tag: String): UiObject2? {
        return container.findObject(By.res(tag))
    }

    /**
     * Find an element within this component by text content.
     */
    fun findByText(text: String): UiObject2? {
        return container.findObject(By.text(text))
    }

    /**
     * Find an element within this component by content description.
     */
    fun findByDescription(description: String): UiObject2? {
        return container.findObject(By.desc(description))
    }
}

/**
 * Factory for creating component elements from a list container.
 *
 * Useful for lists/grids of repeated components.
 *
 * Usage:
 * ```kotlin
 * val cardFactory = ComponentFactory<PostCard>(device) { container ->
 *     PostCard(device, container)
 * }
 *
 * val cards = cardFactory.findAll("Post_Card_container")
 * val firstCard = cardFactory.findFirst("Post_Card_container")
 * ```
 */
class ComponentFactory<T : ComponentElement>(
    private val device: UiDevice,
    private val factory: (UiObject2) -> T
) {
    /**
     * Find all components matching the given test tag.
     */
    fun findAll(containerTag: String): List<T> {
        return device.findObjects(By.res(containerTag)).map(factory)
    }

    /**
     * Find the first component matching the given test tag.
     */
    fun findFirst(containerTag: String): T? {
        return device.findObject(By.res(containerTag))?.let(factory)
    }

    /**
     * Find a specific component by index.
     */
    fun findAt(containerTag: String, index: Int): T? {
        return device.findObjects(By.res(containerTag)).getOrNull(index)?.let(factory)
    }

    /**
     * Count components matching the given test tag.
     */
    fun count(containerTag: String): Int {
        return device.findObjects(By.res(containerTag)).size
    }
}
