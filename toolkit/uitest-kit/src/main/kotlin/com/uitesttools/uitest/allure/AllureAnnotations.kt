package com.uitesttools.uitest.allure

/**
 * Allure integration for Android UI tests.
 *
 * This module provides interfaces and utilities for annotating tests
 * with Allure metadata for better test reporting.
 *
 * Note: Allure dependencies are compileOnly - they must be added to
 * the consuming project if Allure reporting is desired.
 */

/**
 * Interface for test classes that support Allure tracking.
 *
 * Implement this interface to enable automatic Allure metadata
 * for your test classes.
 *
 * Usage:
 * ```kotlin
 * @Epic("Authentication")
 * @Feature("Login")
 * class LoginTest : BaseUiTestSuite(), AllureTrackable {
 *
 *     override val epic = "Authentication"
 *     override val feature = "Login"
 *
 *     @Test
 *     @Story("Successful login")
 *     @Severity(SeverityLevel.CRITICAL)
 *     fun testSuccessfulLogin() {
 *         // ...
 *     }
 * }
 * ```
 */
interface AllureTrackable {

    /**
     * The epic this test belongs to.
     * Epics are the highest level of test organization.
     */
    val epic: String

    /**
     * The feature being tested.
     * Features group related functionality.
     */
    val feature: String

    /**
     * Optional owner/maintainer of this test.
     */
    val owner: String?
        get() = null

    /**
     * Optional tags for filtering tests.
     */
    val tags: List<String>
        get() = emptyList()
}

/**
 * Annotation aliases for convenience.
 *
 * These match the Allure Kotlin annotations but can be used
 * even without the Allure dependency (annotations will be ignored
 * at runtime if Allure is not present).
 */

/**
 * Mark a test method with a specific severity level.
 */
enum class TestSeverity {
    BLOCKER,
    CRITICAL,
    NORMAL,
    MINOR,
    TRIVIAL
}

/**
 * Test priority for execution ordering.
 */
enum class TestPriority {
    P0_SMOKE,
    P1_CRITICAL,
    P2_EXTENDED,
    P3_OPTIONAL
}

/**
 * Test environment tags.
 */
object TestTags {
    const val SMOKE = "smoke"
    const val REGRESSION = "regression"
    const val INTEGRATION = "integration"
    const val E2E = "e2e"
    const val FLAKY = "flaky"
    const val WIP = "wip"
}

/**
 * Step annotation for marking test steps.
 *
 * When Allure is available, these will be tracked as test steps
 * in the Allure report.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Step(val description: String)

/**
 * Manual step tracking for dynamic step descriptions.
 *
 * Usage (when Allure is available):
 * ```kotlin
 * AllureSteps.step("Entering username: $username") {
 *     usernameField.text = username
 * }
 * ```
 */
object AllureSteps {

    /**
     * Execute a block as an Allure step.
     *
     * Falls back to just executing the block if Allure is not available.
     */
    fun <T> step(description: String, block: () -> T): T {
        return try {
            // Try to use Allure step if available
            val allureClass = Class.forName("io.qameta.allure.kotlin.Allure")
            val stepMethod = allureClass.getMethod(
                "step",
                String::class.java,
                Function0::class.java
            )
            @Suppress("UNCHECKED_CAST")
            stepMethod.invoke(null, description, block) as T
        } catch (e: ClassNotFoundException) {
            // Allure not available, just run the block
            block()
        } catch (e: Exception) {
            // Some other error, still run the block
            block()
        }
    }

    /**
     * Add an attachment to the current test.
     */
    fun attachment(name: String, content: ByteArray, type: String = "image/png") {
        try {
            val allureClass = Class.forName("io.qameta.allure.kotlin.Allure")
            val attachMethod = allureClass.getMethod(
                "attachment",
                String::class.java,
                ByteArray::class.java,
                String::class.java
            )
            attachMethod.invoke(null, name, content, type)
        } catch (e: Exception) {
            // Allure not available, ignore
        }
    }

    /**
     * Add a text attachment.
     */
    fun textAttachment(name: String, content: String) {
        attachment(name, content.toByteArray(), "text/plain")
    }

    /**
     * Add a link to the test report.
     */
    fun link(name: String, url: String) {
        try {
            val allureClass = Class.forName("io.qameta.allure.kotlin.Allure")
            val linkMethod = allureClass.getMethod(
                "link",
                String::class.java,
                String::class.java
            )
            linkMethod.invoke(null, name, url)
        } catch (e: Exception) {
            // Allure not available, ignore
        }
    }

    /**
     * Add an issue link.
     */
    fun issue(name: String, url: String) {
        link("Issue: $name", url)
    }

    /**
     * Add a test case management link.
     */
    fun tmsLink(id: String, url: String) {
        link("TMS: $id", url)
    }

    /**
     * Set a parameter value visible in the report.
     */
    fun parameter(name: String, value: Any) {
        try {
            val allureClass = Class.forName("io.qameta.allure.kotlin.Allure")
            val paramMethod = allureClass.getMethod(
                "parameter",
                String::class.java,
                Any::class.java
            )
            paramMethod.invoke(null, name, value)
        } catch (e: Exception) {
            // Allure not available, ignore
        }
    }
}

/**
 * Data class for test metadata.
 *
 * Can be used to programmatically define test metadata.
 */
data class TestMetadata(
    val epic: String,
    val feature: String,
    val story: String? = null,
    val severity: TestSeverity = TestSeverity.NORMAL,
    val priority: TestPriority = TestPriority.P2_EXTENDED,
    val owner: String? = null,
    val tags: List<String> = emptyList(),
    val links: Map<String, String> = emptyMap()
) {
    /**
     * Apply this metadata to the current Allure context.
     */
    fun apply() {
        AllureSteps.parameter("epic", epic)
        AllureSteps.parameter("feature", feature)
        story?.let { AllureSteps.parameter("story", it) }
        AllureSteps.parameter("severity", severity.name)
        AllureSteps.parameter("priority", priority.name)
        owner?.let { AllureSteps.parameter("owner", it) }
        tags.forEach { AllureSteps.parameter("tag", it) }
        links.forEach { (name, url) -> AllureSteps.link(name, url) }
    }
}
