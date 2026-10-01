package io.github.kdroidfilter.seforim.tabs

// Temporary release switch. Set to true to restore history and favorites UI and shortcuts.
// Stored data and Ctrl/Cmd+Shift+T remain available while this is false.
const val HISTORY_FAVORITES_ENABLED = false

internal fun TabsDestination.availableDestination(): TabsDestination =
    if (!HISTORY_FAVORITES_ENABLED && (this is TabsDestination.History || this is TabsDestination.Favorites)) {
        TabsDestination.Home(tabId = tabId)
    } else {
        this
    }
