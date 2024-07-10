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

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

class TipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    var valueByUser: MutableMap<HexKey, ULong> = mutableMapOf()
    val totalValue: ULong
        get() = valueByUser.values.sum()

    fun tipProof(): TipProof? =
        try {
            if (content.isNotEmpty()) {
                mapper.readValue<TipProof>(content)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("TipEvent", "Can't parse content as tip proof: $content", e)
            null
        }

    companion object {
        const val KIND = 1814

        fun create(
            event: EventInterface?,
            users: Set<HexKey>,
            txId: String,
            proofs: Map<String, Array<String>>,
            message: String = "",
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (TipEvent) -> Unit,
        ) {
            if (proofs.isEmpty()) {
                throw IllegalArgumentException("No proofs specified")
            }

            if (users.isEmpty()) {
                throw IllegalArgumentException("At least one user must be specified")
            }

            val proof = TipProof(txId, proofs, message.ifEmpty { null })
            val content =
                ObjectMapper().writeValueAsString(proof)

            val tags: MutableList<Array<String>> = mutableListOf()
            users.forEach {
                tags.add(arrayOf("p", it))
            }

            event?.let {
                if (it is AddressableEvent) {
                    tags.add(arrayOf("a", it.addressTag()))
                } else {
                    tags.add(arrayOf("e", it.id()))
                }
            }

            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }

    enum class TipType() {
        PRIVATE,
        ANONYMOUS,
        PUBLIC,
    }
}

data class TipProof(
    @get:JsonProperty("txid") val txId: String,
    val proofs: Map<String, Array<String>>,
    val message: String?,
)
