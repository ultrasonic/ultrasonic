package com.thejoshwa.ultrasonic.androidapp.domain;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Share implements Serializable
{
	private static final long serialVersionUID = 1487561657691009668L;
	private String id;
	private String url;
	private String description;
	private String username;
	private String created;
	private String lastVisited;
	private String expires;
	private Long visitCount;
	private List<Entry> entries;

	public Share()
	{
		entries = new ArrayList<Entry>();
	}

	public String getName()
	{
		return url.replaceFirst(".*/([^/?]+).*", "$1");
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}

	public String getUrl()
	{
		return url;
	}

	public void setUrl(String url)
	{
		this.url = url;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}

	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getCreated()
	{
		return this.created;
	}

	public void setCreated(String created)
	{
		this.created = created;
	}

	public String getLastVisited()
	{
		return lastVisited;
	}

	public void setLastVisited(String lastVisited)
	{
		this.lastVisited = lastVisited;
	}

	public String getExpires()
	{
		return expires;
	}

	public void setExpires(String expires)
	{
		this.expires = expires;
	}

	public Long getVisitCount()
	{
		return visitCount;
	}

	public void setVisitCount(Long visitCount)
	{
		this.visitCount = visitCount;
	}

	public List<Entry> getEntries()
	{
		return this.entries;
	}

	public void addEntry(Entry entry)
	{
		entries.add(entry);
	}
}
