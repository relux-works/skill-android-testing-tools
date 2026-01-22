# Page Object Pattern for Android

## Overview

The Page Object pattern encapsulates UI structure and interactions, providing:
- Readable, maintainable tests
- Reusable page interactions
- Separation of test logic from UI details
- Easy updates when UI changes

## Core Interfaces

### PageElement

Represents a screen/page in the app:

```kotlin
interface PageElement {
    val device: UiDevice
    val readyMarker: String  // Tag that indicates page is ready
    val defaultTimeout: Long
        get() = 5000L

    fun <T : PageElement> T.waitForReady(timeout: Long = defaultTimeout): T
    fun isDisplayed(): Boolean
    fun testTag(tag: String): String
}
```

### ComponentElement

Represents reusable UI components (cards, list items):

```kotlin
interface ComponentElement {
    val device: UiDevice
    val container: UiObject2

    fun isVisible(): Boolean
    fun findByTag(tag: String): UiObject2?
    fun findByText(text: String): UiObject2?
}
```

## Page Object Structure

```kotlin
package com.example.tests.pages

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.example.testenv.TestTags
import com.uitesttools.uitest.pageobject.PageElement

class LoginPage(override val device: UiDevice) : PageElement {

    // Ready marker - element that indicates page is loaded
    override val readyMarker = TestTags.Auth.Login.TITLE

    // ----- Elements -----

    val titleText: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.TITLE))

    val usernameField: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.USERNAME_INPUT))

    val passwordField: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.PASSWORD_INPUT))

    val loginButton: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.SUBMIT_BUTTON))

    val forgotPasswordLink: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.FORGOT_PASSWORD_LINK))

    val errorMessage: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.ERROR_MESSAGE))

    val loadingIndicator: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.LOADING_INDICATOR))

    // ----- Actions -----

    /**
     * Enter username.
     */
    fun enterUsername(username: String): LoginPage {
        usernameField?.text = username
        return this
    }

    /**
     * Enter password.
     */
    fun enterPassword(password: String): LoginPage {
        passwordField?.text = password
        return this
    }

    /**
     * Tap login button.
     */
    fun tapLogin(): LoginPage {
        loginButton?.click()
        return this
    }

    /**
     * Complete login flow, returning HomePage on success.
     */
    fun login(username: String, password: String): HomePage {
        enterUsername(username)
        enterPassword(password)
        tapLogin()

        // Wait for navigation
        return HomePage(device).waitForReady()
    }

    /**
     * Attempt login expecting an error.
     */
    fun loginExpectingError(username: String, password: String): LoginPage {
        enterUsername(username)
        enterPassword(password)
        tapLogin()

        // Wait for error to appear
        device.wait(
            androidx.test.uiautomator.Until.hasObject(
                By.res(TestTags.Auth.Login.ERROR_MESSAGE)
            ),
            defaultTimeout
        )

        return this
    }

    /**
     * Navigate to forgot password screen.
     */
    fun tapForgotPassword(): ForgotPasswordPage {
        forgotPasswordLink?.click()
        return ForgotPasswordPage(device).waitForReady()
    }

    // ----- Assertions -----

    /**
     * Assert error message is displayed with specific text.
     */
    fun assertErrorMessage(expectedText: String): LoginPage {
        val error = errorMessage
        require(error != null) { "Error message not displayed" }
        require(error.text == expectedText) {
            "Expected error '$expectedText' but got '${error.text}'"
        }
        return this
    }

    /**
     * Assert loading indicator is visible.
     */
    fun assertIsLoading(): LoginPage {
        require(loadingIndicator != null) { "Loading indicator not displayed" }
        return this
    }
}
```

## Component Object Example

```kotlin
package com.example.tests.components

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.uitesttools.uitest.pageobject.ComponentElement

/**
 * Represents a post card in the feed.
 */
class PostCard(
    override val device: UiDevice,
    override val container: UiObject2
) : ComponentElement {

    val title: String?
        get() = findByTag("Post_Card_Title_text")?.text

    val content: String?
        get() = findByTag("Post_Card_Content_text")?.text

    val authorName: String?
        get() = findByTag("Post_Card_Author_text")?.text

    val likeButton: UiObject2?
        get() = findByTag("Post_Card_Like_button")

    val likeCount: Int
        get() = findByTag("Post_Card_LikeCount_text")?.text?.toIntOrNull() ?: 0

    val shareButton: UiObject2?
        get() = findByTag("Post_Card_Share_button")

    fun tapLike(): PostCard {
        likeButton?.click()
        return this
    }

    fun tapShare(): PostCard {
        shareButton?.click()
        return this
    }

    fun tap(): PostDetailPage {
        container.click()
        return PostDetailPage(device).waitForReady()
    }
}
```

