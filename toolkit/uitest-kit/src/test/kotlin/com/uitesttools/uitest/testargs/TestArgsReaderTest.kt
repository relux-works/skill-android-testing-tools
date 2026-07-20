package com.uitesttools.uitest.testargs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local JVM unit tests for the pure, channel-agnostic [TestArgsReader]. These
 * drive the reader through an in-memory map, standing in for the normalized
 * string form every real channel (intent extras, forwarded instrumentation
 * args) collapses to.
 */
class TestArgsReaderTest {

    private fun reader(vararg pairs: Pair<String, String?>) =
        TestArgsReader.ofMap(mapOf(*pairs))

    @Test
    fun `isPresent reflects supplied keys`() {
        val r = reader("A" to "x", "B" to null)
        assertTrue(r.isPresent("A"))
        // A key mapped to null is treated as absent (no value supplied).
        assertFalse(r.isPresent("B"))
        assertFalse(r.isPresent("MISSING"))
    }

    @Test
    fun `boolean returns default when absent`() {
        val r = reader()
        assertFalse(r.boolean("FLAG"))
        assertTrue(r.boolean("FLAG", default = true))
    }

    @Test
    fun `boolean parses true tokens case-insensitively`() {
        listOf("true", "TRUE", "True", "1", "yes", "YES", "on", "  on  ").forEach {
            assertTrue("expected true for '$it'", reader("FLAG" to it).boolean("FLAG"))
        }
    }

    @Test
    fun `boolean treats false tokens and junk as false when present`() {
        listOf("false", "FALSE", "0", "no", "off", "", "banana").forEach {
            assertFalse("expected false for '$it'", reader("FLAG" to it).boolean("FLAG", default = true))
        }
    }

    @Test
    fun `true and false token sets are disjoint`() {
        assertTrue(TestArgsReader.TRUE_TOKENS.intersect(TestArgsReader.FALSE_TOKENS).isEmpty())
    }

    @Test
    fun `string returns value or default`() {
        val r = reader("S" to "hello", "EMPTY" to "")
        assertEquals("hello", r.string("S"))
        assertEquals("", r.string("EMPTY"))               // explicit empty is preserved
        assertNull(r.string("MISSING"))
        assertEquals("fallback", r.string("MISSING", default = "fallback"))
    }

    @Test
    fun `int parses or falls back to default`() {
        val r = reader("N" to "42", "NEG" to "-3", "PAD" to "  7 ", "BAD" to "12x")
        assertEquals(42, r.int("N", default = 0))
        assertEquals(-3, r.int("NEG", default = 0))
        assertEquals(7, r.int("PAD", default = 0))
        assertEquals(99, r.int("BAD", default = 99))      // unparseable -> default
        assertEquals(99, r.int("MISSING", default = 99))  // absent -> default
    }

    @Test
    fun `custom source is honored`() {
        // Simulates an intent-extra style backing where only some keys exist.
        val backing = mapOf("MOCK_NETWORK" to "true")
        val r = TestArgsReader { backing[it] }
        assertTrue(r.boolean("MOCK_NETWORK"))
        assertFalse(r.isPresent("USE_FAKE_CLOCK"))
    }
}
