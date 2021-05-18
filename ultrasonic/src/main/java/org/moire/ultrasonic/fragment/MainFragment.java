package org.moire.ultrasonic.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.data.ServerSetting;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.MergeAdapter;
import org.moire.ultrasonic.util.Util;

import java.util.Collections;

import kotlin.Lazy;

import static java.util.Arrays.asList;
import static org.koin.java.KoinJavaComponent.inject;

/**
 * Displays the Main screen of Ultrasonic, where the music library can be browsed
 */
public class MainFragment extends Fragment {

    private static boolean shouldUseId3;
    private static String lastActiveServerProperties;

    private ListView list;

    private final Lazy<ActiveServerProvider> activeServerProvider = inject(ActiveServerProvider.class);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        list = view.findViewById(R.id.main_list);
        setupMenuList(list);

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean shouldRestart = false;

        boolean id3 = Util.getShouldUseId3Tags();
        String currentActiveServerProperties = getActiveServerProperties();

        if (id3 != shouldUseId3)
        {
            shouldUseId3 = id3;
            shouldRestart = true;
        }

        if (!currentActiveServerProperties.equals(lastActiveServerProperties))
        {
            lastActiveServerProperties = currentActiveServerProperties;
            shouldRestart = true;
        }

        if (shouldRestart) {
            setupMenuList(list);
        }
    }

    private void setupMenuList(ListView list)
    {
        final View buttons = getLayoutInflater().inflate(R.layout.main_buttons, list, false);
        final View serverButton = buttons.findViewById(R.id.main_select_server);
        final TextView serverTextView = serverButton.findViewById(R.id.main_select_server_2);

        lastActiveServerProperties = getActiveServerProperties();
        String name = activeServerProvider.getValue().getActiveServer().getName();

        serverTextView.setText(name);

        final View musicTitle = buttons.findViewById(R.id.main_music);
        final View artistsButton = buttons.findViewById(R.id.main_artists_button);
        final View albumsButton = buttons.findViewById(R.id.main_albums_button);
        final View genresButton = buttons.findViewById(R.id.main_genres_button);
        final View videosTitle = buttons.findViewById(R.id.main_videos_title);
        final View songsTitle = buttons.findViewById(R.id.main_songs);
        final View randomSongsButton = buttons.findViewById(R.id.main_songs_button);
        final View songsStarredButton = buttons.findViewById(R.id.main_songs_starred);
        final View albumsTitle = buttons.findViewById(R.id.main_albums);
        final View albumsNewestButton = buttons.findViewById(R.id.main_albums_newest);
        final View albumsRandomButton = buttons.findViewById(R.id.main_albums_random);
        final View albumsHighestButton = buttons.findViewById(R.id.main_albums_highest);
        final View albumsStarredButton = buttons.findViewById(R.id.main_albums_starred);
        final View albumsRecentButton = buttons.findViewById(R.id.main_albums_recent);
        final View albumsFrequentButton = buttons.findViewById(R.id.main_albums_frequent);
        final View albumsAlphaByNameButton = buttons.findViewById(R.id.main_albums_alphaByName);
        final View albumsAlphaByArtistButton = buttons.findViewById(R.id.main_albums_alphaByArtist);
        final View videosButton = buttons.findViewById(R.id.main_videos);

        final MergeAdapter adapter = new MergeAdapter();
        adapter.addViews(Collections.singletonList(serverButton), true);

        if (!ActiveServerProvider.Companion.isOffline())
        {
            adapter.addView(musicTitle, false);
            adapter.addViews(asList(artistsButton, albumsButton, genresButton), true);
            adapter.addView(songsTitle, false);
            adapter.addViews(asList(randomSongsButton, songsStarredButton), true);
            adapter.addView(albumsTitle, false);

            if (Util.getShouldUseId3Tags())
            {
                shouldUseId3 = true;
                adapter.addViews(asList(albumsNewestButton, albumsRecentButton, albumsFrequentButton, albumsRandomButton, albumsStarredButton, albumsAlphaByNameButton, albumsAlphaByArtistButton), true);
            }
            else
            {
                shouldUseId3 = false;
                adapter.addViews(asList(albumsNewestButton, albumsRecentButton, albumsFrequentButton, albumsHighestButton, albumsRandomButton, albumsStarredButton, albumsAlphaByNameButton, albumsAlphaByArtistButton), true);
            }

            adapter.addView(videosTitle, false);
            adapter.addViews(Collections.singletonList(videosButton), true);
        }

        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (view == serverButton)
            {
                showServers();
            }
            else if (view == albumsNewestButton)
            {
                showAlbumList("newest", R.string.main_albums_newest);
            }
            else if (view == albumsRandomButton)
            {
                showAlbumList("random", R.string.main_albums_random);
            }
            else if (view == albumsHighestButton)
            {
                showAlbumList("highest", R.string.main_albums_highest);
            }
            else if (view == albumsRecentButton)
            {
                showAlbumList("recent", R.string.main_albums_recent);
            }
            else if (view == albumsFrequentButton)
            {
                showAlbumList("frequent", R.string.main_albums_frequent);
            }
            else if (view == albumsStarredButton)
            {
                showAlbumList(Constants.STARRED, R.string.main_albums_starred);
            }
            else if (view == albumsAlphaByNameButton)
            {
                showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_alphaByName);
            }
            else if (view == albumsAlphaByArtistButton)
            {
                showAlbumList("alphabeticalByArtist", R.string.main_albums_alphaByArtist);
            }
            else if (view == songsStarredButton)
            {
                showStarredSongs();
            }
            else if (view == artistsButton)
            {
                showArtists();
            }
            else if (view == albumsButton)
            {
                showAlbumList(Constants.ALPHABETICAL_BY_NAME, R.string.main_albums_title);
            }
            else if (view == randomSongsButton)
            {
                showRandomSongs();
            }
            else if (view == genresButton)
            {
                showGenres();
            }
            else if (view == videosButton)
            {
                showVideos();
            }
        });
    }

    private String getActiveServerProperties()
    {
        ServerSetting currentSetting = activeServerProvider.getValue().getActiveServer();
        return String.format("%s;%s;%s;%s;%s;%s", currentSetting.getUrl(), currentSetting.getUserName(),
            currentSetting.getPassword(), currentSetting.getAllowSelfSignedCertificate(),
            currentSetting.getLdapSupport(), currentSetting.getMinimumApiVersion());
    }

    private void showStarredSongs()
    {
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.INTENT_EXTRA_NAME_STARRED, 1);
        Navigation.findNavController(getView()).navigate(R.id.mainToTrackCollection, bundle);
    }

    private void showRandomSongs()
    {
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.INTENT_EXTRA_NAME_RANDOM, 1);
        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, Util.getMaxSongs());
        Navigation.findNavController(getView()).navigate(R.id.mainToTrackCollection, bundle);
    }

    private void showArtists()
    {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, getContext().getResources().getString(R.string.main_artists_title));
        Navigation.findNavController(getView()).navigate(R.id.mainToArtistList, bundle);
    }

    private void showAlbumList(final String type, final int title) {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, title);
        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, Util.getMaxAlbums());
        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);
        Navigation.findNavController(getView()).navigate(R.id.mainToAlbumList, bundle);
    }

    private void showGenres()
    {
        Navigation.findNavController(getView()).navigate(R.id.mainToSelectGenre);
    }

    private void showVideos()
    {
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.INTENT_EXTRA_NAME_VIDEOS, 1);
        Navigation.findNavController(getView()).navigate(R.id.mainToTrackCollection, bundle);
    }

    private void showServers()
    {
        Navigation.findNavController(getView()).navigate(R.id.mainToServerSelector);
    }
}

