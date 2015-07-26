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
package org.moire.ultrasonic.domain;

import java.io.Serializable;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class Indexes implements Serializable
{

	/**
	 *
	 */
	private static final long serialVersionUID = 8156117238598414701L;
	private final long lastModified;
	private final String ignoredArticles;
	private final List<org.moire.ultrasonic.domain.Artist> shortcuts;
	private final List<org.moire.ultrasonic.domain.Artist> artists;

	public Indexes(long lastModified, String ignoredArticles, List<Artist> shortcuts, List<Artist> artists)
	{
		this.lastModified = lastModified;
		this.ignoredArticles = ignoredArticles;
		this.shortcuts = shortcuts;
		this.artists = artists;
	}

	public long getLastModified()
	{
		return lastModified;
	}

	public List<org.moire.ultrasonic.domain.Artist> getShortcuts()
	{
		return shortcuts;
	}

	public List<org.moire.ultrasonic.domain.Artist> getArtists()
	{
		return artists;
	}

	public String getIgnoredArticles()
	{
		return ignoredArticles;
	}
}