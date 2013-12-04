package com.thejoshwa.ultrasonic.androidapp.domain;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;

public class Share implements Serializable
{
	private static final long serialVersionUID = 1487561657691009668L;
	private String id;
	private String url;
	private String description;
	private String username;
	private Date created;
	private Date lastVisited;
	private Date expires;
	private Long visitCount;
	private List<Entry> entries;

	public Share()
	{
		entries = new ArrayList<Entry>();
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

	public Date getCreated()
	{
		return created;
	}

	public void setCreated(String created)
	{
		if (created != null)
		{
			try
			{
				this.created = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(created);
			}
			catch (ParseException e)
			{
				this.created = null;
			}
		}
		else
		{
			this.created = null;
		}
	}

	public Date getLastVisited()
	{
		return lastVisited;
	}

	public void setLastVisited(String lastVisited)
	{
		if (lastVisited != null)
		{
			try
			{
				this.lastVisited = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(lastVisited);
			}
			catch (ParseException e)
			{
				this.lastVisited = null;
			}
		}
		else
		{
			this.lastVisited = null;
		}
	}

	public Date getExpires()
	{
		return expires;
	}

	public void setExpires(String expires)
	{
		if (expires != null)
		{
			try
			{
				this.expires = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(expires);
			}
			catch (ParseException e)
			{
				this.expires = null;
			}
		}
		else
		{
			this.expires = null;
		}
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
