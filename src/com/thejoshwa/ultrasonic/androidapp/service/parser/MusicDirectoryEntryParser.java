/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.service.parser;

import android.content.Context;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;

/**
 * @author Sindre Mehus
 */
public class MusicDirectoryEntryParser extends AbstractParser {

    public MusicDirectoryEntryParser(Context context) {
        super(context);
    }

    protected MusicDirectory.Entry parseEntry(String artist, boolean isAlbum, int bookmarkPosition) {
        MusicDirectory.Entry entry = new MusicDirectory.Entry();
        entry.setId(get("id"));
        entry.setParent(get("parent"));
        entry.setTitle(isAlbum ? get("name") : get("title"));
        entry.setIsDirectory(getBoolean("isDir") || isAlbum);
        entry.setCoverArt(get("coverArt"));
        entry.setArtist(get("artist"));
        entry.setArtistId(get("artistId"));
        entry.setYear(getInteger("year"));
        entry.setCreated(get("created"));
        entry.setStarred(getValueExists(Constants.STARRED));

        if (!entry.isDirectory()) {
            entry.setAlbum(get("album"));
            entry.setAlbumId(get("albumId"));
            entry.setTrack(getInteger("track"));
            entry.setGenre(get("genre"));
            entry.setContentType(get("contentType"));
            entry.setSuffix(get("suffix"));
            entry.setTranscodedContentType(get("transcodedContentType"));
            entry.setTranscodedSuffix(get("transcodedSuffix"));
            entry.setSize(getLong("size"));
            entry.setDuration(getInteger("duration"));
            entry.setBitRate(getInteger("bitRate"));
            entry.setPath(get("path"));
            entry.setIsVideo(getBoolean("isVideo"));
            entry.setDiscNumber(getInteger("discNumber"));
            entry.setType(get("type"));
            entry.setBookmarkPosition(bookmarkPosition);
        } else if(!"".equals(artist)) {
			entry.setPath(artist + "/" + entry.getTitle());
		}
        
        return entry;
    }
}