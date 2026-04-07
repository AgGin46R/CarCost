package com.aggin.carcost.data.notifications

/**
 * Tracks which chat screen (if any) the user is currently viewing.
 * Set by ChatScreen via DisposableEffect when the screen enters/leaves composition.
 * RealtimeSyncManager uses this to suppress chat notifications when the user
 * is already reading that conversation.
 */
object ActiveChatTracker {
    @Volatile
    var activeCarId: String? = null
}
