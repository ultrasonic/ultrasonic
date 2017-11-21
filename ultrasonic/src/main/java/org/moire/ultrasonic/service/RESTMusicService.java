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
package org.moire.ultrasonic.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient;
import org.moire.ultrasonic.api.subsonic.models.AlbumListType;
import org.moire.ultrasonic.api.subsonic.models.JukeboxAction;
import org.moire.ultrasonic.api.subsonic.models.MusicDirectoryChild;
import org.moire.ultrasonic.api.subsonic.response.BookmarksResponse;
import org.moire.ultrasonic.api.subsonic.response.ChatMessagesResponse;
import org.moire.ultrasonic.api.subsonic.response.GenresResponse;
import org.moire.ultrasonic.api.subsonic.response.GetAlbumList2Response;
import org.moire.ultrasonic.api.subsonic.response.GetAlbumListResponse;
import org.moire.ultrasonic.api.subsonic.response.GetAlbumResponse;
import org.moire.ultrasonic.api.subsonic.response.GetArtistResponse;
import org.moire.ultrasonic.api.subsonic.response.GetArtistsResponse;
import org.moire.ultrasonic.api.subsonic.response.GetIndexesResponse;
import org.moire.ultrasonic.api.subsonic.response.GetLyricsResponse;
import org.moire.ultrasonic.api.subsonic.response.GetMusicDirectoryResponse;
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistResponse;
import org.moire.ultrasonic.api.subsonic.response.GetPlaylistsResponse;
import org.moire.ultrasonic.api.subsonic.response.GetPodcastsResponse;
import org.moire.ultrasonic.api.subsonic.response.GetRandomSongsResponse;
import org.moire.ultrasonic.api.subsonic.response.GetSongsByGenreResponse;
import org.moire.ultrasonic.api.subsonic.response.GetStarredResponse;
import org.moire.ultrasonic.api.subsonic.response.GetStarredTwoResponse;
import org.moire.ultrasonic.api.subsonic.response.GetUserResponse;
import org.moire.ultrasonic.api.subsonic.response.JukeboxResponse;
import org.moire.ultrasonic.api.subsonic.response.LicenseResponse;
import org.moire.ultrasonic.api.subsonic.response.MusicFoldersResponse;
import org.moire.ultrasonic.api.subsonic.response.SearchResponse;
import org.moire.ultrasonic.api.subsonic.response.SearchThreeResponse;
import org.moire.ultrasonic.api.subsonic.response.SearchTwoResponse;
import org.moire.ultrasonic.api.subsonic.response.SharesResponse;
import org.moire.ultrasonic.api.subsonic.response.StreamResponse;
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse;
import org.moire.ultrasonic.api.subsonic.response.VideosResponse;
import org.moire.ultrasonic.data.APIAlbumConverter;
import org.moire.ultrasonic.data.APIArtistConverter;
import org.moire.ultrasonic.data.APIBookmarkConverter;
import org.moire.ultrasonic.data.APIChatMessageConverter;
import org.moire.ultrasonic.data.APIIndexesConverter;
import org.moire.ultrasonic.data.APIJukeboxConverter;
import org.moire.ultrasonic.data.APILyricsConverter;
import org.moire.ultrasonic.data.APIMusicDirectoryConverter;
import org.moire.ultrasonic.data.APIMusicFolderConverter;
import org.moire.ultrasonic.data.APIPlaylistConverter;
import org.moire.ultrasonic.data.APIPodcastConverter;
import org.moire.ultrasonic.data.APISearchConverter;
import org.moire.ultrasonic.data.APIShareConverter;
import org.moire.ultrasonic.data.APIUserConverter;
import org.moire.ultrasonic.data.ApiGenreConverter;
import org.moire.ultrasonic.domain.Bookmark;
import org.moire.ultrasonic.domain.ChatMessage;
import org.moire.ultrasonic.domain.Genre;
import org.moire.ultrasonic.domain.Indexes;
import org.moire.ultrasonic.domain.JukeboxStatus;
import org.moire.ultrasonic.domain.Lyrics;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.MusicFolder;
import org.moire.ultrasonic.domain.Playlist;
import org.moire.ultrasonic.domain.PodcastsChannel;
import org.moire.ultrasonic.domain.SearchCriteria;
import org.moire.ultrasonic.domain.SearchResult;
import org.moire.ultrasonic.domain.Share;
import org.moire.ultrasonic.domain.UserInfo;
import org.moire.ultrasonic.service.parser.SubsonicRESTException;
import org.moire.ultrasonic.util.CancellableTask;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.ProgressListener;
import org.moire.ultrasonic.util.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import kotlin.Pair;
import retrofit2.Response;

