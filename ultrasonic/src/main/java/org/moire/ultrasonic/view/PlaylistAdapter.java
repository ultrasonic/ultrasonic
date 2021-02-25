package org.moire.ultrasonic.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.Playlist;

import java.util.List;

/**
 * @author Sindre Mehus
 */
public class PlaylistAdapter extends ArrayAdapter<Playlist>
{

	private final Context context;

	public PlaylistAdapter(Context context, List<Playlist> Playlists)
	{
		super(context, R.layout.playlist_list_item, Playlists);
		this.context = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		Playlist entry = getItem(position);
		PlaylistView view;

		if (convertView instanceof PlaylistView)
		{
			PlaylistView currentView = (PlaylistView) convertView;

			ViewHolder viewHolder = (ViewHolder) convertView.getTag();
			view = currentView;
			view.setViewHolder(viewHolder);
		}
		else
		{
			view = new PlaylistView(context);
			view.setLayout();
		}

		view.setPlaylist(entry);
		return view;
	}

	static class ViewHolder
	{
		TextView name;
	}
}