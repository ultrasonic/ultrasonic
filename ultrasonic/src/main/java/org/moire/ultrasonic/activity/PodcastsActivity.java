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

package org.moire.ultrasonic.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.PodcastsChannel;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.view.PodcastsChannelsAdapter;

import java.util.List;

public class PodcastsActivity extends SubsonicTabActivity {

	private View emptyTextView;
    SubsonicTabActivity currentActivity = null;
    ListView channelItemsListView = null;

	Context currentContext = (Context)this;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
        this.currentActivity = this;

		super.onCreate(savedInstanceState);
		setContentView(R.layout.podcasts);


		emptyTextView = findViewById(R.id.select_podcasts_empty);
        channelItemsListView = (ListView)findViewById(R.id.podcasts_channels_items_list);
		channelItemsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				PodcastsChannel pc = (PodcastsChannel) parent.getItemAtPosition(position);
				if (pc == null) {
					return;
				}

				Intent intent = new Intent(currentContext, SelectAlbumActivity.class);
				intent.putExtra(Constants.INTENT_EXTRA_NAME_PODCAST_CHANNEL_ID, pc.getId());
				startActivityForResultWithoutTransition(PodcastsActivity.this, intent);
			}
		});

		load();
    }



	private void load()
	{
		BackgroundTask<List<PodcastsChannel>> task = new TabActivityBackgroundTask<List<PodcastsChannel>>(this, true)
		{
			@Override
			protected List<PodcastsChannel> doInBackground() throws Throwable
			{
				MusicService musicService = MusicServiceFactory.getMusicService(PodcastsActivity.this);
				List<PodcastsChannel> channels = musicService.getPodcastsChannels(false,PodcastsActivity.this, this);

			/*	 TODO c'est quoi ce nettoyage de cache ?
				if (!Util.isOffline(PodcastsActivity.this))
					new CacheCleaner(PodcastsActivity.this, getDownloadService()).cleanPlaylists(playlists);
            */
				return channels;
			}

			@Override
			protected void done(List<PodcastsChannel> result)
			{
                channelItemsListView.setAdapter(new PodcastsChannelsAdapter(currentActivity, result));
				emptyTextView.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
			}
		};
		task.execute();
	}


}