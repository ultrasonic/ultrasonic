package org.moire.ultrasonic.domain;

import org.moire.ultrasonic.domain.MusicDirectory.Entry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Share implements Serializable {
	private static final long serialVersionUID = 1487561657691009668L;
	private static final Pattern urlPattern = Pattern.compile(".*/([^/?]+).*");
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
		return urlPattern.matcher(url).replaceFirst("$1");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Share share = (Share) o;

        if (id != null ? !id.equals(share.id) : share.id != null) return false;
        if (url != null ? !url.equals(share.url) : share.url != null) return false;
        if (description != null ? !description.equals(share.description) : share.description != null)
            return false;
        if (username != null ? !username.equals(share.username) : share.username != null)
            return false;
        if (created != null ? !created.equals(share.created) : share.created != null) return false;
        if (lastVisited != null ? !lastVisited.equals(share.lastVisited) : share.lastVisited != null)
            return false;
        if (expires != null ? !expires.equals(share.expires) : share.expires != null) return false;
        if (visitCount != null ? !visitCount.equals(share.visitCount) : share.visitCount != null)
            return false;
        return entries != null ? entries.equals(share.entries) : share.entries == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (created != null ? created.hashCode() : 0);
        result = 31 * result + (lastVisited != null ? lastVisited.hashCode() : 0);
        result = 31 * result + (expires != null ? expires.hashCode() : 0);
        result = 31 * result + (visitCount != null ? visitCount.hashCode() : 0);
        result = 31 * result + (entries != null ? entries.hashCode() : 0);
        return result;
    }
}