## Using Components in Pages

```kotlin
class FeedPage(override val device: UiDevice) : PageElement {

    override val readyMarker = TestTags.Home.Feed.SCREEN

    val postList: List<PostCard>
        get() = device.findObjects(By.res(TestTags.Home.Feed.POST_CARD))
            .map { PostCard(device, it) }

    fun getPostAt(index: Int): PostCard? {
        val containers = device.findObjects(By.res(TestTags.Home.Feed.POST_CARD))
        return containers.getOrNull(index)?.let { PostCard(device, it) }
    }

    fun getPostById(id: String): PostCard? {
        val container = device.findObject(By.res(TestTags.Home.Feed.postCard(id)))
        return container?.let { PostCard(device, it) }
    }

    fun scrollToPostAt(index: Int): PostCard? {
        // Scroll until post is visible
        repeat(10) {
            getPostAt(index)?.let { return it }
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                20
            )
            device.waitForIdle(500)
        }
        return getPostAt(index)
    }
}
```

## Base Test Suite

```kotlin
package com.example.tests

import com.uitesttools.uitest.pageobject.BaseUiTestSuite
import org.junit.Test

class LoginFlowTest : BaseUiTestSuite() {

    override val packageName = "com.example.app"

    @Test
    fun testSuccessfulLogin() {
        launchApp()
        screenshot(1, "app_launched")

        val loginPage = LoginPage(device).waitForReady()
        screenshot(2, "login_page_ready")

        val homePage = loginPage.login("user@test.com", "password123")
        screenshot(3, "logged_in")

        assertPageDisplayed(homePage)
    }

    @Test
    fun testInvalidCredentials() {
        launchApp()

        val loginPage = LoginPage(device).waitForReady()
        loginPage.loginExpectingError("wrong@test.com", "wrongpassword")
            .assertErrorMessage("Invalid credentials")

        screenshot(1, "error_displayed")
    }

    @Test
    fun testFeedNavigation() {
        launchApp()

        val homePage = LoginPage(device)
            .waitForReady()
            .login("user@test.com", "password123")

        val feedPage = homePage.tapFeed().waitForReady()
        screenshot(1, "feed_loaded")

        // Interact with first post
        val firstPost = feedPage.getPostAt(0)
        require(firstPost != null) { "No posts in feed" }

        firstPost.tapLike()
        screenshot(2, "post_liked")

        val detailPage = firstPost.tap()
        screenshot(3, "post_detail")

        assertPageDisplayed(detailPage)
    }
}
```

## Best Practices

### 1. Keep Selectors Private

```kotlin
// Good - encapsulate selectors
class LoginPage(override val device: UiDevice) : PageElement {
    val usernameField: UiObject2?
        get() = device.findObject(By.res(TestTags.Auth.Login.USERNAME_INPUT))

    fun enterUsername(username: String) {
        usernameField?.text = username
    }
}

// Bad - expose raw selectors in tests
device.findObject(By.res("Auth_Login_Username_input"))?.text = "user"
```

### 2. Return Page Objects from Navigation

```kotlin
// Good - chain navigation
fun login(username: String, password: String): HomePage {
    // ... perform login
    return HomePage(device).waitForReady()
}

// Usage
loginPage.login("user", "pass").tapSettings().tapProfile()
```

### 3. Use waitForReady()

```kotlin
// Good - always wait for page readiness
val loginPage = LoginPage(device).waitForReady()

// Bad - might fail if page not loaded
val loginPage = LoginPage(device)
loginPage.usernameField?.text = "user"  // might fail
```

### 4. Descriptive Action Names

```kotlin
// Good
fun tapLoginButton(): LoginPage
fun submitCredentials(): HomePage
fun enterUsername(text: String): LoginPage

// Bad
fun click1(): LoginPage
fun doIt(): HomePage
fun type(text: String): LoginPage
```

## See Also

- @assets/UIStruct/ - Template files
- @references/shared-identifiers.md - Shared test tags
