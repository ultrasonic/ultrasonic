package com.thejoshwa.ultrasonic.androidapp.domain;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;

public class Bookmark implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8988990025189807803L;
	private Long position;
	private String username;
    private String comment;
    private Date created;
    private Date changed;
    private List<Entry> entries;
	
    public Long getPosition() {
        return position;
    }

    public void setPosition(Long position) {
        this.position = position;
    }
    
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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
    
    public Date getChanged() {
        return changed;
    }

    public void setChanged(String changed) {
    	if (changed != null) {
    		try {
				this.changed = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(changed);
			} catch (ParseException e) { 
				this.changed = null;
			}
    	} else {
    		this.changed = null;
    	}
    }
    
    public List<Entry> getEntries() {
    	return this.entries;
    }
    
    public void addEntry(Entry entry) {
    	entries.add(entry);
    }
}
