/*
 This file is part of UltraSonic.

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

 Copyright 2013 (C) Joshua Bahnsen
 */
package com.thejoshwa.ultrasonic.androidapp.domain;

/**
 * Information about the Subsonic server.
 *
 * @author Joshua Bahnsen
 */
public class UserInfo
{
	private String userName;
	private String email;
	private boolean scrobblingEnabled;
	private boolean adminRole;
	private boolean settingsRole;
	private boolean downloadRole;
	private boolean uploadRole;
	private boolean playlistRole;
	private boolean coverArtRole;
	private boolean commentRole;
	private boolean podcastRole;
	private boolean streamRole;
	private boolean jukeboxRole;
	private boolean shareRole;

	public String getUserName()
	{
		return this.userName;
	}

	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public String getEmail()
	{
		return this.email;
	}

	public void setEmail(String email)
	{
		this.email = email;
	}

	public boolean getScrobblingEnabled()
	{
		return this.scrobblingEnabled;
	}

	public void setScrobblingEnabled(boolean scrobblingEnabled)
	{
		this.scrobblingEnabled = scrobblingEnabled;
	}

	public boolean getAdminRole()
	{
		return this.adminRole;
	}

	public void setAdminRole(boolean adminRole)
	{
		this.adminRole = adminRole;
	}

	public boolean getSettingsRole()
	{
		return this.settingsRole;
	}

	public void setSettingsRole(boolean settingsRole)
	{
		this.settingsRole = settingsRole;
	}

	public boolean getDownloadRole()
	{
		return this.downloadRole;
	}

	public void setDownloadRole(boolean downloadRole)
	{
		this.downloadRole = downloadRole;
	}

	public boolean getUploadRole()
	{
		return this.uploadRole;
	}

	public void setUploadRole(boolean uploadRole)
	{
		this.uploadRole = uploadRole;
	}

	public boolean getPlaylistRole()
	{
		return this.playlistRole;
	}

	public void setPlaylistRole(boolean playlistRole)
	{
		this.playlistRole = playlistRole;
	}

	public boolean getCoverArtRole()
	{
		return this.coverArtRole;
	}

	public void setCoverArtRole(boolean coverArtRole)
	{
		this.coverArtRole = coverArtRole;
	}

	public boolean getCommentRole()
	{
		return this.commentRole;
	}

	public void setCommentRole(boolean commentRole)
	{
		this.commentRole = commentRole;
	}

	public boolean getPodcastRole()
	{
		return this.podcastRole;
	}

	public void setPodcastRole(boolean podcastRole)
	{
		this.podcastRole = podcastRole;
	}

	public boolean getStreamRole()
	{
		return this.streamRole;
	}

	public void setStreamRole(boolean streamRole)
	{
		this.streamRole = streamRole;
	}

	public boolean getJukeboxRole()
	{
		return this.jukeboxRole;
	}

	public void setJukeboxRole(boolean jukeboxRole)
	{
		this.jukeboxRole = jukeboxRole;
	}

	public boolean getShareRole()
	{
		return this.shareRole;
	}

	public void setShareRole(boolean shareRole)
	{
		this.shareRole = shareRole;
	}
}