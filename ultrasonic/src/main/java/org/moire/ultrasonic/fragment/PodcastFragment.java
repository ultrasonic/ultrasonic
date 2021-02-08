package org.moire.ultrasonic.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.PodcastsChannel;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.PodcastsChannelsAdapter;

import java.util.List;

public class PodcastFragment extends Fragment {

    private View emptyTextView;
    ListView channelItemsListView = null;
    private CancellationToken cancellationToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.podcasts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cancellationToken = new CancellationToken();
        FragmentTitle.Companion.setTitle(this, R.string.podcasts_label);

        emptyTextView = view.findViewById(R.id.select_podcasts_empty);
        channelItemsListView = view.findViewById(R.id.podcasts_channels_items_list);
        channelItemsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PodcastsChannel pc = (PodcastsChannel) parent.getItemAtPosition(position);
                if (pc == null) {
                    return;
                }

                Bundle bundle = new Bundle();
                bundle.putString(Constants.INTENT_EXTRA_NAME_PODCAST_CHANNEL_ID, pc.getId());
                Navigation.findNavController(getView()).navigate(R.id.selectAlbumFragment, bundle);
            }
        });

        // TODO: Probably a swipeRefresh should be added here in the long run
        load();
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load()
    {
        BackgroundTask<List<PodcastsChannel>> task = new TabActivityBackgroundTask<List<PodcastsChannel>>(getActivity(), true, null, cancellationToken)
        {
            @Override
            protected List<PodcastsChannel> doInBackground() throws Throwable
            {
                MusicService musicService = MusicServiceFactory.getMusicService(getContext());
                return musicService.getPodcastsChannels(false,getContext(), this);

   			/*	 TODO Why is here a cache cleaning? (original TODO text: c'est quoi ce nettoyage de cache ?)
				if (!Util.isOffline(PodcastsActivity.this))
					new CacheCleaner(PodcastsActivity.this, getDownloadService()).cleanPlaylists(playlists);
            */
            }

            @Override
            protected void done(List<PodcastsChannel> result)
            {
                channelItemsListView.setAdapter(new PodcastsChannelsAdapter(getContext(), result));
                emptyTextView.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
            }
        };
        task.execute();
    }
}
