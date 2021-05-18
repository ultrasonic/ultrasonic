package org.moire.ultrasonic.fragment;

import android.content.Context;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.PodcastsChannel;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.PodcastsChannelsAdapter;

import java.util.List;

/**
 * Displays the podcasts available on the server
 */
public class PodcastFragment extends Fragment {

    private View emptyTextView;
    ListView channelItemsListView = null;
    private CancellationToken cancellationToken;
    private SwipeRefreshLayout swipeRefresh;

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
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cancellationToken = new CancellationToken();
        swipeRefresh = view.findViewById(R.id.podcasts_refresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh() {
                load(view.getContext(), true);
            }
        });

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
                Navigation.findNavController(view).navigate(R.id.trackCollectionFragment, bundle);
            }
        });

        load(view.getContext(), false);
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void load(final Context context, final boolean refresh)
    {
        BackgroundTask<List<PodcastsChannel>> task = new FragmentBackgroundTask<List<PodcastsChannel>>(getActivity(), true, swipeRefresh, cancellationToken)
        {
            @Override
            protected List<PodcastsChannel> doInBackground() throws Throwable
            {
                MusicService musicService = MusicServiceFactory.getMusicService();
                return musicService.getPodcastsChannels(refresh);
            }

            @Override
            protected void done(List<PodcastsChannel> result)
            {
                channelItemsListView.setAdapter(new PodcastsChannelsAdapter(context, result));
                emptyTextView.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
            }
        };
        task.execute();
    }
}