/**
 * @author Sindre Mehus
 */
public class RESTMusicService implements MusicService {
    private static final String TAG = RESTMusicService.class.getSimpleName();

    private final SubsonicAPIClient subsonicAPIClient;

    public RESTMusicService(SubsonicAPIClient subsonicAPIClient) {
        this.subsonicAPIClient = subsonicAPIClient;
    }

    @Override
    public void ping(Context context, ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.service_connecting);

        final Response<SubsonicResponse> response = subsonicAPIClient.getApi().ping().execute();
        checkResponseSuccessful(response);
    }

    @Override
    public boolean isLicenseValid(Context context, ProgressListener progressListener)
            throws Exception {
        updateProgressListener(progressListener, R.string.service_connecting);

        final Response<LicenseResponse> response = subsonicAPIClient.getApi().getLicense().execute();

        checkResponseSuccessful(response);
        return response.body().getLicense().getValid();
    }

    @Override
    public List<MusicFolder> getMusicFolders(boolean refresh,
                                             Context context,
                                             ProgressListener progressListener) throws Exception {
        List<MusicFolder> cachedMusicFolders = readCachedMusicFolders(context);
        if (cachedMusicFolders != null && !refresh) {
            return cachedMusicFolders;
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<MusicFoldersResponse> response = subsonicAPIClient.getApi().getMusicFolders().execute();
        checkResponseSuccessful(response);

        List<MusicFolder> musicFolders = APIMusicFolderConverter
                .toDomainEntityList(response.body().getMusicFolders());
        writeCachedMusicFolders(context, musicFolders);
        return musicFolders;
    }

    private static List<MusicFolder> readCachedMusicFolders(Context context) {
        String filename = getCachedMusicFoldersFilename(context);
        return FileUtil.deserialize(context, filename);
    }

    private static void writeCachedMusicFolders(Context context, List<MusicFolder> musicFolders) {
        String filename = getCachedMusicFoldersFilename(context);
        FileUtil.serialize(context, new ArrayList<>(musicFolders), filename);
    }

    private static String getCachedMusicFoldersFilename(Context context) {
        String s = Util.getRestUrl(context, null);
        return String.format(Locale.US, "musicFolders-%d.ser", Math.abs(s.hashCode()));
    }

    @Override
    public Indexes getIndexes(String musicFolderId,
                              boolean refresh,
                              Context context,
                              ProgressListener progressListener) throws Exception {
        Indexes cachedIndexes = readCachedIndexes(context, musicFolderId);
        if (cachedIndexes != null && !refresh) {
            return cachedIndexes;
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetIndexesResponse> response = subsonicAPIClient.getApi()
                .getIndexes(musicFolderId == null ? null : Long.valueOf(musicFolderId), null).execute();
        checkResponseSuccessful(response);

        Indexes indexes = APIIndexesConverter.toDomainEntity(response.body().getIndexes());
        writeCachedIndexes(context, indexes, musicFolderId);
        return indexes;
    }

    private static Indexes readCachedIndexes(Context context, String musicFolderId) {
        String filename = getCachedIndexesFilename(context, musicFolderId);
        return FileUtil.deserialize(context, filename);
    }

    private static void writeCachedIndexes(Context context, Indexes indexes, String musicFolderId) {
        String filename = getCachedIndexesFilename(context, musicFolderId);
        FileUtil.serialize(context, indexes, filename);
    }

    private static String getCachedIndexesFilename(Context context, String musicFolderId) {
        String s = Util.getRestUrl(context, null) + musicFolderId;
        return String.format(Locale.US, "indexes-%d.ser", Math.abs(s.hashCode()));
    }

    @Override
    public Indexes getArtists(boolean refresh,
                              Context context,
                              ProgressListener progressListener) throws Exception {
        Indexes cachedArtists = readCachedArtists(context);
        if (cachedArtists != null &&
                !refresh) {
            return cachedArtists;
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetArtistsResponse> response = subsonicAPIClient.getApi().getArtists(null).execute();
        checkResponseSuccessful(response);

        Indexes indexes = APIIndexesConverter.toDomainEntity(response.body().getIndexes());
        writeCachedArtists(context, indexes);
        return indexes;
    }

    private static Indexes readCachedArtists(Context context) {
        String filename = getCachedArtistsFilename(context);
        return FileUtil.deserialize(context, filename);
    }

    private static void writeCachedArtists(Context context, Indexes artists) {
        String filename = getCachedArtistsFilename(context);
        FileUtil.serialize(context, artists, filename);
    }

    private static String getCachedArtistsFilename(Context context) {
        String s = Util.getRestUrl(context, null);
        return String.format(Locale.US, "indexes-%d.ser", Math.abs(s.hashCode()));
    }

    @Override
    public void star(String id,
                     String albumId,
                     String artistId,
                     Context context,
                     ProgressListener progressListener) throws Exception {
        Long apiId = id == null ? null : Long.valueOf(id);
        Long apiAlbumId = albumId == null ? null : Long.valueOf(albumId);
        Long apiArtistId = artistId == null ? null : Long.valueOf(artistId);

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .star(apiId, apiAlbumId, apiArtistId).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public void unstar(String id,
                       String albumId,
                       String artistId,
                       Context context,
                       ProgressListener progressListener) throws Exception {
        Long apiId = id == null ? null : Long.valueOf(id);
        Long apiAlbumId = albumId == null ? null : Long.valueOf(albumId);
        Long apiArtistId = artistId == null ? null : Long.valueOf(artistId);

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .unstar(apiId, apiAlbumId, apiArtistId).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public MusicDirectory getMusicDirectory(String id,
                                            String name,
                                            boolean refresh,
                                            Context context,
                                            ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Id should not be null!");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetMusicDirectoryResponse> response = subsonicAPIClient.getApi()
                .getMusicDirectory(Long.valueOf(id)).execute();
        checkResponseSuccessful(response);

        return APIMusicDirectoryConverter.toDomainEntity(response.body().getMusicDirectory());
    }

    @Override
    public MusicDirectory getArtist(String id,
                                    String name,
                                    boolean refresh,
                                    Context context,
                                    ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Id can't be null!");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetArtistResponse> response = subsonicAPIClient.getApi()
                .getArtist(Long.valueOf(id)).execute();
        checkResponseSuccessful(response);

        return APIArtistConverter.toMusicDirectoryDomainEntity(response.body().getArtist());
    }

    @Override
    public MusicDirectory getAlbum(String id,
                                   String name,
                                   boolean refresh,
                                   Context context,
                                   ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Id argument is null!");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetAlbumResponse> response = subsonicAPIClient.getApi()
                .getAlbum(Long.valueOf(id)).execute();
        checkResponseSuccessful(response);

        return APIAlbumConverter.toMusicDirectoryDomainEntity(response.body().getAlbum());
    }

    @Override
    public SearchResult search(SearchCriteria criteria,
                               Context context,
                               ProgressListener progressListener) throws Exception {
        try {
            return !Util.isOffline(context) &&
                    Util.getShouldUseId3Tags(context) ?
                    search3(criteria, context, progressListener) :
                    search2(criteria, context, progressListener);
        } catch (ServerTooOldException x) {
            // Ensure backward compatibility with REST 1.3.
            return searchOld(criteria, context, progressListener);
        }
    }

    /**
     * Search using the "search" REST method.
     */
    private SearchResult searchOld(SearchCriteria criteria,
                                   Context context,
                                   ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SearchResponse> response = subsonicAPIClient.getApi().search(null, null, null, criteria.getQuery(),
                criteria.getSongCount(), null, null).execute();
        checkResponseSuccessful(response);

        return APISearchConverter.toDomainEntity(response.body().getSearchResult());
    }

    /**
     * Search using the "search2" REST method, available in 1.4.0 and later.
     */
    private SearchResult search2(SearchCriteria criteria,
                                 Context context,
                                 ProgressListener progressListener) throws Exception {
        if (criteria.getQuery() == null) {
            throw new IllegalArgumentException("Query param is null");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SearchTwoResponse> response = subsonicAPIClient.getApi().search2(criteria.getQuery(),
                criteria.getArtistCount(), null, criteria.getAlbumCount(), null,
                criteria.getSongCount(), null).execute();
        checkResponseSuccessful(response);

        return APISearchConverter.toDomainEntity(response.body().getSearchResult());
    }

    private SearchResult search3(SearchCriteria criteria,
                                 Context context,
                                 ProgressListener progressListener) throws Exception {
        if (criteria.getQuery() == null) {
            throw new IllegalArgumentException("Query param is null");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SearchThreeResponse> response = subsonicAPIClient.getApi().search3(criteria.getQuery(),
                criteria.getArtistCount(), null, criteria.getAlbumCount(), null,
                criteria.getSongCount(), null).execute();
        checkResponseSuccessful(response);

        return APISearchConverter.toDomainEntity(response.body().getSearchResult());
    }

    @Override
    public MusicDirectory getPlaylist(String id,
                                      String name,
                                      Context context,
                                      ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("id param is null!");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetPlaylistResponse> response = subsonicAPIClient.getApi()
                .getPlaylist(Long.valueOf(id)).execute();
        checkResponseSuccessful(response);

        MusicDirectory playlist = APIPlaylistConverter
                .toMusicDirectoryDomainEntity(response.body().getPlaylist());
        savePlaylist(name, context, playlist);
        return playlist;
    }

    private void savePlaylist(String name,
                              Context context,
                              MusicDirectory playlist) throws IOException {
        File playlistFile = FileUtil.getPlaylistFile(Util.getServerName(context), name);
        FileWriter fw = new FileWriter(playlistFile);
        BufferedWriter bw = new BufferedWriter(fw);
        try {
            fw.write("#EXTM3U\n");
            for (MusicDirectory.Entry e : playlist.getChildren()) {
                String filePath = FileUtil.getSongFile(context, e).getAbsolutePath();
                if (!new File(filePath).exists()) {
                    String ext = FileUtil.getExtension(filePath);
                    String base = FileUtil.getBaseName(filePath);
                    filePath = base + ".complete." + ext;
                }
                fw.write(filePath + '\n');
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to save playlist: " + name);
            throw e;
        } finally {
            bw.close();
            fw.close();
        }
    }

    @Override
    public List<Playlist> getPlaylists(boolean refresh,
                                       Context context,
                                       ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetPlaylistsResponse> response = subsonicAPIClient.getApi()
                .getPlaylists(null).execute();
        checkResponseSuccessful(response);

        return APIPlaylistConverter.toDomainEntitiesList(response.body().getPlaylists());
    }

    @Override
    public void createPlaylist(String id,
                               String name,
                               List<MusicDirectory.Entry> entries,
                               Context context,
                               ProgressListener progressListener) throws Exception {
        Long pId = id == null ? null : Long.valueOf(id);
        List<Long> pSongIds = new ArrayList<>(entries.size());
        for (MusicDirectory.Entry entry : entries) {
            if (entry.getId() != null) {
                pSongIds.add(Long.valueOf(entry.getId()));
            }
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .createPlaylist(pId, name, pSongIds).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public void deletePlaylist(String id,
                               Context context,
                               ProgressListener progressListener) throws Exception {
        Long pId = id == null ? null : Long.valueOf(id);

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .deletePlaylist(pId).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public void updatePlaylist(String id,
                               String name,
                               String comment,
                               boolean pub,
                               Context context,
                               ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .updatePlaylist(Long.valueOf(id), name, comment, pub, null, null).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public List<PodcastsChannel> getPodcastsChannels(boolean refresh,
                                                     Context context,
                                                     ProgressListener progressListener)
            throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetPodcastsResponse> response = subsonicAPIClient.getApi()
                .getPodcasts(false, null).execute();
        checkResponseSuccessful(response);

        return APIPodcastConverter.toDomainEntitiesList(response.body().getPodcastChannels());
    }

    @Override
    public MusicDirectory getPodcastEpisodes(String podcastChannelId,
                                             Context context,
                                             ProgressListener progressListener) throws Exception {
        if (podcastChannelId == null) {
            throw new IllegalArgumentException("Podcast channel id is null!");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetPodcastsResponse> response = subsonicAPIClient.getApi()
                .getPodcasts(true, Long.valueOf(podcastChannelId)).execute();
        checkResponseSuccessful(response);

        List<MusicDirectoryChild> podcastEntries = response.body().getPodcastChannels().get(0)
                .getEpisodeList();
        MusicDirectory musicDirectory = new MusicDirectory();
        for (MusicDirectoryChild podcastEntry : podcastEntries) {
            if (!"skipped".equals(podcastEntry.getStatus()) &&
                    !"error".equals(podcastEntry.getStatus())) {
                MusicDirectory.Entry entry = APIMusicDirectoryConverter.toDomainEntity(podcastEntry);
                entry.setTrack(null);
                musicDirectory.addChild(entry);
            }
        }
        return musicDirectory;
    }

    @Override
    public Lyrics getLyrics(String artist,
                            String title,
                            Context context,
                            ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetLyricsResponse> response = subsonicAPIClient.getApi()
                .getLyrics(artist, title).execute();
        checkResponseSuccessful(response);

        return APILyricsConverter.toDomainEntity(response.body().getLyrics());
    }

    @Override
    public void scrobble(String id,
                         boolean submission,
                         Context context,
                         ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Scrobble id is null");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .scrobble(id, null, submission).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public MusicDirectory getAlbumList(String type,
                                       int size,
                                       int offset,
                                       Context context,
                                       ProgressListener progressListener) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("Type is null!");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetAlbumListResponse> response = subsonicAPIClient.getApi()
                .getAlbumList(AlbumListType.fromName(type), size, offset, null,
                        null, null, null).execute();
        checkResponseSuccessful(response);

        List<MusicDirectory.Entry> childList = APIMusicDirectoryConverter
                .toDomainEntityList(response.body().getAlbumList());
        MusicDirectory result = new MusicDirectory();
        result.addAll(childList);
        return result;
    }

    @Override
    public MusicDirectory getAlbumList2(String type,
                                        int size,
                                        int offset,
                                        Context context,
                                        ProgressListener progressListener) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("Type is null!");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetAlbumList2Response> response = subsonicAPIClient.getApi()
                .getAlbumList2(AlbumListType.fromName(type), size, offset, null, null,
                        null, null).execute();
        checkResponseSuccessful(response);

        MusicDirectory result = new MusicDirectory();
        result.addAll(APIAlbumConverter.toDomainEntityList(response.body().getAlbumList()));
        return result;
    }

    @Override
    public MusicDirectory getRandomSongs(int size,
                                         Context context,
                                         ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetRandomSongsResponse> response = subsonicAPIClient.getApi()
                .getRandomSongs(size, null, null, null, null).execute();
        checkResponseSuccessful(response);

        MusicDirectory result = new MusicDirectory();
        result.addAll(APIMusicDirectoryConverter.toDomainEntityList(response.body().getSongsList()));
        return result;
    }

    @Override
    public SearchResult getStarred(Context context,
                                   ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetStarredResponse> response = subsonicAPIClient.getApi()
                .getStarred(null).execute();
        checkResponseSuccessful(response);

        return APISearchConverter.toDomainEntity(response.body().getStarred());
    }

    @Override
    public SearchResult getStarred2(Context context,
                                    ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetStarredTwoResponse> response = subsonicAPIClient.getApi()
                .getStarred2(null).execute();
        checkResponseSuccessful(response);

        return APISearchConverter.toDomainEntity(response.body().getStarred2());
    }

    @Override
    public Bitmap getCoverArt(Context context,
                              final MusicDirectory.Entry entry,
                              int size,
                              boolean saveToFile,
                              boolean highQuality,
                              ProgressListener progressListener) throws Exception {
        // Synchronize on the entry so that we don't download concurrently for
        // the same song.
        if (entry == null) {
            return null;
        }

        synchronized (entry) {
            // Use cached file, if existing.
            Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, entry, size, highQuality);
            boolean serverScaling = Util.isServerScalingEnabled(context);

            if (bitmap == null) {
                Log.d(TAG, "Loading cover art for: " + entry);

                final String id = entry.getCoverArt();
                if (id == null) {
                    return null; // Can't load
                }

                StreamResponse response = subsonicAPIClient.getCoverArt(id, (long) size);
                checkStreamResponseError(response);

                if (response.getStream() == null) {
                    return null; // Failed to load
                }

                InputStream in = null;
                try {
                    in = response.getStream();
                    byte[] bytes = Util.toByteArray(in);

                    // If we aren't allowing server-side scaling, always save the file to disk because it will be unmodified
                    if (!serverScaling || saveToFile) {
                        OutputStream out = null;

                        try {
                            out = new FileOutputStream(FileUtil.getAlbumArtFile(context, entry));
                            out.write(bytes);
                        } finally {
                            Util.close(out);
                        }
                    }

                    bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality);
                } finally {
                    Util.close(in);
                }
            }

            // Return scaled bitmap
            return Util.scaleBitmap(bitmap, size);
        }
    }

    private void checkStreamResponseError(StreamResponse response)
            throws SubsonicRESTException, IOException {
        if (response.hasError() || response.getStream() == null) {
            if (response.getApiError() != null) {
                throw new SubsonicRESTException(response.getApiError().getCode(), "rest error");
            } else {
                throw new IOException("Failed to make endpoint request, code: " +
                        response.getResponseHttpCode());
            }
        }
    }

    @Override
    public Pair<InputStream, Boolean> getDownloadInputStream(final Context context,
                                                             final MusicDirectory.Entry song,
                                                             final long offset,
                                                             final int maxBitrate,
                                                             final CancellableTask task)
            throws Exception {
        if (song == null) {
            throw new IllegalArgumentException("Song for download is null!");
        }
        long songOffset = offset < 0 ? 0 : offset;

        StreamResponse response = subsonicAPIClient.stream(song.getId(), maxBitrate, songOffset);
        checkStreamResponseError(response);
        if (response.getStream() == null) {
            throw new IOException("Null stream response");
        }
        Boolean partial = response.getResponseHttpCode() == 206;

        return new Pair<>(response.getStream(), partial);
    }

    @Override
    public String getVideoUrl(final Context context,
                              final String id,
                              final boolean useFlash) throws Exception {
        // This method should not exists as video should be loaded using stream method
        // Previous method implementation uses assumption that video will be available
        // by videoPlayer.view?id=<id>&maxBitRate=500&autoplay=true, but this url is not
        // official Subsonic API call.
        if (id == null) {
            throw new IllegalArgumentException("Id is null");
        }
        final String[] expectedResult = new String[1];
        expectedResult[0] = null;
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                expectedResult[0] = subsonicAPIClient.getStreamUrl(id) + "&format=raw";
                latch.countDown();
            }
        }, "Get-Video-Url").start();

        latch.await(3, TimeUnit.SECONDS);
        return expectedResult[0];
    }

    @Override
    public JukeboxStatus updateJukeboxPlaylist(List<String> ids,
                                               Context context,
                                               ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<JukeboxResponse> response = subsonicAPIClient.getApi()
                .jukeboxControl(JukeboxAction.SET, null, null, ids, null)
                .execute();
        checkResponseSuccessful(response);

        return APIJukeboxConverter.toDomainEntity(response.body().getJukebox());
    }

    @Override
    public JukeboxStatus skipJukebox(int index,
                                     int offsetSeconds,
                                     Context context,
                                     ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<JukeboxResponse> response = subsonicAPIClient.getApi()
                .jukeboxControl(JukeboxAction.SKIP, index, offsetSeconds, null, null)
                .execute();
        checkResponseSuccessful(response);

        return APIJukeboxConverter.toDomainEntity(response.body().getJukebox());
    }

    @Override
    public JukeboxStatus stopJukebox(Context context,
                                     ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<JukeboxResponse> response = subsonicAPIClient.getApi()
                .jukeboxControl(JukeboxAction.STOP, null, null, null, null)
                .execute();
        checkResponseSuccessful(response);

        return APIJukeboxConverter.toDomainEntity(response.body().getJukebox());
    }

    @Override
    public JukeboxStatus startJukebox(Context context,
                                      ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<JukeboxResponse> response = subsonicAPIClient.getApi()
                .jukeboxControl(JukeboxAction.START, null, null, null, null)
                .execute();
        checkResponseSuccessful(response);

        return APIJukeboxConverter.toDomainEntity(response.body().getJukebox());
    }

    @Override
    public JukeboxStatus getJukeboxStatus(Context context,
                                          ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<JukeboxResponse> response = subsonicAPIClient.getApi()
                .jukeboxControl(JukeboxAction.STATUS, null, null, null, null)
                .execute();
        checkResponseSuccessful(response);

        return APIJukeboxConverter.toDomainEntity(response.body().getJukebox());
    }

    @Override
    public JukeboxStatus setJukeboxGain(float gain, Context context,
                                        ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<JukeboxResponse> response = subsonicAPIClient.getApi()
                .jukeboxControl(JukeboxAction.SET_GAIN, null, null, null, gain)
                .execute();
        checkResponseSuccessful(response);

        return APIJukeboxConverter.toDomainEntity(response.body().getJukebox());
    }

    @Override
    public List<Share> getShares(boolean refresh,
                                 Context context,
                                 ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);

        Response<SharesResponse> response = subsonicAPIClient.getApi().getShares().execute();
        checkResponseSuccessful(response);

        return APIShareConverter.toDomainEntitiesList(response.body().getShares());
    }

    @Override
    public List<Genre> getGenres(Context context,
                                 ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GenresResponse> response = subsonicAPIClient.getApi().getGenres().execute();
        checkResponseSuccessful(response);

        return ApiGenreConverter.toDomainEntityList(response.body().getGenresList());
    }

    @Override
    public MusicDirectory getSongsByGenre(String genre,
                                          int count,
                                          int offset,
                                          Context context,
                                          ProgressListener progressListener) throws Exception {
        if (genre == null) {
            throw new IllegalArgumentException("Genre is null");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetSongsByGenreResponse> response = subsonicAPIClient.getApi()
                .getSongsByGenre(genre, count, offset, null)
                .execute();
        checkResponseSuccessful(response);

        MusicDirectory result = new MusicDirectory();
        result.addAll(APIMusicDirectoryConverter.toDomainEntityList(response.body().getSongsList()));
        return result;
    }

    @Override
    public UserInfo getUser(String username,
                            Context context,
                            ProgressListener progressListener) throws Exception {
        if (username == null) {
            throw new IllegalArgumentException("Username is null");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<GetUserResponse> response = subsonicAPIClient.getApi()
                .getUser(username).execute();
        checkResponseSuccessful(response);

        return APIUserConverter.toDomainEntity(response.body().getUser());
    }

    @Override
    public List<ChatMessage> getChatMessages(Long since,
                                             Context context,
                                             ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<ChatMessagesResponse> response = subsonicAPIClient.getApi()
                .getChatMessages(since).execute();
        checkResponseSuccessful(response);

        return APIChatMessageConverter.toDomainEntitiesList(response.body().getChatMessages());
    }

    @Override
    public void addChatMessage(String message,
                               Context context,
                               ProgressListener progressListener) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Message is null");
        }

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .addChatMessage(message).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public List<Bookmark> getBookmarks(Context context,
                                       ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<BookmarksResponse> response = subsonicAPIClient.getApi()
                .getBookmarks().execute();
        checkResponseSuccessful(response);

        return APIBookmarkConverter.toDomainEntitiesList(response.body().getBookmarkList());
    }

    @Override
    public void createBookmark(String id,
                               int position,
                               Context context,
                               ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Item id should not be null");
        }
        Integer itemId = Integer.valueOf(id);
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .createBookmark(itemId, position, null).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public void deleteBookmark(String id,
                               Context context,
                               ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Id is null");
        }
        Integer itemId = Integer.parseInt(id);

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .deleteBookmark(itemId).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public MusicDirectory getVideos(boolean refresh,
                                    Context context,
                                    ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<VideosResponse> response = subsonicAPIClient.getApi()
                .getVideos().execute();
        checkResponseSuccessful(response);

        MusicDirectory musicDirectory = new MusicDirectory();
        musicDirectory.addAll(APIMusicDirectoryConverter
                .toDomainEntityList(response.body().getVideosList()));
        return musicDirectory;
    }

    @Override
    public List<Share> createShare(List<String> ids,
                                   String description,
                                   Long expires,
                                   Context context,
                                   ProgressListener progressListener) throws Exception {
        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SharesResponse> response = subsonicAPIClient.getApi()
                .createShare(ids, description, expires).execute();
         checkResponseSuccessful(response);

         return APIShareConverter.toDomainEntitiesList(response.body().getShares());
    }

    @Override
    public void deleteShare(String id,
                            Context context,
                            ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Id is null!");
        }
        Long shareId = Long.valueOf(id);

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .deleteShare(shareId).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public void updateShare(String id,
                            String description,
                            Long expires,
                            Context context,
                            ProgressListener progressListener) throws Exception {
        if (id == null) {
            throw new IllegalArgumentException("Id is null");
        }
        if (expires != null &&
                expires == 0) {
            expires = null;
        }

        Long shareId = Long.valueOf(id);

        updateProgressListener(progressListener, R.string.parser_reading);
        Response<SubsonicResponse> response = subsonicAPIClient.getApi()
                .updateShare(shareId, description, expires).execute();
        checkResponseSuccessful(response);
    }

    @Override
    public Bitmap getAvatar(final Context context,
                            final String username,
                            final int size,
                            final boolean saveToFile,
                            final boolean highQuality,
                            final ProgressListener progressListener) throws Exception {
        // Synchronize on the username so that we don't download concurrently for
        // the same user.
        if (username == null) {
            return null;
        }

        synchronized (username) {
            // Use cached file, if existing.
            Bitmap bitmap = FileUtil.getAvatarBitmap(username, size, highQuality);

            if (bitmap == null) {
                InputStream in = null;
                try {
                    updateProgressListener(progressListener, R.string.parser_reading);
                    StreamResponse response = subsonicAPIClient.getAvatar(username);
                    if (response.hasError()) {
                        return null;
                    }
                    in = response.getStream();
                    byte[] bytes = Util.toByteArray(in);

                    // If we aren't allowing server-side scaling, always save the file to disk because it will be unmodified
                    if (saveToFile) {
                        OutputStream out = null;

                        try {
                            out = new FileOutputStream(FileUtil.getAvatarFile(username));
                            out.write(bytes);
                        } finally {
                            Util.close(out);
                        }
                    }

                    bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality);
                } finally {
                    Util.close(in);
                }
            }

            // Return scaled bitmap
            return Util.scaleBitmap(bitmap, size);
        }
    }

    private void updateProgressListener(@Nullable final ProgressListener progressListener,
                                        @StringRes final int messageId) {
        if (progressListener != null) {
            progressListener.updateProgress(messageId);
        }
    }

    private void checkResponseSuccessful(@NonNull final Response<? extends SubsonicResponse> response)
            throws IOException {
        if (response.isSuccessful() &&
                response.body().getStatus() == SubsonicResponse.Status.OK) {
            return;
        }

        if (!response.isSuccessful()) {
            throw new IOException("Server error, code: " + response.code());
        } else if (response.body().getStatus() == SubsonicResponse.Status.ERROR &&
                response.body().getError() != null) {
            throw new IOException("Server error: " + response.body().getError().getCode());
        } else {
            throw new IOException("Failed to perform request: " + response.code());
        }
    }
}
