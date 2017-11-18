package org.moire.ultrasonic.domain;

import org.moire.ultrasonic.domain.MusicDirectory.Entry;

import java.io.Serializable;
import java.util.Date;

public class Bookmark implements Serializable
{
	/**
	 *
	 */
	private static final long serialVersionUID = 8988990025189807803L;
	private int position;
	private String username;
	private String comment;
	private Date created;
	private Date changed;
	private Entry entry;

	public int getPosition()
	{
		return position;
	}

	public void setPosition(int position)
	{
		this.position = position;
	}

	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getComment()
	{
		return comment;
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getChanged() {
        return changed;
    }

    public void setChanged(Date changed) {
        this.changed = changed;
    }

	public Entry getEntry()
	{
		return this.entry;
	}

	public void setEntry(Entry entry)
	{
		this.entry = entry;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Bookmark bookmark = (Bookmark) o;

        if (position != bookmark.position) return false;
        if (username != null ? !username.equals(bookmark.username) : bookmark.username != null)
            return false;
        if (comment != null ? !comment.equals(bookmark.comment) : bookmark.comment != null)
            return false;
        if (created != null ? !created.equals(bookmark.created) : bookmark.created != null)
            return false;
        if (changed != null ? !changed.equals(bookmark.changed) : bookmark.changed != null)
            return false;
        return entry != null ? entry.equals(bookmark.entry) : bookmark.entry == null;
    }

    @Override
    public int hashCode() {
        int result = position;
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        result = 31 * result + (created != null ? created.hashCode() : 0);
        result = 31 * result + (changed != null ? changed.hashCode() : 0);
        result = 31 * result + (entry != null ? entry.hashCode() : 0);
        return result;
    }
}
