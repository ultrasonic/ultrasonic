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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient;
import org.moire.ultrasonic.api.subsonic.response.GetArtistResponse;
import org.moire.ultrasonic.api.subsonic.response.GetArtistsResponse;
import org.moire.ultrasonic.api.subsonic.response.GetIndexesResponse;
import org.moire.ultrasonic.api.subsonic.response.GetMusicDirectoryResponse;
import org.moire.ultrasonic.api.subsonic.response.LicenseResponse;
import org.moire.ultrasonic.api.subsonic.response.MusicFoldersResponse;
import org.moire.ultrasonic.api.subsonic.response.SubsonicResponse;
import org.moire.ultrasonic.data.APIConverter;
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
import org.moire.ultrasonic.domain.Version;
import org.moire.ultrasonic.service.parser.AlbumListParser;
import org.moire.ultrasonic.service.parser.BookmarkParser;
import org.moire.ultrasonic.service.parser.ChatMessageParser;
import org.moire.ultrasonic.service.parser.ErrorParser;
import org.moire.ultrasonic.service.parser.GenreParser;
import org.moire.ultrasonic.service.parser.JukeboxStatusParser;
import org.moire.ultrasonic.service.parser.LyricsParser;
import org.moire.ultrasonic.service.parser.MusicDirectoryParser;
import org.moire.ultrasonic.service.parser.PlaylistParser;
import org.moire.ultrasonic.service.parser.PlaylistsParser;
import org.moire.ultrasonic.service.parser.PodcastEpisodeParser;
import org.moire.ultrasonic.service.parser.PodcastsChannelsParser;
import org.moire.ultrasonic.service.parser.RandomSongsParser;
import org.moire.ultrasonic.service.parser.SearchResult2Parser;
import org.moire.ultrasonic.service.parser.SearchResultParser;
import org.moire.ultrasonic.service.parser.ShareParser;
import org.moire.ultrasonic.service.parser.UserInfoParser;
import org.moire.ultrasonic.service.ssl.SSLSocketFactory;
import org.moire.ultrasonic.service.ssl.TrustSelfSignedStrategy;
import org.moire.ultrasonic.util.CancellableTask;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.ProgressListener;
import org.moire.ultrasonic.util.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Response;

import static java.util.Arrays.asList;

/**
 * @author Sindre Mehus
 */
public class RESTMusicService implements MusicService
{

	private static final String TAG = RESTMusicService.class.getSimpleName();

	private static final int SOCKET_CONNECT_TIMEOUT = 10 * 1000;
	private static final int SOCKET_READ_TIMEOUT_DEFAULT = 10 * 1000;
	private static final int SOCKET_READ_TIMEOUT_DOWNLOAD = 30 * 1000;
	private static final int SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS = 60 * 1000;
	private static final int SOCKET_READ_TIMEOUT_GET_PLAYLIST = 60 * 1000;

	// Allow 20 seconds extra timeout per MB offset.
	private static final double TIMEOUT_MILLIS_PER_OFFSET_BYTE = 20000.0 / 1000000.0;

	/**
	 * URL from which to fetch latest versions.
	 */
	private static final String VERSION_URL = "http://subsonic.org/backend/version.view";

	private static final int HTTP_REQUEST_MAX_ATTEMPTS = 5;
	private static final long REDIRECTION_CHECK_INTERVAL_MILLIS = 60L * 60L * 1000L;

	private final DefaultHttpClient httpClient;
	private long redirectionLastChecked;
	private int redirectionNetworkType = -1;
	private String redirectFrom;
	private String redirectTo;
	private final ThreadSafeClientConnManager connManager;
    private SubsonicAPIClient subsonicAPIClient;

