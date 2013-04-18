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
package com.thejoshwa.ultrasonic.androidapp.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import com.thejoshwa.ultrasonic.androidapp.domain.Artist;
import com.thejoshwa.ultrasonic.androidapp.domain.Indexes;
import com.thejoshwa.ultrasonic.androidapp.domain.JukeboxStatus;
import com.thejoshwa.ultrasonic.androidapp.domain.Lyrics;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.MusicFolder;
import com.thejoshwa.ultrasonic.androidapp.domain.Playlist;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchCritera;
import com.thejoshwa.ultrasonic.androidapp.domain.SearchResult;
import com.thejoshwa.ultrasonic.androidapp.util.Constants;
import com.thejoshwa.ultrasonic.androidapp.util.FileUtil;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

/**
 * @author Sindre Mehus
 */
public class OfflineMusicService extends RESTMusicService {

    @Override
    public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception {
        return true;
    }

    @Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        List<Artist> artists = new ArrayList<Artist>();
        File root = FileUtil.getMusicDirectory(context);
        for (File file : FileUtil.listFiles(root)) {
            if (file.isDirectory()) {
                Artist artist = new Artist();
                artist.setId(file.getPath());
                artist.setName(file.getName());
                
                String artistIndex = "";
                
                try {
                	artistIndex = file.getName().substring(0, 1);
                }
                catch (Exception ex) { }
                
                artist.setIndex(artistIndex);
                
                artists.add(artist);
            }
        }
        return new Indexes(0L, Collections.<Artist>emptyList(), artists);
    }

    @Override
    public MusicDirectory getMusicDirectory(String id, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        File dir = new File(id);
        MusicDirectory result = new MusicDirectory();
        result.setName(dir.getName());

        Set<String> names = new HashSet<String>();

        for (File file : FileUtil.listMusicFiles(dir)) {
            String name = getName(file);
            if (name != null & !names.contains(name)) {
                names.add(name);
                result.addChild(createEntry(context, file, name));
            }
        }
        return result;
    }

    private String getName(File file) {
        String name = file.getName();
        if (file.isDirectory()) {
            return name;
        }

        if (name.endsWith(".partial") || name.contains(".partial.") || name.equals(Constants.ALBUM_ART_FILE)) {
            return null;
        }

        name = name.replace(".complete", "");
        return FileUtil.getBaseName(name);
    }

    private MusicDirectory.Entry createEntry(Context context, File file, String name) {
        MusicDirectory.Entry entry = new MusicDirectory.Entry();
        entry.setDirectory(file.isDirectory());
        entry.setId(file.getPath());
        entry.setParent(file.getParent());
        entry.setSize(file.length());
        String root = FileUtil.getMusicDirectory(context).getPath();
        entry.setPath(file.getPath().replaceFirst("^" + root + "/" , ""));
        entry.setTitle(name);
        
        if (file.isFile()) {
        	String artist = null;
        	String album = null;
        	String title = null;
        	String track = null;
        	String disc = null;
        	String year = null;
        	String genre = null;
        	//String bitrate = null;
        	String duration = null;
        	String hasVideo = null;
        	
        	try
        	{
            	MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        		mmr.setDataSource(file.getPath());
        		artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        		album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        		title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        		track = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
        		disc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
        		year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
        		genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
        		//bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
                mmr.release();
        	} catch (Exception ex) { }
        	
            entry.setArtist(artist != null ? artist : file.getParentFile().getParentFile().getName());
            entry.setAlbum(album != null ? album : file.getParentFile().getName());
           
            if (title != null) {
            	entry.setTitle(title);
            }
            
            entry.setVideo(hasVideo != null);
            
            Log.i("OfflineMusicService", "Offline Stuff: " + track);
            
            if (track != null) {
            	
            	int trackValue = 0;
            
            	try {
            		int slashIndex = track.indexOf("/");
          		
            		if (slashIndex > 0) {
          				track = track.substring(0, slashIndex);
            		}
          		
          			trackValue = Integer.parseInt(track);
            	} 
          		catch(Exception ex) {            Log.e("OfflineMusicService", "Offline Stuff: " + ex); }
            	
                Log.i("OfflineMusicService", "Offline Stuff: Setting Track: " + trackValue);
            	
            	entry.setTrack(trackValue);
            }
            
            if (disc != null) {
            	int discValue = 0;
            	
            	try {
            		int slashIndex = disc.indexOf("/");
          		
            		if (slashIndex > 0) {
            			disc = disc.substring(0, slashIndex);
            		}
          		
          			discValue = Integer.parseInt(disc);
          		} 
          		catch(Exception ex) { }
            	
      			entry.setDiscNumber(discValue);
            }
            
            if (year != null) {
            	int yearValue = 0;
            	
          		try {
          			yearValue = Integer.parseInt(year);
          		} catch(Exception ex) { }
          		
      			entry.setYear(yearValue);            		
            }
            
            if (genre != null) {
            	entry.setGenre(genre);
            }
            
//            if (bitrate != null) {
//              	int bitRateValue = 0;
//              	
//          		try {
//          			bitRateValue = Integer.parseInt(bitrate) / 1000;
//          		} catch(Exception ex) { }
//          		
//      			entry.setBitRate(bitRateValue);
//            }
            
            if (duration != null) {
            	long durationValue = 0;
            	
          		try {
          			durationValue = Long.parseLong(duration);
          			durationValue = TimeUnit.MILLISECONDS.toSeconds(durationValue);
          		} catch(Exception ex) { }
          		
            	entry.setDuration(durationValue);
            }
        }

        entry.setSuffix(FileUtil.getExtension(file.getName().replace(".complete", "")));

        File albumArt = FileUtil.getAlbumArtFile(context, entry);
        
        if (albumArt.exists()) {
            entry.setCoverArt(albumArt.getPath());
        }
        
        return entry;
    }

    @Override
    public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, boolean saveToFile, ProgressListener progressListener) throws Exception {
        InputStream in = new FileInputStream(entry.getCoverArt());
        try {
            byte[] bytes = Util.toByteArray(in);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            Log.i("getCoverArt", "getCoverArt");
            return Util.scaleBitmap(bitmap, size);
        } finally {
            Util.close(in);
        }
    }

    @Override
    public void star(String id, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Star not available in offline mode");
    }
    
    @Override
    public void unstar(String id, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("UnStar not available in offline mode");
    }
    
    @Override
    public List<MusicFolder> getMusicFolders(Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Music folders not available in offline mode");
    }

    @Override
    public SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Search not available in offline mode");
    }

    @Override
    public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Playlists not available in offline mode");
    }

    @Override
    public MusicDirectory getPlaylist(String id, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Playlists not available in offline mode");
    }

    @Override
    public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Playlists not available in offline mode");
    }

    @Override
    public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Lyrics not available in offline mode");
    }

    @Override
    public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Scrobbling not available in offline mode");
    }

    @Override
    public MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Album lists not available in offline mode");
    }

    @Override
    public String getVideoUrl(Context context, String id) {
        return null;
    }

    @Override
    public JukeboxStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Jukebox not available in offline mode");
    }

    @Override
    public JukeboxStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Jukebox not available in offline mode");
    }

    @Override
    public JukeboxStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Jukebox not available in offline mode");
    }

    @Override
    public JukeboxStatus startJukebox(Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Jukebox not available in offline mode");
    }

    @Override
    public JukeboxStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Jukebox not available in offline mode");
    }

    @Override
    public JukeboxStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception {
        throw new OfflineException("Jukebox not available in offline mode");
    }
    
    @Override
    public SearchResult getStarred(Context context, ProgressListener progressListener) throws Exception {
    	throw new OfflineException("Starred not available in offline mode");
    }

    @Override
    public MusicDirectory getRandomSongs(int size, Context context, ProgressListener progressListener) throws Exception {
        File root = FileUtil.getMusicDirectory(context);
        List<File> children = new LinkedList<File>();
        listFilesRecursively(root, children);
        MusicDirectory result = new MusicDirectory();

        if (children.isEmpty()) {
            return result;
        }
        Random random = new Random();
        for (int i = 0; i < size; i++) {
            File file = children.get(random.nextInt(children.size()));
            result.addChild(createEntry(context, file, getName(file)));
        }

        return result;
    }

    private void listFilesRecursively(File parent, List<File> children) {
        for (File file : FileUtil.listMusicFiles(parent)) {
            if (file.isFile()) {
                children.add(file);
            } else {
                listFilesRecursively(file, children);
            }
        }
    }
}
