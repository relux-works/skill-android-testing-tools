# Shared Test Identifiers

## Overview

Sharing test tag constants between app and test targets ensures:
- No typos in test tag strings
- IDE autocomplete support
- Compile-time verification
- Single source of truth

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── com/example/
│   │           ├── ui/
│   │           │   └── LoginScreen.kt
│   │           └── testenv/        # Shared identifiers
│   │               └── TestTags.kt
│   └── androidTest/
│       └── kotlin/
│           └── com/example/
│               └── tests/
│                   └── LoginTest.kt
```

## Implementation

### TestTags.kt (in main source set)

```kotlin
package com.example.testenv

/**
 * Shared test tag constants for UI testing.
 *
 * These constants are shared between the app and test targets
 * to ensure consistency and compile-time verification.
 *
 * Naming pattern: {Module}_{Screen}_{Element}_{Type}
 */
object TestTags {

    // ===== Auth Module =====
    object Auth {
        object Login {
            const val SCREEN = "Auth_Login_screen"
            const val TITLE = "Auth_Login_Title_text"
            const val USERNAME_INPUT = "Auth_Login_Username_input"
            const val PASSWORD_INPUT = "Auth_Login_Password_input"
            const val SUBMIT_BUTTON = "Auth_Login_Submit_button"
            const val FORGOT_PASSWORD_LINK = "Auth_Login_ForgotPassword_link"
            const val ERROR_MESSAGE = "Auth_Login_Error_text"
            const val LOADING_INDICATOR = "Auth_Login_Loading_indicator"
        }

        object Register {
            const val SCREEN = "Auth_Register_screen"
            const val EMAIL_INPUT = "Auth_Register_Email_input"
            const val PASSWORD_INPUT = "Auth_Register_Password_input"
            const val CONFIRM_PASSWORD_INPUT = "Auth_Register_ConfirmPassword_input"
            const val TERMS_CHECKBOX = "Auth_Register_Terms_checkbox"
            const val SUBMIT_BUTTON = "Auth_Register_Submit_button"
        }
    }

    // ===== Home Module =====
    object Home {
        object Feed {
            const val SCREEN = "Home_Feed_screen"
            const val POST_LIST = "Home_Feed_PostList_list"
            const val POST_CARD = "Home_Feed_Post_card"  // Use with index
            const val NEW_POST_BUTTON = "Home_Feed_NewPost_button"
            const val SEARCH_INPUT = "Home_Feed_Search_input"
            const val FILTER_BUTTON = "Home_Feed_Filter_button"
            const val EMPTY_STATE = "Home_Feed_Empty_container"
            const val LOADING_INDICATOR = "Home_Feed_Loading_indicator"

            fun postCard(index: Int) = "Home_Feed_Post_${index}_card"
            fun postCard(id: String) = "Home_Feed_Post_${id}_card"
        }
    }

    // ===== Settings Module =====
    object Settings {
        object Main {
            const val SCREEN = "Settings_Main_screen"
            const val PROFILE_BUTTON = "Settings_Main_Profile_button"
            const val NOTIFICATIONS_BUTTON = "Settings_Main_Notifications_button"
            const val PRIVACY_BUTTON = "Settings_Main_Privacy_button"
            const val LOGOUT_BUTTON = "Settings_Main_Logout_button"
        }

        object Profile {
            const val SCREEN = "Settings_Profile_screen"
            const val AVATAR_IMAGE = "Settings_Profile_Avatar_image"
            const val NAME_INPUT = "Settings_Profile_Name_input"
            const val BIO_INPUT = "Settings_Profile_Bio_input"
            const val SAVE_BUTTON = "Settings_Profile_Save_button"
        }

        object Notifications {
            const val SCREEN = "Settings_Notifications_screen"
            const val PUSH_SWITCH = "Settings_Notifications_Push_switch"
            const val EMAIL_SWITCH = "Settings_Notifications_Email_switch"
            const val SOUND_SWITCH = "Settings_Notifications_Sound_switch"
        }
    }

    // ===== Common Components =====
    object Common {
        const val LOADING_INDICATOR = "Common_Loading_indicator"
        const val ERROR_DIALOG = "Common_Error_dialog"
        const val CONFIRM_DIALOG = "Common_Confirm_dialog"
        const val SNACKBAR = "Common_Snackbar_container"
        const val BACK_BUTTON = "Common_Back_button"
    }
}
```

### Using in Composables

```kotlin
package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.testenv.TestTags

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onForgotPassword: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.testTag(TestTags.Auth.Login.SCREEN)
    ) {
        Text(
            text = "Login",
            modifier = Modifier.testTag(TestTags.Auth.Login.TITLE)
        )

        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.testTag(TestTags.Auth.Login.USERNAME_INPUT)
        )

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.testTag(TestTags.Auth.Login.PASSWORD_INPUT)
        )

        Button(
            onClick = { onLogin(username, password) },
            modifier = Modifier.testTag(TestTags.Auth.Login.SUBMIT_BUTTON)
        ) {
            Text("Login")
        }

        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.testTag(TestTags.Auth.Login.FORGOT_PASSWORD_LINK)
        ) {
            Text("Forgot Password?")
        }
    }
}
```

### Using in Tests

```kotlin
package com.example.tests

import androidx.test.uiautomator.By
import com.example.testenv.TestTags
import com.uitesttools.uitest.pageobject.PageElement

class LoginPage(override val device: UiDevice) : PageElement {

    override val readyMarker = TestTags.Auth.Login.TITLE

    val usernameField
        get() = device.findObject(By.res(TestTags.Auth.Login.USERNAME_INPUT))

    val passwordField
        get() = device.findObject(By.res(TestTags.Auth.Login.PASSWORD_INPUT))

    val loginButton
        get() = device.findObject(By.res(TestTags.Auth.Login.SUBMIT_BUTTON))

    val errorMessage
        get() = device.findObject(By.res(TestTags.Auth.Login.ERROR_MESSAGE))

    fun login(username: String, password: String): HomePage {
        usernameField?.text = username
        passwordField?.text = password
        loginButton?.click()
        return HomePage(device).waitForReady()
    }
}
```

## Alternative: Separate Module

For larger projects, create a separate module:

```
shared-test-identifiers/
├── build.gradle.kts
└── src/main/kotlin/
    └── com/example/testenv/
        └── TestTags.kt

app/
├── build.gradle.kts  # implementation(project(":shared-test-identifiers"))
```

```kotlin
// shared-test-identifiers/build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm")
}

// No Android dependencies needed - pure Kotlin constants
```

## Benefits

1. **Type Safety**: Compile-time verification of tag strings
2. **Autocomplete**: IDE support for finding tags
3. **Refactoring**: Rename support across app and tests
4. **Documentation**: Centralized list of all UI elements
5. **Consistency**: Single source of truth

## See Also

- @assets/TestEnvShared/ - Template files for TestTags
- @references/accessibility-ids.md - Naming conventions