    public RESTMusicService(SubsonicAPIClient subsonicAPIClient) {
        this.subsonicAPIClient = subsonicAPIClient;

        // Create and initialize default HTTP parameters
		HttpParams params = new BasicHttpParams();
		ConnManagerParams.setMaxTotalConnections(params, 20);
		ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(20));
		HttpConnectionParams.setConnectionTimeout(params, SOCKET_CONNECT_TIMEOUT);
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_DEFAULT);

		// Turn off stale checking.  Our connections break all the time anyway,
		// and it's not worth it to pay the penalty of checking every time.
		HttpConnectionParams.setStaleCheckingEnabled(params, false);

		// Create and initialize scheme registry
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", createSSLSocketFactory(), 443));

		// Create an HttpClient with the ThreadSafeClientConnManager.
		// This connection manager must be used if more than one thread will
		// be using the HttpClient.
		connManager = new ThreadSafeClientConnManager(params, schemeRegistry);
		httpClient = new DefaultHttpClient(connManager, params);
	}

	private static SocketFactory createSSLSocketFactory()
	{
		try
		{
			return new SSLSocketFactory(new TrustSelfSignedStrategy(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		}
		catch (Throwable x)
		{
			Log.e(TAG, "Failed to create custom SSL socket factory, using default.", x);
			return org.apache.http.conn.ssl.SSLSocketFactory.getSocketFactory();
		}
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

        List<MusicFolder> musicFolders = APIConverter.toDomainEntityList(response.body().getMusicFolders());
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

        Indexes indexes = APIConverter.toDomainEntity(response.body().getIndexes());
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

        Indexes indexes = APIConverter.toDomainEntity(response.body().getIndexes());
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

        return APIConverter.toDomainEntity(response.body().getMusicDirectory());
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

        return APIConverter.toMusicDirectoryDomainEntity(response.body().getArtist());
    }

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Album by ID3 tag not supported.");

		Reader reader = getReader(context, progressListener, "getAlbum", null, "id", id);
		try
		{
			return new MusicDirectoryParser(context).parse(name, reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public SearchResult search(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		try
		{
			return !Util.isOffline(context) && Util.getShouldUseId3Tags(context) ? search3(criteria, context, progressListener) : search2(criteria, context, progressListener);
		}
		catch (ServerTooOldException x)
		{
			// Ensure backward compatibility with REST 1.3.
			return searchOld(criteria, context, progressListener);
		}
	}

	/**
	 * Search using the "search" REST method.
	 */
	private SearchResult searchOld(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = asList("any", "songCount");
		List<Object> parameterValues = Arrays.<Object>asList(criteria.getQuery(), criteria.getSongCount());
		Reader reader = getReader(context, progressListener, "search", null, parameterNames, parameterValues);
		try
		{
			return new SearchResultParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	/**
	 * Search using the "search2" REST method, available in 1.4.0 and later.
	 */
	private SearchResult search2(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.4", "Search2 not supported.");

		List<String> parameterNames = asList("query", "artistCount", "albumCount", "songCount");
		List<Object> parameterValues = Arrays.<Object>asList(criteria.getQuery(), criteria.getArtistCount(), criteria.getAlbumCount(), criteria.getSongCount());
		Reader reader = getReader(context, progressListener, "search2", null, parameterNames, parameterValues);
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	private SearchResult search3(SearchCriteria criteria, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Searching by ID3 tag not supported.");

		List<String> parameterNames = asList("query", "artistCount", "albumCount", "songCount");
		List<Object> parameterValues = Arrays.<Object>asList(criteria.getQuery(), criteria.getArtistCount(), criteria.getAlbumCount(), criteria.getSongCount());
		Reader reader = getReader(context, progressListener, "search3", null, parameterNames, parameterValues);
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getPlaylist(String id, String name, Context context, ProgressListener progressListener) throws Exception
	{
		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_PLAYLIST);

		Reader reader = getReader(context, progressListener, "getPlaylist", params, "id", id);
		try
		{
			MusicDirectory playlist = new PlaylistParser(context).parse(reader, progressListener);

			File playlistFile = FileUtil.getPlaylistFile(Util.getServerName(context), name);
			FileWriter fw = new FileWriter(playlistFile);
			BufferedWriter bw = new BufferedWriter(fw);
			try
			{
				fw.write("#EXTM3U\n");
				for (MusicDirectory.Entry e : playlist.getChildren())
				{
					String filePath = FileUtil.getSongFile(context, e).getAbsolutePath();
					if (!new File(filePath).exists())
					{
						String ext = FileUtil.getExtension(filePath);
						String base = FileUtil.getBaseName(filePath);
						filePath = base + ".complete." + ext;
					}
					fw.write(filePath + '\n');
				}
			}
			catch (Exception e)
			{
				Log.w(TAG, "Failed to save playlist: " + name);
			}
			finally
			{
				bw.close();
				fw.close();
			}

			return playlist;
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<PodcastsChannel> getPodcastsChannels(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReader(context, progressListener, "getPodcasts", null,"includeEpisodes", "false");
		try {
			return new PodcastsChannelsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getPodcastEpisodes(String podcastChannelId, Context context, ProgressListener progressListener) throws Exception {

		List<String> names = new ArrayList<String>();
		names.add("id");
		names.add("includeEpisodes");
		List<Object> values = new ArrayList<Object>();
		values.add(podcastChannelId);
		values.add("true");

		// TODO
		Reader reader = getReader(context, progressListener, "getPodcasts", null, names,values);
		//Reader reader = GetPodcastEpisodesTestReaderProvider.getReader();
		try {
			return new PodcastEpisodeParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}



	@Override
	public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReader(context, progressListener, "getPlaylists", null);
		try
		{
			return new PlaylistsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = new LinkedList<String>();
		List<Object> parameterValues = new LinkedList<Object>();

		if (id != null)
		{
			parameterNames.add("playlistId");
			parameterValues.add(id);
		}
		if (name != null)
		{
			parameterNames.add("name");
			parameterValues.add(name);
		}
		for (MusicDirectory.Entry entry : entries)
		{
			parameterNames.add("songId");
			parameterValues.add(entry.getId());
		}

		Reader reader = getReader(context, progressListener, "createPlaylist", null, parameterNames, parameterValues);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception
	{
		Reader reader = getReader(context, progressListener, "deletePlaylist", null, "id", id);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Updating playlists is not supported.");
		Reader reader = getReader(context, progressListener, "updatePlaylist", null, asList("playlistId", "name", "comment", "public"), Arrays.<Object>asList(id, name, comment, pub));
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Lyrics not supported.");

		Reader reader = getReader(context, progressListener, "getLyrics", null, asList("artist", "title"), Arrays.<Object>asList(artist, title));
		try
		{
			return new LyricsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.5", "Scrobbling not supported.");

		Reader reader = getReader(context, progressListener, "scrobble", null, asList("id", "submission"), Arrays.<Object>asList(id, submission));
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Album list not supported.");

		Reader reader = getReader(context, progressListener, "getAlbumList", null, asList("type", "size", "offset"), Arrays.<Object>asList(type, size, offset));
		try
		{
			return new AlbumListParser(context).parse(reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getAlbumList2(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Album list by ID3 tag not supported.");

		Reader reader = getReader(context, progressListener, "getAlbumList2", null, asList("type", "size", "offset"), Arrays.<Object>asList(type, size, offset));
		try
		{
			return new AlbumListParser(context).parse(reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getRandomSongs(int size, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Random songs not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> names = new ArrayList<String>();
		List<Object> values = new ArrayList<Object>();

		names.add("size");
		values.add(size);

		Reader reader = getReader(context, progressListener, "getRandomSongs", params, names, values);
		try
		{
			return new RandomSongsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public SearchResult getStarred(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Starred albums not supported.");

		Reader reader = getReader(context, progressListener, "getStarred", null);
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public SearchResult getStarred2(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Starred albums by ID3 tag not supported.");

		Reader reader = getReader(context, progressListener, "getStarred2", null);
		try
		{
			return new SearchResult2Parser(context).parse(reader, progressListener, true);
		}
		finally
		{
			Util.close(reader);
		}
	}

	private static void checkServerVersion(Context context, String version, String text) throws ServerTooOldException
	{
		Version serverVersion = Util.getServerRestVersion(context);
		Version requiredVersion = new Version(version);
		boolean ok = serverVersion == null || serverVersion.compareTo(requiredVersion) >= 0;

		if (!ok)
		{
			throw new ServerTooOldException(text);
		}
	}

	private static boolean checkServerVersion(Context context, String version)
	{
		Version serverVersion = Util.getServerRestVersion(context);
		Version requiredVersion = new Version(version);
		return serverVersion == null || serverVersion.compareTo(requiredVersion) >= 0;
	}

	@Override
	public Bitmap getCoverArt(Context context, final MusicDirectory.Entry entry, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception
	{
		// Synchronize on the entry so that we don't download concurrently for
		// the same song.
		if (entry == null)
		{
			return null;
		}

		synchronized (entry)
		{
			// Use cached file, if existing.
			Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, entry, size, highQuality);
			boolean serverScaling = Util.isServerScalingEnabled(context);

			if (bitmap == null)
			{
				String url = Util.getRestUrl(context, "getCoverArt");

				InputStream in = null;
				try
				{
					List<String> parameterNames;
					List<Object> parameterValues;

					if (serverScaling)
					{
						parameterNames = asList("id", "size");
						parameterValues = Arrays.<Object>asList(entry.getCoverArt(), size);
					}
					else
					{
						parameterNames = Collections.singletonList("id");
						parameterValues = Arrays.<Object>asList(entry.getCoverArt());
					}

					HttpEntity entity = getEntityForURL(context, url, null, parameterNames, parameterValues, progressListener);
					in = entity.getContent();

					// If content type is XML, an error occurred. Get it.
					String contentType = Util.getContentType(entity);
					if (contentType != null && contentType.startsWith("text/xml"))
					{
						new ErrorParser(context).parse(new InputStreamReader(in, Constants.UTF_8));
						return null; // Never reached.
					}

					byte[] bytes = Util.toByteArray(in);

					// If we aren't allowing server-side scaling, always save the file to disk because it will be unmodified
					if (!serverScaling || saveToFile)
					{
						OutputStream out = null;

						try
						{
							out = new FileOutputStream(FileUtil.getAlbumArtFile(context, entry));
							out.write(bytes);
						}
						finally
						{
							Util.close(out);
						}
					}

					bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality);
				}
				finally
				{
					Util.close(in);
				}
			}

			// Return scaled bitmap
			return Util.scaleBitmap(bitmap, size);
		}
	}

	@Override
	public HttpResponse getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, CancellableTask task) throws Exception
	{

		String url = Util.getRestUrl(context, "stream");

		// Set socket read timeout. Note: The timeout increases as the offset gets larger. This is
		// to avoid the thrashing effect seen when offset is combined with transcoding/downsampling on the server.
		// In that case, the server uses a long time before sending any data, causing the client to time out.
		HttpParams params = new BasicHttpParams();
		int timeout = (int) (SOCKET_READ_TIMEOUT_DOWNLOAD + offset * TIMEOUT_MILLIS_PER_OFFSET_BYTE);
		HttpConnectionParams.setSoTimeout(params, timeout);

		// Add "Range" header if offset is given.
		Collection<Header> headers = new ArrayList<Header>();

		if (offset > 0)
		{
			headers.add(new BasicHeader("Range", String.format("bytes=%d-", offset)));
		}

		List<String> parameterNames = asList("id", "maxBitRate");
		List<Object> parameterValues = Arrays.<Object>asList(song.getId(), maxBitrate);
		HttpResponse response = getResponseForURL(context, url, params, parameterNames, parameterValues, headers, null, task);

		// If content type is XML, an error occurred.  Get it.
		String contentType = Util.getContentType(response.getEntity());
		if (contentType != null && contentType.startsWith("text/xml"))
		{
			InputStream in = response.getEntity().getContent();
			try
			{
				new ErrorParser(context).parse(new InputStreamReader(in, Constants.UTF_8));
			}
			finally
			{
				Util.close(in);
			}
		}

		return response;
	}

	@Override
	public String getVideoUrl(Context context, String id, boolean useFlash) throws Exception
	{
		StringBuilder builder = new StringBuilder(5);
		if (useFlash)
		{
			builder.append(Util.getRestUrl(context, "videoPlayer"));
			builder.append("&id=").append(id);
			builder.append("&maxBitRate=500");
			builder.append("&autoplay=true");
		}
		else
		{
			checkServerVersion(context, "1.9", "Video streaming not supported.");
			builder.append(Util.getRestUrl(context, "stream"));
			builder.append("&id=").append(id);
			builder.append("&format=raw");
		}

		String url = rewriteUrlWithRedirect(context, builder.toString());
		Log.i(TAG, String.format("Using video URL: %s", url));
		return url;
	}

	@Override
	public JukeboxStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception
	{
		int n = ids.size();
		List<String> parameterNames = new ArrayList<String>(n + 1);
		parameterNames.add("action");

		for (String ignored : ids)
		{
			parameterNames.add("id");
		}

		List<Object> parameterValues = new ArrayList<Object>();
		parameterValues.add("set");
		parameterValues.addAll(ids);

		return executeJukeboxCommand(context, progressListener, parameterNames, parameterValues);
	}

	@Override
	public JukeboxStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = asList("action", "index", "offset");
		List<Object> parameterValues = Arrays.<Object>asList("skip", index, offsetSeconds);
		return executeJukeboxCommand(context, progressListener, parameterNames, parameterValues);
	}

	@Override
	public JukeboxStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		return executeJukeboxCommand(context, progressListener, Collections.singletonList("action"), Arrays.<Object>asList("stop"));
	}

	@Override
	public JukeboxStatus startJukebox(Context context, ProgressListener progressListener) throws Exception
	{
		return executeJukeboxCommand(context, progressListener, Collections.singletonList("action"), Arrays.<Object>asList("start"));
	}

	@Override
	public JukeboxStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception
	{
		return executeJukeboxCommand(context, progressListener, Collections.singletonList("action"), Arrays.<Object>asList("status"));
	}

	@Override
	public JukeboxStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = asList("action", "gain");
		List<Object> parameterValues = Arrays.<Object>asList("setGain", gain);
		return executeJukeboxCommand(context, progressListener, parameterNames, parameterValues);
	}

	@Override
	public List<Share> getShares(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.6", "Shares not supported.");
		Reader reader = getReader(context, progressListener, "getShares", null);
		try
		{
			return new ShareParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	private JukeboxStatus executeJukeboxCommand(Context context, ProgressListener progressListener, List<String> parameterNames, List<Object> parameterValues) throws Exception
	{
		checkServerVersion(context, "1.7", "Jukebox not supported.");
		Reader reader = getReader(context, progressListener, "jukeboxControl", null, parameterNames, parameterValues);
		try
		{
			return new JukeboxStatusParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	private Reader getReader(Context context, ProgressListener progressListener, String method, HttpParams requestParams) throws Exception
	{
		return getReader(context, progressListener, method, requestParams, Collections.<String>emptyList(), Collections.emptyList());
	}

	private Reader getReader(Context context, ProgressListener progressListener, String method, HttpParams requestParams, String parameterName, Object parameterValue) throws Exception
	{
		return getReader(context, progressListener, method, requestParams, Collections.singletonList(parameterName), Collections.singletonList(parameterValue));
	}

	private Reader getReader(Context context, ProgressListener progressListener, String method, HttpParams requestParams, List<String> parameterNames, List<Object> parameterValues) throws Exception
	{

		if (progressListener != null)
		{
			progressListener.updateProgress(R.string.service_connecting);
		}

		String url = Util.getRestUrl(context, method);
		return getReaderForURL(context, url, requestParams, parameterNames, parameterValues, progressListener);
	}

	private Reader getReaderForURL(Context context, String url, HttpParams requestParams, List<String> parameterNames, List<Object> parameterValues, ProgressListener progressListener) throws Exception
	{
		HttpEntity entity = getEntityForURL(context, url, requestParams, parameterNames, parameterValues, progressListener);
		if (entity == null)
		{
			throw new RuntimeException(String.format("No entity received for URL %s", url));
		}

		InputStream in = entity.getContent();
		return new InputStreamReader(in, Constants.UTF_8);
	}

	private HttpEntity getEntityForURL(Context context, String url, HttpParams requestParams, List<String> parameterNames, List<Object> parameterValues, ProgressListener progressListener) throws Exception
	{
		return getResponseForURL(context, url, requestParams, parameterNames, parameterValues, null, progressListener, null).getEntity();
	}

	private HttpResponse getResponseForURL(Context context, String url, HttpParams requestParams, List<String> parameterNames, List<Object> parameterValues, Iterable<Header> headers, ProgressListener progressListener, CancellableTask task) throws Exception
	{
		Log.d(TAG, String.format("Connections in pool: %d", connManager.getConnectionsInPool()));

		// If not too many parameters, extract them to the URL rather than
		// relying on the HTTP POST request being
		// received intact. Remember, HTTP POST requests are converted to GET
		// requests during HTTP redirects, thus
		// loosing its entity.

		if (parameterNames != null)
		{
			int parameters = parameterNames.size();

			if (parameters < 10)
			{
				StringBuilder builder = new StringBuilder(url);

				for (int i = 0; i < parameters; i++)
				{
					builder.append('&').append(parameterNames.get(i)).append('=');
					builder.append(URLEncoder.encode(String.valueOf(parameterValues.get(i)), "UTF-8"));
				}

				url = builder.toString();
				parameterNames = null;
				parameterValues = null;
			}
		}

		String rewrittenUrl = rewriteUrlWithRedirect(context, url);
		return executeWithRetry(context, rewrittenUrl, url, requestParams, parameterNames, parameterValues, headers, progressListener, task);
	}

	private HttpResponse executeWithRetry(Context context, String url, String originalUrl, HttpParams requestParams, List<String> parameterNames, List<Object> parameterValues, Iterable<Header> headers, ProgressListener progressListener, CancellableTask task) throws IOException
	{
		Log.i(TAG, String.format("Using URL %s", url));

		int networkTimeout = Util.getNetworkTimeout(context);
		HttpParams newParams = httpClient.getParams();
		HttpConnectionParams.setSoTimeout(newParams, networkTimeout);
		httpClient.setParams(newParams);
		final AtomicReference<Boolean> cancelled = new AtomicReference<Boolean>(false);
		int attempts = 0;

		while (true)
		{
			attempts++;
			HttpContext httpContext = new BasicHttpContext();
			final HttpPost request = new HttpPost(url);

			if (task != null)
			{
				// Attempt to abort the HTTP request if the task is cancelled.
				task.setOnCancelListener(new CancellableTask.OnCancelListener()
				{
					@Override
					public void onCancel()
					{
						new Thread(new Runnable()
						{
							@Override
							public void run()
							{
								try
								{
									cancelled.set(true);
									request.abort();
								}
								catch (Exception e)
								{
									Log.e(TAG, "Failed to stop http task");
								}
							}
						}).start();
					}
				});
			}

			if (parameterNames != null)
			{
				List<NameValuePair> params = new ArrayList<NameValuePair>();

				for (int i = 0; i < parameterNames.size(); i++)
				{
					params.add(new BasicNameValuePair(parameterNames.get(i), String.valueOf(parameterValues.get(i))));
				}

				request.setEntity(new UrlEncodedFormEntity(params, Constants.UTF_8));
			}

			if (requestParams != null)
			{
				request.setParams(requestParams);
				Log.d(TAG, String.format("Socket read timeout: %d ms.", HttpConnectionParams.getSoTimeout(requestParams)));
			}

			if (headers != null)
			{
				for (Header header : headers)
				{
					request.addHeader(header);
				}
			}

			// Set credentials to get through apache proxies that require authentication.
			SharedPreferences preferences = Util.getPreferences(context);
			int instance = preferences.getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
			String username = preferences.getString(Constants.PREFERENCES_KEY_USERNAME + instance, null);
			String password = preferences.getString(Constants.PREFERENCES_KEY_PASSWORD + instance, null);
			httpClient.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), new UsernamePasswordCredentials(username, password));

			try
			{
				HttpResponse response = httpClient.execute(request, httpContext);
				detectRedirect(originalUrl, context, httpContext);
				return response;
			}
			catch (IOException x)
			{
				request.abort();

				if (attempts >= HTTP_REQUEST_MAX_ATTEMPTS || cancelled.get())
				{
					throw x;
				}

				if (progressListener != null)
				{
					String msg = context.getResources().getString(R.string.music_service_retry, attempts, HTTP_REQUEST_MAX_ATTEMPTS - 1);
					progressListener.updateProgress(msg);
				}

				Log.w(TAG, String.format("Got IOException (%d), will retry", attempts), x);
				increaseTimeouts(requestParams);
				Util.sleepQuietly(2000L);
			}
		}
	}

	private static void increaseTimeouts(HttpParams requestParams)
	{
		if (requestParams != null)
		{
			int connectTimeout = HttpConnectionParams.getConnectionTimeout(requestParams);
			if (connectTimeout != 0)
			{
				HttpConnectionParams.setConnectionTimeout(requestParams, (int) (connectTimeout * 1.3F));
			}
			int readTimeout = HttpConnectionParams.getSoTimeout(requestParams);
			if (readTimeout != 0)
			{
				HttpConnectionParams.setSoTimeout(requestParams, (int) (readTimeout * 1.5F));
			}
		}
	}

	private void detectRedirect(String originalUrl, Context context, HttpContext httpContext)
	{
		HttpUriRequest request = (HttpUriRequest) httpContext.getAttribute(ExecutionContext.HTTP_REQUEST);
		HttpHost host = (HttpHost) httpContext.getAttribute(ExecutionContext.HTTP_TARGET_HOST);

		// Sometimes the request doesn't contain the "http://host" part
		String redirectedUrl;
		redirectedUrl = request.getURI().getScheme() == null ? host.toURI() + request.getURI() : request.getURI().toString();

		redirectFrom = originalUrl.substring(0, originalUrl.indexOf("/rest/"));
		redirectTo = redirectedUrl.substring(0, redirectedUrl.indexOf("/rest/"));

		Log.i(TAG, String.format("%s redirects to %s", redirectFrom, redirectTo));
		redirectionLastChecked = System.currentTimeMillis();
		redirectionNetworkType = getCurrentNetworkType(context);
	}

	private String rewriteUrlWithRedirect(Context context, String url)
	{
		// Only cache for a certain time.
		if (System.currentTimeMillis() - redirectionLastChecked > REDIRECTION_CHECK_INTERVAL_MILLIS)
		{
			return url;
		}

		// Ignore cache if network type has changed.
		if (redirectionNetworkType != getCurrentNetworkType(context))
		{
			return url;
		}

		if (redirectFrom == null || redirectTo == null)
		{
			return url;
		}

		return url.replace(redirectFrom, redirectTo);
	}

	private static int getCurrentNetworkType(Context context)
	{
		ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = manager.getActiveNetworkInfo();
		return networkInfo == null ? -1 : networkInfo.getType();
	}

	@Override
	public List<Genre> getGenres(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Genres not supported.");

		Reader reader = getReader(context, progressListener, "getGenres", null);
		try
		{
			return new GenreParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Genres not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("genre");
		parameterValues.add(genre);
		parameterNames.add("count");
		parameterValues.add(count);
		parameterNames.add("offset");
		parameterValues.add(offset);

		Reader reader = getReader(context, progressListener, "getSongsByGenre", params, parameterNames, parameterValues);

		try
		{
			return new RandomSongsParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public UserInfo getUser(String username, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.3", "getUser not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("username");
		parameterValues.add(username);

		Reader reader = getReader(context, progressListener, "getUser", params, parameterNames, parameterValues);

		try
		{
			return new UserInfoParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Chat not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("since");
		parameterValues.add(since);

		Reader reader = getReader(context, progressListener, "getChatMessages", params, parameterNames, parameterValues);

		try
		{
			return new ChatMessageParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void addChatMessage(String message, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.2", "Chat not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("message");
		parameterValues.add(message);

		Reader reader = getReader(context, progressListener, "addChatMessage", params, parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<Bookmark> getBookmarks(Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Bookmarks not supported.");

		Reader reader = getReader(context, progressListener, "getBookmarks", null);

		try
		{
			return new BookmarkParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void createBookmark(String id, int position, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Bookmarks not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);
		parameterNames.add("position");
		parameterValues.add(position);

		Reader reader = getReader(context, progressListener, "createBookmark", params, parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void deleteBookmark(String id, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.9", "Bookmarks not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);

		Reader reader = getReader(context, progressListener, "deleteBookmark", params, parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public MusicDirectory getVideos(boolean refresh, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.8", "Videos not supported.");

		Reader reader = getReader(context, progressListener, "getVideos", null);

		try
		{
			return new MusicDirectoryParser(context).parse("", reader, progressListener, false);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires, Context context, ProgressListener progressListener) throws Exception
	{
		List<String> parameterNames = new LinkedList<String>();
		List<Object> parameterValues = new LinkedList<Object>();

		for (String id : ids)
		{
			parameterNames.add("id");
			parameterValues.add(id);
		}

		if (description != null)
		{
			parameterNames.add("description");
			parameterValues.add(description);
		}

		if (expires > 0)
		{
			parameterNames.add("expires");
			parameterValues.add(expires);
		}

		Reader reader = getReader(context, progressListener, "createShare", null, parameterNames, parameterValues);
		try
		{
			return new ShareParser(context).parse(reader, progressListener);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void deleteShare(String id, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.6", "Shares not supported.");

		HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);

		Reader reader = getReader(context, progressListener, "deleteShare", params, parameterNames, parameterValues);

		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public void updateShare(String id, String description, Long expires, Context context, ProgressListener progressListener) throws Exception
	{
		checkServerVersion(context, "1.6", "Updating share not supported.");

  	HttpParams params = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(params, SOCKET_READ_TIMEOUT_GET_RANDOM_SONGS);

		List<String> parameterNames = new ArrayList<String>();
		List<Object> parameterValues = new ArrayList<Object>();

		parameterNames.add("id");
		parameterValues.add(id);

		if (description != null)
		{
			parameterNames.add("description");
			parameterValues.add(description);
		}

		if (expires > 0)
		{
			parameterNames.add("expires");
			parameterValues.add(expires);
		}

		Reader reader = getReader(context, progressListener, "updateShare", params, parameterNames, parameterValues);
		try
		{
			new ErrorParser(context).parse(reader);
		}
		finally
		{
			Util.close(reader);
		}
	}

	@Override
	public Bitmap getAvatar(Context context, String username, int size, boolean saveToFile, boolean highQuality, ProgressListener progressListener) throws Exception
	{
		// Return silently if server is too old
		if (!checkServerVersion(context, "1.8"))
			return null;

		// Synchronize on the username so that we don't download concurrently for
		// the same user.
		if (username == null)
		{
			return null;
		}

		synchronized (username)
		{
			// Use cached file, if existing.
			Bitmap bitmap = FileUtil.getAvatarBitmap(username, size, highQuality);

			if (bitmap == null)
			{
				String url = Util.getRestUrl(context, "getAvatar");

				InputStream in = null;

				try
				{
					List<String> parameterNames;
					List<Object> parameterValues;

					parameterNames = Collections.singletonList("username");
					parameterValues = Arrays.<Object>asList(username);

					HttpEntity entity = getEntityForURL(context, url, null, parameterNames, parameterValues, progressListener);
					in = entity.getContent();

					// If content type is XML, an error occurred. Get it.
					String contentType = Util.getContentType(entity);
					if (contentType != null && contentType.startsWith("text/xml"))
					{
						new ErrorParser(context).parse(new InputStreamReader(in, Constants.UTF_8));
						return null; // Never reached.
					}

					byte[] bytes = Util.toByteArray(in);

					// If we aren't allowing server-side scaling, always save the file to disk because it will be unmodified
					if (saveToFile)
					{
						OutputStream out = null;

						try
						{
							out = new FileOutputStream(FileUtil.getAvatarFile(username));
							out.write(bytes);
						}
						finally
						{
							Util.close(out);
						}
					}

					bitmap = FileUtil.getSampledBitmap(bytes, size, highQuality);
				}
				finally
				{
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

        if (response.body().getStatus() == SubsonicResponse.Status.ERROR &&
                response.body().getError() != null) {
            throw new IOException("Server error: " + response.body().getError().getCode());
        } else {
            throw new IOException("Failed to perform request: " + response.code());
        }
    }
}
