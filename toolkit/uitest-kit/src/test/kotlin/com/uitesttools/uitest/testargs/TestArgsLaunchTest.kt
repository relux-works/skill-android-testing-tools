package com.uitesttools.uitest.testargs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local JVM unit tests for the pure launch-side helpers in [TestArgsLaunch]. */
class TestArgsLaunchTest {

    @Test
    fun `amStartExtras emits ez for booleans and es for strings`() {
        val tokens = TestArgsLaunch.amStartExtras(
            booleans = linkedMapOf("MOCK_NETWORK" to true, "DISABLE_ANIMATIONS" to false),
            strings = linkedMapOf("START_SCREEN" to "Login"),
        )
        assertEquals(
            listOf(
                "--ez", "MOCK_NETWORK", "true",
                "--ez", "DISABLE_ANIMATIONS", "false",
                "--es", "START_SCREEN", "Login",
            ),
            tokens,
        )
    }

    @Test
    fun `amStartExtras is empty for no args`() {
        assertTrue(TestArgsLaunch.amStartExtras().isEmpty())
    }

    @Test
    fun `amStartExtras keeps each token separate so spaced values survive`() {
        val tokens = TestArgsLaunch.amStartExtras(strings = mapOf("SEED_DATASET" to "two words"))
        assertEquals(listOf("--es", "SEED_DATASET", "two words"), tokens)
    }

    @Test
    fun `forward keeps only known TestArgs keys`() {
        val instrumentationArgs = mapOf(
            "MOCK_NETWORK" to "true",
            "START_SCREEN" to "Login",
            "class" to "com.example.FooTest",   // runner arg, must be dropped
            "listener" to "x",                  // runner arg, must be dropped
        )
        val known = setOf("MOCK_NETWORK", "START_SCREEN", "USE_FAKE_CLOCK")
        val forwarded = TestArgsLaunch.forward(instrumentationArgs, known)
        assertEquals(
            mapOf("MOCK_NETWORK" to "true", "START_SCREEN" to "Login"),
            forwarded,
        )
    }

    @Test
    fun `forward output round-trips through the reader`() {
        val forwarded = TestArgsLaunch.forward(
            instrumentationArgs = mapOf("MOCK_NETWORK" to "true", "junk" to "1"),
            knownKeys = setOf("MOCK_NETWORK"),
        )
        val reader = TestArgsReader.ofMap(forwarded)
        assertTrue(reader.boolean("MOCK_NETWORK"))
        assertEquals(false, reader.isPresent("junk"))
    }
}
