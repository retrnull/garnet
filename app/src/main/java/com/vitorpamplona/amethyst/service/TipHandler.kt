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
package com.vitorpamplona.amethyst.service

import android.content.Context
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.PendingTransaction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TransactionPriority
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.TipEvent
import com.vitorpamplona.quartz.events.TipSplitSetup
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class TipHandler(val account: Account) {
    suspend fun tip(
        note: Note,
        amount: ULong,
        message: String,
        context: Context,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        tipType: TipEvent.TipType,
        priority: TransactionPriority,
    ) = withContext(Dispatchers.IO) {
        val noteEvent = note.event
        val tipSplitSetup = noteEvent?.tipSplitSetup()
        var accountTipRecipient = note.author?.info?.moneroAddress()
        var accountPubKey: HexKey? = null
        note.author?.let {
            it.info?.let { info ->
                accountTipRecipient = info.moneroAddress()
                if (accountTipRecipient == null) {
                    info.about?.split(" ")?.let { tokens ->
                        for (token in tokens) {
                            if (token.length == 95 && account.moneroAddressIsValid(token)) {
                                accountTipRecipient = token
                                break
                            }
                        }
                    }
                }
                accountPubKey = it.pubkeyHex
            }
        }

        onProgress(0.10f)

        val tippedUsers = mutableListOf<HexKey>()

        val tipsToSend =
            if (!tipSplitSetup.isNullOrEmpty()) {
                tipSplitSetup.mapNotNull { tip ->
                    if (!tip.isAddress) {
                        val recipient = LocalCache.checkGetOrCreateUser(tip.addressOrPubKeyHex)
                        recipient?.info?.moneroAddress()?.let {
                            if (account.moneroAddressIsValid(it)) {
                                tippedUsers += tip.addressOrPubKeyHex
                                tip.copy(
                                    addressOrPubKeyHex = it,
                                    isAddress = true,
                                )
                            } else {
                                null
                            }
                        }
                    } else {
                        if (account.moneroAddressIsValid(tip.addressOrPubKeyHex)) {
                            tip
                        } else {
                            null
                        }
                    }
                }
            } else if (noteEvent is LiveActivitiesEvent && noteEvent.hasHost()) {
                noteEvent.hosts().mapNotNull { host ->
                    val hostUser = LocalCache.checkGetOrCreateUser(host)
                    hostUser?.info?.moneroAddress()?.let {
                        if (account.moneroAddressIsValid(it)) {
                            tippedUsers += it
                            TipSplitSetup(it, null, 1.0, true)
                        } else {
                            null
                        }
                    }
                }
            } else {
                accountTipRecipient?.let { tipRecipient ->
                    accountPubKey?.let {
                        if (account.moneroAddressIsValid(tipRecipient)) {
                            tippedUsers += it
                            listOf(TipSplitSetup(tipRecipient, null, 1.0, true))
                        } else {
                            null
                        }
                    }
                }
            }

        if (tipsToSend.isNullOrEmpty()) {
            onError(
                context.getString(R.string.missing_monero_setup),
                context.getString(
                    R.string.user_does_not_have_a_monero_address_setup_to_receive_tips,
                ),
            )
            return@withContext
        }

        onProgress(0.50f)

        val transaction = account.tip(tipsToSend, amount, priority, note.idHex)
        if (transaction.status.type != PendingTransaction.StatusType.OK) {
            val error = "${transaction.status.error[0].uppercase()}${transaction.status.error.substring(1)}"
            onError(
                context.getString(R.string.error_dialog_tip_error),
                error,
            )
            return@withContext
        }

        if (tipType != TipEvent.TipType.PRIVATE) {
            val tippedUsers = tippedUsers.toSet().ifEmpty { accountPubKey?.let { setOf(it) } }
            if (tippedUsers.isNullOrEmpty()) {
                onProgress(1f)
                return@withContext
            }

            val signer =
                if (tipType == TipEvent.TipType.PUBLIC) {
                    null
                } else {
                    val keyPair = KeyPair()
                    NostrSignerInternal(keyPair)
                }

            val proofMessage =
                if (tipType == TipEvent.TipType.PUBLIC) {
                    account.userProfile().pubkeyHex
                } else {
                    signer!!.pubKey
                }

            var proofsWithStatus = account.getProofs(transaction.txId, tipsToSend, proofMessage)
            // there can be errors getting the transaction for generating the proof right after sending because the daemon
            // might not be able to return the just sent transaction, so we retry until we manage to get it.
            while (proofsWithStatus.any { (proof, _) -> !proof.status.isOk() }) {
                delay(500.milliseconds)
                proofsWithStatus = account.getProofs(transaction.txId, tipsToSend, proofMessage)
            }
            val proofs: MutableMap<String, Array<String>> = mutableMapOf()
            for ((proof, recipient) in proofsWithStatus) {
                // no need to specify recommended relay, recipient is always an address here
                proofs += proof.proof to arrayOf(recipient)
            }

            onProgress(0.75f)

            account.sendTipProof(
                note,
                tippedUsers,
                transaction.txId,
                proofs,
                tipType,
                message,
                signer,
            ) {
                onProgress(1f)
            }

            return@withContext
        }

        onProgress(1f)
    }
}
