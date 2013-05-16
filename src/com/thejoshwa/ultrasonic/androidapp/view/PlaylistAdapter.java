package com.thejoshwa.ultrasonic.androidapp.view;

import java.util.List;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import java.util.Collections;
import java.util.Comparator;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;
import com.thejoshwa.ultrasonic.androidapp.domain.Playlist;

/**
 * @author Sindre Mehus
 */
public class PlaylistAdapter extends ArrayAdapter<Playlist> {

    private final SubsonicTabActivity activity;

    public PlaylistAdapter(SubsonicTabActivity activity, List<Playlist> Playlists) {
        super(activity, R.layout.playlist_list_item, Playlists);
        this.activity = activity;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Playlist entry = getItem(position);
		PlaylistView view;
		if (convertView != null && convertView instanceof PlaylistView) {
			view = (PlaylistView) convertView;
		} else {
			view = new PlaylistView(activity);
		}
		view.setPlaylist(entry);
		return view;
    }
	
	public static class PlaylistComparator implements Comparator<Playlist> {
        @Override
        public int compare(Playlist playlist1, Playlist playlist2) {
            return playlist1.getName().compareToIgnoreCase(playlist2.getName());
        }

        public static List<Playlist> sort(List<Playlist> playlists) {
            Collections.sort(playlists, new PlaylistComparator());
            return playlists;
        }

    }
}