/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.events

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarnetFeaturesTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun testMoneroAddressRetrieval() {
        val metadata = UserMetadata()
        metadata.cryptoAddresses = mapOf("monero" to "44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXz9ucDeZaYo7kn7sZyj55xXLTxtr5W3e3v6kK8e3S9qV8R8")

        assertEquals("44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXz9ucDeZaYo7kn7sZyj55xXLTxtr5W3e3v6kK8e3S9qV8R8", metadata.moneroAddress())
    }

    @Test
    fun testMoneroAddressNull() {
        val metadata = UserMetadata()
        assertNull(metadata.moneroAddress())

        metadata.cryptoAddresses = mapOf("bitcoin" to "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        assertNull(metadata.moneroAddress())
    }

    @Test
    fun testSimplexFieldProcessing() {
        val metadata = UserMetadata()
        metadata.simplex = " simplex:/smp/server/id "

        // Before cleaning, it should have the spaces
        assertEquals(" simplex:/smp/server/id ", metadata.simplex)

        metadata.cleanBlankNames()

        // After cleaning, it should be trimmed
        assertEquals("simplex:/smp/server/id", metadata.simplex)
    }

    @Test
    fun testSimplexFieldNullification() {
        val metadata = UserMetadata()
        metadata.simplex = "   "

        metadata.cleanBlankNames()

        // Should be nullified if blank
        assertNull(metadata.simplex)
    }

    @Test
    fun testMetadataSerializationWithSimplex() {
        // Mocking a JSON string that represents a MetadataEvent's content
        val json =
            """
            {
                "name": "User",
                "simplex": "simplex:/smp/server/id"
            }
            """.trimIndent()

        val metadata = mapper.readValue(json, UserMetadata::class.java)
        assertEquals("simplex:/smp/server/id", metadata.simplex)
    }

    @Test
    fun testMetadataSerializationWithMonero() {
        val json =
            """
            {
                "name": "User",
                "cryptocurrency_addresses": {
                    "monero": "44AFF"
                }
            }
            """.trimIndent()

        val metadata = mapper.readValue(json, UserMetadata::class.java)
        assertEquals("44AFF", metadata.moneroAddress())
    }
}
