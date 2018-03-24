package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Bookmark

class BookmarksResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("bookmarks") private val bookmarksWrapper = BookmarkWrapper()

    val bookmarkList: List<Bookmark> get() = bookmarksWrapper.bookmarkList
}

internal class BookmarkWrapper(
    @JsonProperty("bookmark") val bookmarkList: List<Bookmark> = emptyList()
)
