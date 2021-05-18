package org.moire.ultrasonic.fragment

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.core.component.KoinApiExtension
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.Util

@KoinApiExtension
class AlbumListModel(application: Application) : GenericListModel(application) {

    val albumList: MutableLiveData<List<MusicDirectory.Entry>> = MutableLiveData()
    private var loadedUntil: Int = 0

    fun getAlbumList(
        refresh: Boolean,
        swipe: SwipeRefreshLayout,
        args: Bundle
    ): LiveData<List<MusicDirectory.Entry>> {

        backgroundLoadFromServer(refresh, swipe, args)
        return albumList
    }

    override fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean,
        args: Bundle
    ) {
        super.load(isOffline, useId3Tags, musicService, refresh, args)

        val albumListType = args.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE)!!
        val size = args.getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0)
        var offset = args.getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0)
        val append = args.getBoolean(Constants.INTENT_EXTRA_NAME_APPEND, false)

        val musicDirectory: MusicDirectory
        val musicFolderId = if (showSelectFolderHeader(args)) {
            activeServerProvider.getActiveServer().musicFolderId
        } else {
            null
        }

        // Handle the logic for endless scrolling:
        // If appending the existing list, set the offset from where to load
        if (append) offset += (size + loadedUntil)

        if (useId3Tags) {
            musicDirectory = musicService.getAlbumList2(
                albumListType, size,
                offset, musicFolderId
            )
        } else {
            musicDirectory = musicService.getAlbumList(
                albumListType, size,
                offset, musicFolderId
            )
        }

        currentListIsSortable = isCollectionSortable(albumListType)

        if (append && albumList.value != null) {
            val list = ArrayList<MusicDirectory.Entry>()
            list.addAll(albumList.value!!)
            list.addAll(musicDirectory.getAllChild())
            albumList.postValue(list)
        } else {
            albumList.postValue(musicDirectory.getAllChild())
        }

        loadedUntil = offset
    }

    override fun showSelectFolderHeader(args: Bundle?): Boolean {
        if (args == null) return false

        val albumListType = args.getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE)!!

        val isAlphabetical = (albumListType == AlbumListType.SORTED_BY_NAME.toString()) ||
            (albumListType == AlbumListType.SORTED_BY_ARTIST.toString())

        return !isOffline() && !Util.getShouldUseId3Tags() && isAlphabetical
    }

    private fun isCollectionSortable(albumListType: String): Boolean {
        return albumListType != "newest" && albumListType != "random" &&
            albumListType != "highest" && albumListType != "recent" &&
            albumListType != "frequent"
    }
}
