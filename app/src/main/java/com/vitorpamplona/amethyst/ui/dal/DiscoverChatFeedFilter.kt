package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.quartz.events.*
import com.vitorpamplona.quartz.utils.TimeUtils

open class DiscoverChatFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultDiscoveryFollowList
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultDiscoveryFollowList == PeopleListEvent.blockList
    }

    override fun feed(): List<Note> {
        val allChannelNotes = LocalCache.channels.values.mapNotNull { LocalCache.getNoteIfExists(it.idHex) }

        val notes = innerApplyFilter(allChannelNotes)

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val now = TimeUtils.now()
        val isGlobal = account.defaultDiscoveryFollowList == GLOBAL_FOLLOWS
        val isHiddenList = showHiddenKey()

        val followingKeySet = account.selectedUsersFollowList(account.defaultDiscoveryFollowList) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(account.defaultDiscoveryFollowList) ?: emptySet()
        val followingGeohashSet = account.selectedGeohashesFollowList(account.defaultDiscoveryFollowList) ?: emptySet()

        val createEvents = collection.filter { it.event is ChannelCreateEvent }
        val anyOtherChannelEvent = collection
            .asSequence()
            .filter { it.event is IsInPublicChatChannel }
            .mapNotNull { (it.event as? IsInPublicChatChannel)?.channel() }
            .mapNotNull { LocalCache.checkGetOrCreateNote(it) }
            .toSet()

        val activities = (createEvents + anyOtherChannelEvent)
            .asSequence()
            // .filter { it.event is ChannelCreateEvent } // Event heads might not be loaded yet.
            .filter { isGlobal || it.author?.pubkeyHex in followingKeySet || it.event?.isTaggedHashes(followingTagSet) == true || it.event?.isTaggedGeoHashes(followingGeohashSet) == true }
            .filter { isHiddenList || it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true }
            .filter { (it.createdAt() ?: 0) <= now }
            .toSet()

        return activities
    }

    override fun sort(collection: Set<Note>): List<Note> {
        val followingKeySet = account.selectedUsersFollowList(account.defaultDiscoveryFollowList)

        val counter = ParticipantListBuilder()
        val participantCounts = collection.associate {
            it to counter.countFollowsThatParticipateOn(it, followingKeySet)
        }

        return collection.sortedWith(
            compareBy(
                { participantCounts[it] },
                { it.createdAt() },
                { it.idHex }
            )
        ).reversed()
    }
}
