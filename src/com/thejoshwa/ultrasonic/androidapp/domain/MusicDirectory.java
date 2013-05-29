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
package com.thejoshwa.ultrasonic.androidapp.domain;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.io.Serializable;

/**
 * @author Sindre Mehus
 */
public class MusicDirectory {

    private String name;
    private final List<Entry> children = new ArrayList<Entry>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addChild(Entry child) {
        children.add(child);
    }

    public List<Entry> getChildren() {
        return getChildren(true, true);
    }

    public List<Entry> getChildren(boolean includeDirs, boolean includeFiles) {
        if (includeDirs && includeFiles) {
            return children;
        }

        List<Entry> result = new ArrayList<Entry>(children.size());
        for (Entry child : children) {
            if (child.isDirectory() && includeDirs || !child.isDirectory() && includeFiles) {
                result.add(child);
            }
        }
        return result;
    }

    public static class Entry implements Serializable {
        /**
		 * 
		 */
		private static final long serialVersionUID = -3339106650010798108L;
		/**
		 * 
		 */
		private String id;
        private String parent;
        private boolean isDirectory;
        private String title;
        private String album;
        private String albumId;
        private String artist;
        private String artistId;
        private Integer track;
        private Integer year;
        private String genre;
        private String contentType;
        private String suffix;
        private String transcodedContentType;
        private String transcodedSuffix;
        private String coverArt;
        private Long size;
        private Long songCount;
        private Integer duration;
        private Integer bitRate;
        private String path;
        private boolean isVideo;
        private boolean isStarred;
        private Integer discNumber;
        private String type;
        private Date created;
        private int closeness;
        private int bookmarkPosition;
        
        public Integer getDiscNumber() {
        	return discNumber;
        }
        
        public void setDiscNumber(Integer discNumber) {
        	this.discNumber = discNumber;
        }

        public boolean getStarred() {
        	return isStarred;
        }
        
        public void setStarred(boolean starred) {
        	this.isStarred = starred;
        }
        
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public void setIsDirectory(boolean directory) {
            this.isDirectory = directory;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAlbum() {
            return album;
        }

        public void setAlbum(String album) {
            this.album = album;
        }
        
        public String getAlbumId() {
            return albumId;
        }

        public void setAlbumId(String albumId) {
            this.albumId = albumId;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }
        
        public String getArtistId() {
            return artistId;
        }

        public void setArtistId(String artistId) {
            this.artistId = artistId;
        }

        public Integer getTrack() {
            return track == null ? 0 : track;
        }

        public void setTrack(Integer track) {
            this.track = track;
        }
        
        public Long getSongcount() {
            return songCount;
        }

        public void setSongCount(Long songCount) {
            this.songCount = songCount;
        }

        public Integer getYear() {
            return year == null ? 0 : year;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }

        public String getTranscodedContentType() {
            return transcodedContentType;
        }

        public void setTranscodedContentType(String transcodedContentType) {
            this.transcodedContentType = transcodedContentType;
        }

        public String getTranscodedSuffix() {
            return transcodedSuffix;
        }

        public void setTranscodedSuffix(String transcodedSuffix) {
            this.transcodedSuffix = transcodedSuffix;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public Integer getDuration() {
            return duration;
        }

        public void setDuration(Integer duration) {
            this.duration = duration;
        }
        
        public void setDuration(long duration) {
            this.duration = (int) duration;
        }

        public Integer getBitRate() {
            return bitRate;
        }

        public void setBitRate(Integer bitRate) {
            this.bitRate = bitRate;
        }

        public String getCoverArt() {
            return coverArt;
        }

        public void setCoverArt(String coverArt) {
            this.coverArt = coverArt;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isVideo() {
            return isVideo;
        }

        public void setIsVideo(boolean video) {
            this.isVideo = video;
        }
        
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
        
        public Date getCreated() {
            return created;
        }

        public void setCreated(String created) {
        	if (created != null) {
        		try {
					this.created = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(created);
				} catch (ParseException e) { 
					this.created = null;
				}
        	} else {
        		this.created = null;
        	}
        }
        
    	public int getCloseness() {
			return closeness;
		}

		public void setCloseness(int closeness) {
			this.closeness = closeness;
		}
		
    	public int getBookmarkPosition() {
    		return bookmarkPosition;
    	}

		public void setBookmarkPosition(int bookmarkPosition) {
			this.bookmarkPosition = bookmarkPosition;
		}

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Entry entry = (Entry) o;
            return id.equals(entry.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return title;
        }
    }
}