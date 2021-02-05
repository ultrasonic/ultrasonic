package org.moire.ultrasonic.fragment;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jetbrains.annotations.NotNull;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.Share;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.subsonic.DownloadHandler;
import org.moire.ultrasonic.subsonic.ImageLoaderProvider;
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker;
import org.moire.ultrasonic.subsonic.ShareHandler;
import org.moire.ultrasonic.subsonic.VideoPlayer;
import org.moire.ultrasonic.util.AlbumHeader;
import org.moire.ultrasonic.util.CancellationToken;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator;
import org.moire.ultrasonic.util.Pair;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.AlbumView;
import org.moire.ultrasonic.view.EntryAdapter;
import org.moire.ultrasonic.view.SongView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import kotlin.Lazy;
import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.inject;

public class SelectAlbumFragment extends Fragment {

    public static final String allSongsId = "-1";
    private SwipeRefreshLayout refreshAlbumListView;
    private ListView albumListView;
    private View header;
    private View albumButtons;
    private View emptyView;
    private ImageView selectButton;
    private ImageView playNowButton;
    private ImageView playNextButton;
    private ImageView playLastButton;
    private ImageView pinButton;
    private ImageView unpinButton;
    private ImageView downloadButton;
    private ImageView deleteButton;
    private ImageView moreButton;
    private boolean playAllButtonVisible;
    private boolean shareButtonVisible;
    private MenuItem playAllButton;
    private MenuItem shareButton;
    private boolean showHeader = true;
    private Random random = new java.security.SecureRandom();

