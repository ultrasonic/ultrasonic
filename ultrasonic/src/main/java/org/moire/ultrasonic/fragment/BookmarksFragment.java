package org.moire.ultrasonic.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.subsonic.ImageLoaderProvider;
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker;
import org.moire.ultrasonic.subsonic.VideoPlayer;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Pair;
import org.moire.ultrasonic.util.FragmentBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.EntryAdapter;

import java.util.ArrayList;
import java.util.List;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * Lists the Bookmarks available on the server
 */
public class BookmarksFragment extends Fragment {

    private SwipeRefreshLayout refreshAlbumListView;
    private ListView albumListView;
    private View albumButtons;
    private View emptyView;
    private ImageView playNowButton;
    private ImageView pinButton;
    private ImageView unpinButton;
    private ImageView downloadButton;
    private ImageView deleteButton;

    private final Lazy<VideoPlayer> videoPlayer = inject(VideoPlayer.class);
    private final Lazy<MediaPlayerController> mediaPlayerController = inject(MediaPlayerController.class);
    private final Lazy<ImageLoaderProvider> imageLoader = inject(ImageLoaderProvider.class);
    private final Lazy<NetworkAndStorageChecker> networkAndStorageChecker = inject(NetworkAndStorageChecker.class);
    private CancellationToken cancellationToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.select_album, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cancellationToken = new CancellationToken();
        albumButtons = view.findViewById(R.id.menu_album);
        super.onViewCreated(view, savedInstanceState);

        refreshAlbumListView = view.findViewById(R.id.select_album_entries_refresh);
        albumListView = view.findViewById(R.id.select_album_entries_list);

        refreshAlbumListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                enableButtons();
                getBookmarks();
            }
        });

        albumListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        albumListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (position >= 0)
                {
                    MusicDirectory.Entry entry = (MusicDirectory.Entry) parent.getItemAtPosition(position);

                    if (entry != null)
                    {
                        if (entry.isVideo())
                        {
                            videoPlayer.getValue().playVideo(getContext(), entry);
                        }
                        else
                        {
                            enableButtons();
                        }
                    }
                }
            }
        });

        ImageView selectButton = view.findViewById(R.id.select_album_select);
        playNowButton = view.findViewById(R.id.select_album_play_now);
        ImageView playNextButton = view.findViewById(R.id.select_album_play_next);
        ImageView playLastButton = view.findViewById(R.id.select_album_play_last);
        pinButton = view.findViewById(R.id.select_album_pin);
        unpinButton = view.findViewById(R.id.select_album_unpin);
        downloadButton = view.findViewById(R.id.select_album_download);
        deleteButton = view.findViewById(R.id.select_album_delete);
        ImageView oreButton = view.findViewById(R.id.select_album_more);
        emptyView = view.findViewById(R.id.select_album_empty);

        selectButton.setVisibility(View.GONE);
        playNextButton.setVisibility(View.GONE);
        playLastButton.setVisibility(View.GONE);
        oreButton.setVisibility(View.GONE);

        playNowButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                playNow(getSelectedSongs(albumListView));
            }
        });

        selectButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                selectAllOrNone();
            }
        });
        pinButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                downloadBackground(true);
                selectAll(false, false);
            }
        });
        unpinButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                unpin();
                selectAll(false, false);
            }
        });
        downloadButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                downloadBackground(false);
                selectAll(false, false);
            }
        });
        deleteButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                delete();
                selectAll(false, false);
            }
        });

        registerForContextMenu(albumListView);
        FragmentTitle.Companion.setTitle(this, R.string.button_bar_bookmarks);

        enableButtons();
        getBookmarks();
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void getBookmarks()
    {
        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                return Util.getSongsFromBookmarks(service.getBookmarks());
            }
        }.execute();
    }

    private void playNow(List<MusicDirectory.Entry> songs)
    {
        if (!getSelectedSongs(albumListView).isEmpty())
        {
            int position = songs.get(0).getBookmarkPosition();
            mediaPlayerController.getValue().restore(songs, 0, position, true, true);
            selectAll(false, false);
        }
    }

    private static List<MusicDirectory.Entry> getSelectedSongs(ListView albumListView)
    {
        List<MusicDirectory.Entry> songs = new ArrayList<>(10);

        if (albumListView != null)
        {
            int count = albumListView.getCount();
            for (int i = 0; i < count; i++)
            {
                if (albumListView.isItemChecked(i))
                {
                    songs.add((MusicDirectory.Entry) albumListView.getItemAtPosition(i));
                }
            }
        }

        return songs;
    }

    private void selectAllOrNone()
    {
        boolean someUnselected = false;
        int count = albumListView.getCount();

        for (int i = 0; i < count; i++)
        {
            if (!albumListView.isItemChecked(i) && albumListView.getItemAtPosition(i) instanceof MusicDirectory.Entry)
            {
                someUnselected = true;
                break;
            }
        }

        selectAll(someUnselected, true);
    }

    private void selectAll(boolean selected, boolean toast)
    {
        int count = albumListView.getCount();
        int selectedCount = 0;

        for (int i = 0; i < count; i++)
        {
            MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(i);
            if (entry != null && !entry.isDirectory() && !entry.isVideo())
            {
                albumListView.setItemChecked(i, selected);
                selectedCount++;
            }
        }

        // Display toast: N tracks selected / N tracks unselected
        if (toast)
        {
            int toastResId = selected ? R.string.select_album_n_selected : R.string.select_album_n_unselected;
            Util.toast(getContext(), getString(toastResId, selectedCount));
        }

        enableButtons();
    }

    private void enableButtons()
    {
        List<MusicDirectory.Entry> selection = getSelectedSongs(albumListView);
        boolean enabled = !selection.isEmpty();
        boolean unpinEnabled = false;
        boolean deleteEnabled = false;

        int pinnedCount = 0;

        for (MusicDirectory.Entry song : selection)
        {
            DownloadFile downloadFile = mediaPlayerController.getValue().getDownloadFileForSong(song);
            if (downloadFile.isWorkDone())
            {
                deleteEnabled = true;
            }

            if (downloadFile.isSaved())
            {
                pinnedCount++;
                unpinEnabled = true;
            }
        }

        playNowButton.setVisibility(enabled && deleteEnabled ? View.VISIBLE : View.GONE);
        pinButton.setVisibility((enabled && !ActiveServerProvider.Companion.isOffline() && selection.size() > pinnedCount) ? View.VISIBLE : View.GONE);
        unpinButton.setVisibility(enabled && unpinEnabled ? View.VISIBLE : View.GONE);
        downloadButton.setVisibility(enabled && !deleteEnabled && !ActiveServerProvider.Companion.isOffline() ? View.VISIBLE : View.GONE);
        deleteButton.setVisibility(enabled && deleteEnabled ? View.VISIBLE : View.GONE);
    }

    private void downloadBackground(final boolean save)
    {
        List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);

        if (songs.isEmpty())
        {
            selectAll(true, false);
            songs = getSelectedSongs(albumListView);
        }

        downloadBackground(save, songs);
    }

    private void downloadBackground(final boolean save, final List<MusicDirectory.Entry> songs)
    {
        Runnable onValid = new Runnable()
        {
            @Override
            public void run()
            {
                networkAndStorageChecker.getValue().warnIfNetworkOrStorageUnavailable();
                mediaPlayerController.getValue().downloadBackground(songs, save);

                if (save)
                {
                    Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_pinned, songs.size(), songs.size()));
                }
                else
                {
                    Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_downloaded, songs.size(), songs.size()));
                }
            }
        };

        onValid.run();
    }

    private void delete()
    {
        List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);

        if (songs.isEmpty())
        {
            selectAll(true, false);
            songs = getSelectedSongs(albumListView);
        }

        mediaPlayerController.getValue().delete(songs);
    }

    private void unpin()
    {
        List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);
        Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_unpinned, songs.size(), songs.size()));
        mediaPlayerController.getValue().unpin(songs);
    }

    private abstract class LoadTask extends FragmentBackgroundTask<Pair<MusicDirectory, Boolean>>
    {
        public LoadTask()
        {
            super(BookmarksFragment.this.getActivity(), true, refreshAlbumListView, cancellationToken);
        }

        protected abstract MusicDirectory load(MusicService service) throws Exception;

        @Override
        protected Pair<MusicDirectory, Boolean> doInBackground() throws Throwable
        {
            MusicService musicService = MusicServiceFactory.getMusicService();
            MusicDirectory dir = load(musicService);
            boolean valid = musicService.isLicenseValid();
            return new Pair<>(dir, valid);
        }

        @Override
        protected void done(Pair<MusicDirectory, Boolean> result)
        {
            MusicDirectory musicDirectory = result.getFirst();
            List<MusicDirectory.Entry> entries = musicDirectory.getChildren();

            int songCount = 0;
            for (MusicDirectory.Entry entry : entries)
            {
                if (!entry.isDirectory())
                {
                    songCount++;
                }
            }

            final int listSize = getArguments() == null? 0 : getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);

            if (songCount > 0)
            {
                pinButton.setVisibility(View.VISIBLE);
                unpinButton.setVisibility(View.VISIBLE);
                downloadButton.setVisibility(View.VISIBLE);
                deleteButton.setVisibility(View.VISIBLE);
                playNowButton.setVisibility(View.VISIBLE);
            }
            else
            {
                pinButton.setVisibility(View.GONE);
                unpinButton.setVisibility(View.GONE);
                downloadButton.setVisibility(View.GONE);
                deleteButton.setVisibility(View.GONE);
                playNowButton.setVisibility(View.GONE);

                if (listSize == 0 || result.getFirst().getChildren().size() < listSize)
                {
                    albumButtons.setVisibility(View.GONE);
                }
            }

            enableButtons();

            emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

            albumListView.setAdapter(new EntryAdapter(getContext(), imageLoader.getValue().getImageLoader(), entries, true));
        }
    }
}
