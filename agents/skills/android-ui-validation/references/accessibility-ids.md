# Test Tag Naming Conventions

## BEM-like Pattern for Android

Test tags (Compose `testTag`, XML `contentDescription`) follow a BEM-like naming pattern:

```
{Module}_{Screen}_{Element}_{Type}
```

### Components

| Component | Description | Examples |
|-----------|-------------|----------|
| Module | Feature/domain area | `Auth`, `Home`, `Settings`, `Profile` |
| Screen | Specific screen | `Login`, `Feed`, `Details`, `Edit` |
| Element | UI element name | `Username`, `Submit`, `Avatar`, `Title` |
| Type | Element type | `button`, `input`, `text`, `image`, `card`, `list` |

### Examples

```kotlin
// Authentication module
"Auth_Login_Username_input"
"Auth_Login_Password_input"
"Auth_Login_Submit_button"
"Auth_Login_ForgotPassword_link"
"Auth_Register_Email_input"
"Auth_Register_Terms_checkbox"

// Home module
"Home_Feed_Post_card"
"Home_Feed_NewPost_button"
"Home_Feed_Search_input"
"Home_Feed_Filter_button"

// Settings module
"Settings_Profile_Avatar_image"
"Settings_Profile_Name_input"
"Settings_Notifications_Push_switch"
"Settings_Privacy_Location_switch"

// Detail screens
"Post_Detail_Title_text"
"Post_Detail_Content_text"
"Post_Detail_Like_button"
"Post_Detail_Share_button"
```

### Type Suffixes

| Suffix | Usage |
|--------|-------|
| `_button` | Clickable buttons |
| `_input` | Text input fields |
| `_text` | Text labels/displays |
| `_image` | Images |
| `_icon` | Icon buttons |
| `_link` | Text links |
| `_switch` | Toggle switches |
| `_checkbox` | Checkboxes |
| `_radio` | Radio buttons |
| `_card` | Card containers |
| `_list` | List/RecyclerView |
| `_item` | List items |
| `_container` | Generic containers |
| `_dialog` | Dialogs |
| `_snackbar` | Snackbar messages |

## Implementation

### Compose

```kotlin
@Composable
fun LoginScreen() {
    Column {
        TextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.testTag("Auth_Login_Username_input")
        )

        TextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.testTag("Auth_Login_Password_input"),
            visualTransformation = PasswordVisualTransformation()
        )

        Button(
            onClick = onLogin,
            modifier = Modifier.testTag("Auth_Login_Submit_button")
        ) {
            Text("Login")
        }
    }
}
```

### XML Layouts

For XML layouts, use `contentDescription` or custom `testTag` attribute:

```xml
<EditText
    android:id="@+id/usernameInput"
    android:contentDescription="@string/test_auth_login_username_input"
    ... />

<Button
    android:id="@+id/loginButton"
    android:contentDescription="@string/test_auth_login_submit_button"
    ... />
```

With string resources:
```xml
<!-- strings.xml -->
<string name="test_auth_login_username_input">Auth_Login_Username_input</string>
<string name="test_auth_login_submit_button">Auth_Login_Submit_button</string>
```

## Best Practices

1. **Consistency**: Use the same naming across the entire app
2. **Clarity**: Names should clearly identify the element
3. **Uniqueness**: Each element should have a unique tag
4. **Avoid Hardcoding**: Use constants (see shared-identifiers.md)
5. **Test Coverage**: Tag all interactive elements

## Finding Elements in Tests

### UIAutomator

```kotlin
// By resource ID (contentDescription)
device.findObject(By.desc("Auth_Login_Username_input"))

// By test tag (Compose)
device.findObject(By.res("Auth_Login_Username_input"))
```

### Compose UI Test

```kotlin
composeTestRule.onNodeWithTag("Auth_Login_Username_input")
```

### Espresso

```kotlin
onView(withContentDescription("Auth_Login_Username_input"))
```

## Dynamic Elements

For lists and dynamic content:

```kotlin
// List items with index
"Home_Feed_Post_0_card"
"Home_Feed_Post_1_card"

// Or use data-driven tags
"Home_Feed_Post_${postId}_card"
```

In Compose:
```kotlin
LazyColumn {
    itemsIndexed(posts) { index, post ->
        PostCard(
            post = post,
            modifier = Modifier.testTag("Home_Feed_Post_${index}_card")
        )
    }
}
```