    private final Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);
    private final Lazy<VideoPlayer> videoPlayer = inject(VideoPlayer.class);
    private final Lazy<DownloadHandler> downloadHandler = inject(DownloadHandler.class);
    private final Lazy<NetworkAndStorageChecker> networkAndStorageChecker = inject(NetworkAndStorageChecker.class);
    private final Lazy<ImageLoaderProvider> imageLoaderProvider = inject(ImageLoaderProvider.class);
    private final Lazy<ShareHandler> shareHandler = inject(ShareHandler.class);
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
        super.onViewCreated(view, savedInstanceState);
        cancellationToken = new CancellationToken();

        albumButtons = view.findViewById(R.id.menu_album);

        refreshAlbumListView = view.findViewById(R.id.select_album_entries_refresh);
        albumListView = view.findViewById(R.id.select_album_entries_list);

        refreshAlbumListView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh() {
                new SelectAlbumFragment.GetDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

        header = LayoutInflater.from(getContext()).inflate(R.layout.select_album_header, albumListView, false);

        albumListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        albumListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (position >= 0)
                {
                    MusicDirectory.Entry entry = (MusicDirectory.Entry) parent.getItemAtPosition(position);
                    if (entry != null && entry.isDirectory())
                    {
                        Bundle bundle = new Bundle();
                        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getId());
                        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, entry.isDirectory());
                        bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getTitle());
                        bundle.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.getParent());
                        Navigation.findNavController(view).navigate(R.id.selectAlbumFragment, bundle);
                    }
                    else if (entry != null && entry.isVideo())
                    {
                        videoPlayer.getValue().playVideo(entry);
                    }
                    else
                    {
                        enableButtons();
                    }
                }
            }
        });

        // TODO: Long click on an item will first try to maximize / collapse the item, even when it fits inside the TextView.
        // The context menu is only displayed on the second long click...
        albumListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof AlbumView) {
                    AlbumView albumView = (AlbumView) view;
                    if (!albumView.isMaximized()) {
                        albumView.maximizeOrMinimize();
                        return true;
                    } else {
                        return false;
                    }
                }
                if (view instanceof SongView) {
                    SongView songView = (SongView) view;
                    songView.maximizeOrMinimize();
                    return true;
                }
                return false;
            }
        });

        selectButton = view.findViewById(R.id.select_album_select);
        playNowButton = view.findViewById(R.id.select_album_play_now);
        playNextButton = view.findViewById(R.id.select_album_play_next);
        playLastButton = view.findViewById(R.id.select_album_play_last);
        pinButton = view.findViewById(R.id.select_album_pin);
        unpinButton = view.findViewById(R.id.select_album_unpin);
        downloadButton = view.findViewById(R.id.select_album_download);
        deleteButton = view.findViewById(R.id.select_album_delete);
        moreButton = view.findViewById(R.id.select_album_more);
        emptyView = view.findViewById(R.id.select_album_empty);

        selectButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                selectAllOrNone();
            }
        });
        playNowButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                playNow(false, false);
            }
        });
        playNextButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                downloadHandler.getValue().download(SelectAlbumFragment.this,true, false, false, true, false, getSelectedSongs(albumListView));
                selectAll(false, false);
            }
        });
        playLastButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                playNow(false, true);
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
        setHasOptionsMenu(true);
        enableButtons();

        String id = getArguments().getString(Constants.INTENT_EXTRA_NAME_ID);
        boolean isAlbum = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, false);
        String name = getArguments().getString(Constants.INTENT_EXTRA_NAME_NAME);
        String parentId = getArguments().getString(Constants.INTENT_EXTRA_NAME_PARENT_ID);
        String playlistId = getArguments().getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
        String podcastChannelId = getArguments().getString(Constants.INTENT_EXTRA_NAME_PODCAST_CHANNEL_ID);
        String playlistName = getArguments().getString(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
        String shareId = getArguments().getString(Constants.INTENT_EXTRA_NAME_SHARE_ID);
        String shareName = getArguments().getString(Constants.INTENT_EXTRA_NAME_SHARE_NAME);
        String albumListType = getArguments().getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
        String genreName = getArguments().getString(Constants.INTENT_EXTRA_NAME_GENRE_NAME);
        int albumListTitle = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, 0);
        int getStarredTracks = getArguments().getInt(Constants.INTENT_EXTRA_NAME_STARRED, 0);
        int getVideos = getArguments().getInt(Constants.INTENT_EXTRA_NAME_VIDEOS, 0);
        int getRandomTracks = getArguments().getInt(Constants.INTENT_EXTRA_NAME_RANDOM, 0);
        int albumListSize = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
        int albumListOffset = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);

        if (playlistId != null)
        {
            getPlaylist(playlistId, playlistName);
        }
        else if (podcastChannelId != null) {
            getPodcastEpisodes(podcastChannelId);
        }
        else if (shareId != null)
        {
            getShare(shareId, shareName);
        }
        else if (albumListType != null)
        {
            getAlbumList(albumListType, albumListTitle, albumListSize, albumListOffset);
        }
        else if (genreName != null)
        {
            getSongsForGenre(genreName, albumListSize, albumListOffset);
        }
        else if (getStarredTracks != 0)
        {
            getStarred();
        }
        else if (getVideos != 0)
        {
            getVideos();
        }
        else if (getRandomTracks != 0)
        {
            getRandom(albumListSize);
        }
        else
        {
            if (!ActiveServerProvider.Companion.isOffline(getActivity()) && Util.getShouldUseId3Tags(getActivity()))
            {
                if (isAlbum)
                {
                    getAlbum(id, name, parentId);
                }
                else
                {
                    getArtist(id, name);
                }
            }
            else
            {
                getMusicDirectory(id, name, parentId);
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(info.position);

        if (entry != null && entry.isDirectory())
        {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.select_album_context, menu);
        }

        shareButton = menu.findItem(R.id.menu_item_share);

        if (shareButton != null)
        {
            shareButton.setVisible(!ActiveServerProvider.Companion.isOffline(getContext()));
        }

        MenuItem downloadMenuItem = menu.findItem(R.id.album_menu_download);

        if (downloadMenuItem != null)
        {
            downloadMenuItem.setVisible(!ActiveServerProvider.Companion.isOffline(getContext()));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem)
    {
        Timber.d("onContextItemSelected");
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();

        if (info == null)
        {
            return true;
        }

        MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(info.position);

        if (entry == null)
        {
            return true;
        }

        String entryId = entry.getId();

        switch (menuItem.getItemId())
        {
            case R.id.album_menu_play_now:
                downloadHandler.getValue().downloadRecursively(this, entryId, false, false, true, false, false, false, false, false);
                break;
            case R.id.album_menu_play_next:
                downloadHandler.getValue().downloadRecursively(this, entryId, false, false, false, false, false, true, false, false);
                break;
            case R.id.album_menu_play_last:
                downloadHandler.getValue().downloadRecursively(this, entryId, false, true, false, false, false, false, false, false);
                break;
            case R.id.album_menu_pin:
                downloadHandler.getValue().downloadRecursively(this, entryId, true, true, false, false, false, false, false, false);
                break;
            case R.id.album_menu_unpin:
                downloadHandler.getValue().downloadRecursively(this, entryId, false, false, false, false, false, false, true, false);
                break;
            case R.id.album_menu_download:
                downloadHandler.getValue().downloadRecursively(this, entryId, false, false, false, false, true, false, false, false);
                break;
            case R.id.select_album_play_all:
                playAll();
                break;
            case R.id.menu_item_share:
                List<MusicDirectory.Entry> entries = new ArrayList<MusicDirectory.Entry>(1);
                entries.add(entry);
                shareHandler.getValue().createShare(this, entries, refreshAlbumListView, cancellationToken);
                return true;
            default:
                return super.onContextItemSelected(menuItem);
        }
        return true;
    }

    @Override
    public void onPrepareOptionsMenu(@NotNull Menu menu)
    {
        super.onPrepareOptionsMenu(menu);
        playAllButton = menu.findItem(R.id.select_album_play_all);

        if (playAllButton != null)
        {
            playAllButton.setVisible(playAllButtonVisible);
        }

        shareButton = menu.findItem(R.id.menu_item_share);

        if (shareButton != null)
        {
            shareButton.setVisible(shareButtonVisible);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.select_album, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.select_album_play_all:
                playAll();
                return true;
            case R.id.menu_item_share:
                shareHandler.getValue().createShare(this, getSelectedSongs(albumListView), refreshAlbumListView, cancellationToken);
                return true;
        }

        return false;
    }

    @Override
    public void onDestroyView() {
        cancellationToken.cancel();
        super.onDestroyView();
    }

    private void playNow(final boolean shuffle, final boolean append)
    {
        List<MusicDirectory.Entry> selectedSongs = getSelectedSongs(albumListView);

        if (!selectedSongs.isEmpty())
        {
            downloadHandler.getValue().download(this, append, false, !append, false, shuffle, selectedSongs);
            selectAll(false, false);
        }
        else
        {
            playAll(shuffle, append);
        }
    }

    private void playAll()
    {
        playAll(false, false);
    }

    private void playAll(final boolean shuffle, final boolean append)
    {
        boolean hasSubFolders = false;

        for (int i = 0; i < albumListView.getCount(); i++)
        {
            MusicDirectory.Entry entry = (MusicDirectory.Entry) albumListView.getItemAtPosition(i);
            if (entry != null && entry.isDirectory())
            {
                hasSubFolders = true;
                break;
            }
        }

        boolean isArtist = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, false);
        String id = getArguments().getString(Constants.INTENT_EXTRA_NAME_ID);

        if (hasSubFolders && id != null)
        {
            downloadHandler.getValue().downloadRecursively(this, id, false, append, !append, shuffle, false, false, false, isArtist);
        }
        else
        {
            selectAll(true, false);
            downloadHandler.getValue().download(this, append, false, !append, false, shuffle, getSelectedSongs(albumListView));
            selectAll(false, false);
        }
    }

    private static List<MusicDirectory.Entry> getSelectedSongs(ListView albumListView)
    {
        List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>(10);

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

    private void refresh()
    {
        // TODO: create better restart
        getView().post(new Runnable() {
            public void run() {
                Timber.d("Refresh called...");
                getArguments().putBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, true);
                onViewCreated(getView(), null);
            }
        });

        /*finish();
        Intent intent = getArguments();
        intent.putExtra(Constants.INTENT_EXTRA_NAME_REFRESH, true);
        startActivityForResultWithoutTransition(this, intent);*/
    }

    private void getMusicDirectory(final String id, final String name, final String parentId)
    {
        FragmentTitle.Companion.setTitle(this, name);
        //setActionBarSubtitle(name);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                MusicDirectory root = new MusicDirectory();

                if (allSongsId.equals(id))
                {
                    boolean refresh = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, false);
                    MusicDirectory musicDirectory = service.getMusicDirectory(parentId, name, refresh, getContext(), this);

                    List<MusicDirectory.Entry> songs = new LinkedList<MusicDirectory.Entry>();
                    getSongsRecursively(musicDirectory, songs);

                    for (MusicDirectory.Entry song : songs)
                    {
                        if (!song.isDirectory())
                        {
                            root.addChild(song);
                        }
                    }
                }
                else
                {
                    boolean refresh = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, false);
                    MusicDirectory musicDirectory = service.getMusicDirectory(id, name, refresh, getContext(), this);

                    if (Util.getShouldShowAllSongsByArtist(getContext()) && musicDirectory.findChild(allSongsId) == null && musicDirectory.getChildren(true, false).size() == musicDirectory.getChildren(true, true).size())
                    {
                        MusicDirectory.Entry allSongs = new MusicDirectory.Entry();

                        allSongs.setDirectory(true);
                        allSongs.setArtist(name);
                        allSongs.setParent(id);
                        allSongs.setId(allSongsId);
                        allSongs.setTitle(String.format(getResources().getString(R.string.select_album_all_songs), name));

                        root.addChild(allSongs);

                        List<MusicDirectory.Entry> children = musicDirectory.getChildren();

                        if (children != null)
                        {
                            root.addAll(children);
                        }
                    }
                    else
                    {
                        root = musicDirectory;
                    }
                }

                return root;
            }

            private void getSongsRecursively(MusicDirectory parent, List<MusicDirectory.Entry> songs) throws Exception
            {
                for (MusicDirectory.Entry song : parent.getChildren(false, true))
                {
                    if (!song.isVideo() && !song.isDirectory())
                    {
                        songs.add(song);
                    }
                }

                MusicService musicService = MusicServiceFactory.getMusicService(getContext());

                for (MusicDirectory.Entry dir : parent.getChildren(true, false))
                {
                    MusicDirectory root;

                    if (!allSongsId.equals(dir.getId()))
                    {
                        root = musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, getContext(), this);

                        getSongsRecursively(root, songs);
                    }
                }
            }
        }.execute();
    }

    private void getArtist(final String id, final String name)
    {
        FragmentTitle.Companion.setTitle(this, name);
        //setActionBarSubtitle(name);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                MusicDirectory root = new MusicDirectory();

                boolean refresh = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, false);
                MusicDirectory musicDirectory = service.getArtist(id, name, refresh, getContext(), this);

                if (Util.getShouldShowAllSongsByArtist(getContext()) && musicDirectory.findChild(allSongsId) == null && musicDirectory.getChildren(true, false).size() == musicDirectory.getChildren(true, true).size())
                {
                    MusicDirectory.Entry allSongs = new MusicDirectory.Entry();

                    allSongs.setDirectory(true);
                    allSongs.setArtist(name);
                    allSongs.setParent(id);
                    allSongs.setId(allSongsId);
                    allSongs.setTitle(String.format(getResources().getString(R.string.select_album_all_songs), name));

                    root.addFirst(allSongs);

                    List<MusicDirectory.Entry> children = musicDirectory.getChildren();

                    if (children != null)
                    {
                        root.addAll(children);
                    }
                }
                else
                {
                    root = musicDirectory;
                }

                return root;
            }
        }.execute();
    }

    private void getAlbum(final String id, final String name, final String parentId)
    {
        FragmentTitle.Companion.setTitle(this, name);
        //setActionBarSubtitle(name);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                MusicDirectory musicDirectory;

                boolean refresh = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, false);

                if (allSongsId.equals(id))
                {
                    MusicDirectory root = new MusicDirectory();

                    Collection<MusicDirectory.Entry> songs = new LinkedList<MusicDirectory.Entry>();
                    getSongsForArtist(parentId, songs);

                    for (MusicDirectory.Entry song : songs)
                    {
                        if (!song.isDirectory())
                        {
                            root.addChild(song);
                        }
                    }

                    musicDirectory = root;
                }
                else
                {
                    musicDirectory = service.getAlbum(id, name, refresh, getContext(), this);
                }

                return musicDirectory;
            }

            private void getSongsForArtist(String id, Collection<MusicDirectory.Entry> songs) throws Exception
            {
                MusicService musicService = MusicServiceFactory.getMusicService(getContext());
                MusicDirectory artist = musicService.getArtist(id, "", false, getContext(), this);

                for (MusicDirectory.Entry album : artist.getChildren())
                {
                    if (!allSongsId.equals(album.getId()))
                    {
                        MusicDirectory albumDirectory = musicService.getAlbum(album.getId(), "", false, getContext(), this);

                        for (MusicDirectory.Entry song : albumDirectory.getChildren())
                        {
                            if (!song.isVideo())
                            {
                                songs.add(song);
                            }
                        }
                    }
                }
            }
        }.execute();
    }

    private void getSongsForGenre(final String genre, final int count, final int offset)
    {
        FragmentTitle.Companion.setTitle(this, genre);
        //setActionBarSubtitle(genre);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                return service.getSongsByGenre(genre, count, offset, getContext(), this);
            }

            @Override
            protected void done(Pair<MusicDirectory, Boolean> result)
            {
                // Hide more button when results are less than album list size
                if (result.getFirst().getChildren().size() < getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0))
                {
                    moreButton.setVisibility(View.GONE);
                }
                else
                {
                    moreButton.setVisibility(View.VISIBLE);
                }

                moreButton.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        String genre = getArguments().getString(Constants.INTENT_EXTRA_NAME_GENRE_NAME);
                        int size = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
                        int offset = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0) + size;

                        Bundle bundle = new Bundle();
                        bundle.putString(Constants.INTENT_EXTRA_NAME_GENRE_NAME, genre);
                        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, size);
                        bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset);
                        Navigation.findNavController(getView()).navigate(R.id.selectAlbumFragment, bundle);
                    }
                });

                super.done(result);
            }
        }.execute();
    }

    private void getStarred()
    {
        FragmentTitle.Companion.setTitle(this, R.string.main_songs_starred);
        //setActionBarSubtitle(R.string.main_songs_starred);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                return Util.getShouldUseId3Tags(getContext()) ? Util.getSongsFromSearchResult(service.getStarred2(getContext(), this)) : Util.getSongsFromSearchResult(service.getStarred(getContext(), this));
            }
        }.execute();
    }

    private void getVideos()
    {
        showHeader = false;

        FragmentTitle.Companion.setTitle(this, R.string.main_videos);
        //setActionBarSubtitle(R.string.main_videos);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                boolean refresh = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_REFRESH, false);
                return service.getVideos(refresh, getContext(), this);
            }
        }.execute();
    }

    private void getRandom(final int size)
    {
        FragmentTitle.Companion.setTitle(this, R.string.main_songs_random);
        //setActionBarSubtitle(R.string.main_songs_random);

        new LoadTask()
        {
            @Override
            protected boolean sortableCollection() {
                return false;
            }

            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                return service.getRandomSongs(size, getContext(), this);
            }
        }.execute();
    }

    private void getPlaylist(final String playlistId, final String playlistName)
    {
        FragmentTitle.Companion.setTitle(this, playlistName);
        //setActionBarSubtitle(playlistName);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                return service.getPlaylist(playlistId, playlistName, getContext(), this);
            }
        }.execute();
    }

    private void getPodcastEpisodes(final String podcastChannelId)
    {
        // TODO: Not sure what the title should be for a podcast episode. Maybe a constant string should be used.
        //setActionBarSubtitle(playlistName);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                return service.getPodcastEpisodes(podcastChannelId, getContext(), this);
            }
        }.execute();
    }

    private void getShare(final String shareId, final CharSequence shareName)
    {
        FragmentTitle.Companion.setTitle(this, shareName);
        //setActionBarSubtitle(shareName);

        new LoadTask()
        {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                List<Share> shares = service.getShares(true, getContext(), this);

                MusicDirectory md = new MusicDirectory();

                for (Share share : shares)
                {
                    if (share.getId().equals(shareId))
                    {
                        for (MusicDirectory.Entry entry : share.getEntries())
                        {
                            md.addChild(entry);
                        }

                        break;
                    }
                }

                return md;
            }
        }.execute();
    }

    private void getAlbumList(final String albumListType, final int albumListTitle, final int size, final int offset)
    {
        showHeader = false;

        FragmentTitle.Companion.setTitle(this, albumListTitle);
        //setActionBarSubtitle(albumListTitle);

        new LoadTask()
        {
            @Override
            protected boolean sortableCollection() {
                return !albumListType.equals("newest") && !albumListType.equals("random") &&
                    !albumListType.equals("highest") && !albumListType.equals("recent") &&
                    !albumListType.equals("frequent");
            }

            @Override
            protected MusicDirectory load(MusicService service) throws Exception
            {
                return Util.getShouldUseId3Tags(getContext()) ? service.getAlbumList2(albumListType, size, offset, getContext(), this) : service.getAlbumList(albumListType, size, offset, getContext(), this);
            }

            @Override
            protected void done(Pair<MusicDirectory, Boolean> result)
            {
                if (!result.getFirst().getChildren().isEmpty())
                {
                    pinButton.setVisibility(View.GONE);
                    unpinButton.setVisibility(View.GONE);
                    downloadButton.setVisibility(View.GONE);
                    deleteButton.setVisibility(View.GONE);

                    // Hide more button when results are less than album list size
                    if (result.getFirst().getChildren().size() < getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0))
                    {
                        moreButton.setVisibility(View.GONE);
                    }
                    else
                    {
                        moreButton.setVisibility(View.VISIBLE);

                        moreButton.setOnClickListener(new View.OnClickListener()
                        {
                            @Override
                            public void onClick(View view)
                            {
                                int albumListTitle = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, 0);
                                String type = getArguments().getString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
                                int size = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
                                int offset = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0) + size;

                                Bundle bundle = new Bundle();
                                bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TITLE, albumListTitle);
                                bundle.putString(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
                                bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, size);
                                bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset);
                                Navigation.findNavController(getView()).navigate(R.id.selectAlbumFragment, bundle);
                            }
                        });
                    }
                }
                else
                {
                    moreButton.setVisibility(View.GONE);
                }

                super.done(result);
            }
        }.execute();
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
            Util.toast(getActivity(), getString(toastResId, selectedCount));
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
            DownloadFile downloadFile = mediaPlayerControllerLazy.getValue().getDownloadFileForSong(song);
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

        playNowButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        playNextButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        playLastButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        pinButton.setVisibility((enabled && !ActiveServerProvider.Companion.isOffline(getContext()) && selection.size() > pinnedCount) ? View.VISIBLE : View.GONE);
        unpinButton.setVisibility(enabled && unpinEnabled ? View.VISIBLE : View.GONE);
        downloadButton.setVisibility(enabled && !deleteEnabled && !ActiveServerProvider.Companion.isOffline(getContext()) ? View.VISIBLE : View.GONE);
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
                mediaPlayerControllerLazy.getValue().downloadBackground(songs, save);

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

        mediaPlayerControllerLazy.getValue().delete(songs);
    }

    private void unpin()
    {
        List<MusicDirectory.Entry> songs = getSelectedSongs(albumListView);
        Util.toast(getContext(), getResources().getQuantityString(R.plurals.select_album_n_songs_unpinned, songs.size(), songs.size()));
        mediaPlayerControllerLazy.getValue().unpin(songs);
    }

    private abstract class LoadTask extends TabActivityBackgroundTask<Pair<MusicDirectory, Boolean>>
    {

        public LoadTask()
        {
            super(SelectAlbumFragment.this.getActivity(), true, refreshAlbumListView, cancellationToken);
        }

        protected abstract MusicDirectory load(MusicService service) throws Exception;

        protected boolean sortableCollection() {
            return true;
        }

        @Override
        protected Pair<MusicDirectory, Boolean> doInBackground() throws Throwable
        {
            MusicService musicService = MusicServiceFactory.getMusicService(getContext());
            MusicDirectory dir = load(musicService);
            boolean valid = musicService.isLicenseValid(getContext(), this);
            return new Pair<MusicDirectory, Boolean>(dir, valid);
        }

        @Override
        protected void done(Pair<MusicDirectory, Boolean> result)
        {
            MusicDirectory musicDirectory = result.getFirst();
            List<MusicDirectory.Entry> entries = musicDirectory.getChildren();

            if (sortableCollection() && Util.getShouldSortByDisc(getContext()))
            {
                Collections.sort(entries, new EntryByDiscAndTrackComparator());
            }

            boolean allVideos = true;
            int songCount = 0;

            for (MusicDirectory.Entry entry : entries)
            {
                if (!entry.isVideo())
                {
                    allVideos = false;
                }

                if (!entry.isDirectory())
                {
                    songCount++;
                }
            }

            final int listSize = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);

            if (songCount > 0)
            {
                if (showHeader)
                {
                    String intentAlbumName = getArguments().getString(Constants.INTENT_EXTRA_NAME_NAME);
                    String directoryName = musicDirectory.getName();
                    View header = createHeader(entries, intentAlbumName != null ? intentAlbumName : directoryName, songCount);

                    if (header != null && albumListView.getHeaderViewsCount() == 0)
                    {
                        albumListView.addHeaderView(header, null, false);
                    }
                }

                pinButton.setVisibility(View.VISIBLE);
                unpinButton.setVisibility(View.VISIBLE);
                downloadButton.setVisibility(View.VISIBLE);
                deleteButton.setVisibility(View.VISIBLE);
                selectButton.setVisibility(allVideos ? View.GONE : View.VISIBLE);
                playNowButton.setVisibility(View.VISIBLE);
                playNextButton.setVisibility(View.VISIBLE);
                playLastButton.setVisibility(View.VISIBLE);

                if (listSize == 0 || songCount < listSize)
                {
                    moreButton.setVisibility(View.GONE);
                }
                else
                {
                    moreButton.setVisibility(View.VISIBLE);

                    if (getArguments().getInt(Constants.INTENT_EXTRA_NAME_RANDOM, 0) > 0)
                    {
                        moreButton.setOnClickListener(new View.OnClickListener()
                        {
                            @Override
                            public void onClick(View view)
                            {
                                int offset = getArguments().getInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0) + listSize;

                                Bundle bundle = new Bundle();
                                bundle.putInt(Constants.INTENT_EXTRA_NAME_RANDOM, 1);
                                bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, listSize);
                                bundle.putInt(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset);
                                Navigation.findNavController(getView()).navigate(R.id.selectAlbumFragment, bundle);
                            }
                        });
                    }
                }
            }
            else
            {
                pinButton.setVisibility(View.GONE);
                unpinButton.setVisibility(View.GONE);
                downloadButton.setVisibility(View.GONE);
                deleteButton.setVisibility(View.GONE);
                selectButton.setVisibility(View.GONE);
                playNowButton.setVisibility(View.GONE);
                playNextButton.setVisibility(View.GONE);
                playLastButton.setVisibility(View.GONE);

                if (listSize == 0 || result.getFirst().getChildren().size() < listSize)
                {
                    albumButtons.setVisibility(View.GONE);
                }
                else
                {
                    moreButton.setVisibility(View.VISIBLE);
                }
            }

            enableButtons();

            boolean isAlbumList = getArguments().containsKey(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
            playAllButtonVisible = !(isAlbumList || entries.isEmpty()) && !allVideos;
            shareButtonVisible = !ActiveServerProvider.Companion.isOffline(getContext()) && songCount > 0;

            emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

            if (playAllButton != null)
            {
                playAllButton.setVisible(playAllButtonVisible);
            }

            if (shareButton != null)
            {
                shareButton.setVisible(shareButtonVisible);
            }

            albumListView.setAdapter(new EntryAdapter(getContext(), imageLoaderProvider.getValue().getImageLoader(), entries, true));

            boolean playAll = getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);
            if (playAll && songCount > 0)
            {
                playAll(getArguments().getBoolean(Constants.INTENT_EXTRA_NAME_SHUFFLE, false), false);
            }
        }

        protected View createHeader(List<MusicDirectory.Entry> entries, CharSequence name, int songCount)
        {
            ImageView coverArtView = (ImageView) header.findViewById(R.id.select_album_art);
            int artworkSelection = random.nextInt(entries.size());
            imageLoaderProvider.getValue().getImageLoader().loadImage(coverArtView, entries.get(artworkSelection), false, Util.getAlbumImageSize(getContext()), false, true);

            AlbumHeader albumHeader = AlbumHeader.processEntries(getContext(), entries);

            TextView titleView = (TextView) header.findViewById(R.id.select_album_title);
            titleView.setText(name != null ? name : FragmentTitle.Companion.getTitle(SelectAlbumFragment.this)); //getActionBarSubtitle());

            // Don't show a header if all entries are videos
            if (albumHeader.getIsAllVideo())
            {
                return null;
            }

            TextView artistView = header.findViewById(R.id.select_album_artist);
            String artist;

            artist = albumHeader.getArtists().size() == 1 ? albumHeader.getArtists().iterator().next() : albumHeader.getGrandParents().size() == 1 ? albumHeader.getGrandParents().iterator().next() : getResources().getString(R.string.common_various_artists);

            artistView.setText(artist);

            TextView genreView = header.findViewById(R.id.select_album_genre);
            String genre;

            genre = albumHeader.getGenres().size() == 1 ? albumHeader.getGenres().iterator().next() : getResources().getString(R.string.common_multiple_genres);

            genreView.setText(genre);

            TextView yearView = header.findViewById(R.id.select_album_year);
            String year;

            year = albumHeader.getYears().size() == 1 ? albumHeader.getYears().iterator().next().toString() : getResources().getString(R.string.common_multiple_years);

            yearView.setText(year);

            TextView songCountView = header.findViewById(R.id.select_album_song_count);
            String songs = getResources().getQuantityString(R.plurals.select_album_n_songs, songCount, songCount);
            songCountView.setText(songs);

            String duration = Util.formatTotalDuration(albumHeader.getTotalDuration());

            TextView durationView = header.findViewById(R.id.select_album_duration);
            durationView.setText(duration);

            return header;
        }
    }

    private class GetDataTask extends AsyncTask<Void, Void, String[]>
    {
        @Override
        protected void onPostExecute(String[] result)
        {
            super.onPostExecute(result);
        }

        @Override
        protected String[] doInBackground(Void... params)
        {
            refresh();
            return null;
        }
    }
}
