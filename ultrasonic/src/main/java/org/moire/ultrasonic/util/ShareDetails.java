package org.moire.ultrasonic.util;

import org.moire.ultrasonic.domain.MusicDirectory;

import java.util.List;

/**
 * Created by Josh on 12/17/13.
 */
public class ShareDetails
{
	public String Description;
	public boolean ShareOnServer;
	public long Expiration;
	public List<MusicDirectory.Entry> Entries;
}
