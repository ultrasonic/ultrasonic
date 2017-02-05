package org.moire.ultrasonic.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.SearchRecentSuggestions;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.util.Log;
import android.view.View;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.activity.SubsonicTabActivity;
import org.moire.ultrasonic.provider.SearchSuggestionProvider;
import org.moire.ultrasonic.service.DownloadService;
import org.moire.ultrasonic.service.DownloadServiceImpl;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.ErrorDialog;
import org.moire.ultrasonic.util.FileUtil;
import org.moire.ultrasonic.util.ImageLoader;
import org.moire.ultrasonic.util.ModalBackgroundTask;
import org.moire.ultrasonic.util.TimeSpanPreference;
import org.moire.ultrasonic.util.Util;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows main app settings.
 */
public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LOG_TAG = SettingsFragment.class.getSimpleName();
    private final Map<String, ServerSettings> serverSettings = new LinkedHashMap<>();

    private ListPreference theme;
    private ListPreference videoPlayer;
    private ListPreference maxBitrateWifi;
    private ListPreference maxBitrateMobile;
    private ListPreference cacheSize;
    private EditTextPreference cacheLocation;
    private ListPreference preloadCount;
    private ListPreference bufferLength;
    private ListPreference incrementTime;
    private ListPreference networkTimeout;
    private ListPreference maxAlbums;
    private ListPreference maxSongs;
    private ListPreference maxArtists;
    private ListPreference defaultAlbums;
    private ListPreference defaultSongs;
    private ListPreference defaultArtists;
    private ListPreference chatRefreshInterval;
    private ListPreference directoryCacheTime;
    private CheckBoxPreference mediaButtonsEnabled;
    private CheckBoxPreference lockScreenEnabled;
    private CheckBoxPreference sendBluetoothNotifications;
    private CheckBoxPreference sendBluetoothAlbumArt;
    private ListPreference viewRefresh;
    private ListPreference imageLoaderConcurrency;
    private EditTextPreference sharingDefaultDescription;
    private EditTextPreference sharingDefaultGreeting;
    private TimeSpanPreference sharingDefaultExpiration;
    private PreferenceCategory serversCategory;
    private Preference addServerPreference;

    private boolean testingConnection;
    private int maxServerCount = 10;
    private SharedPreferences settings;
    private int activeServers;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
        activeServers = settings.getInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, 0);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        theme = (ListPreference) findPreference(Constants.PREFERENCES_KEY_THEME);
        videoPlayer = (ListPreference) findPreference(Constants.PREFERENCES_KEY_VIDEO_PLAYER);
        maxBitrateWifi = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI);
        maxBitrateMobile = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE);
        cacheSize = (ListPreference) findPreference(Constants.PREFERENCES_KEY_CACHE_SIZE);
        cacheLocation = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_CACHE_LOCATION);
        preloadCount = (ListPreference) findPreference(Constants.PREFERENCES_KEY_PRELOAD_COUNT);
        bufferLength = (ListPreference) findPreference(Constants.PREFERENCES_KEY_BUFFER_LENGTH);
        incrementTime = (ListPreference) findPreference(Constants.PREFERENCES_KEY_INCREMENT_TIME);
        networkTimeout = (ListPreference) findPreference(Constants.PREFERENCES_KEY_NETWORK_TIMEOUT);
        maxAlbums = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_ALBUMS);
        maxSongs = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_SONGS);
        maxArtists = (ListPreference) findPreference(Constants.PREFERENCES_KEY_MAX_ARTISTS);
        defaultArtists = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_ARTISTS);
        defaultSongs = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SONGS);
        defaultAlbums = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_ALBUMS);
        chatRefreshInterval = (ListPreference) findPreference(Constants.PREFERENCES_KEY_CHAT_REFRESH_INTERVAL);
        directoryCacheTime = (ListPreference) findPreference(Constants.PREFERENCES_KEY_DIRECTORY_CACHE_TIME);
        mediaButtonsEnabled = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_MEDIA_BUTTONS);
        lockScreenEnabled = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SHOW_LOCK_SCREEN_CONTROLS);
        sendBluetoothAlbumArt = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_ALBUM_ART);
        sendBluetoothNotifications = (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS);
        viewRefresh = (ListPreference) findPreference(Constants.PREFERENCES_KEY_VIEW_REFRESH);
        imageLoaderConcurrency = (ListPreference) findPreference(Constants.PREFERENCES_KEY_IMAGE_LOADER_CONCURRENCY);
        sharingDefaultDescription = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_DESCRIPTION);
        sharingDefaultGreeting = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_GREETING);
        sharingDefaultExpiration = (TimeSpanPreference) findPreference(Constants.PREFERENCES_KEY_DEFAULT_SHARE_EXPIRATION);
        serversCategory = (PreferenceCategory) findPreference(Constants.PREFERENCES_KEY_SERVERS_KEY);

        sharingDefaultGreeting.setText(Util.getShareGreeting(getActivity()));
        setupClearSearchPreference();
        setupGaplessControlSettingsV14();
        setupServersCategory();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        update();
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences preferences = Util.getPreferences(getActivity());
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        SharedPreferences prefs = Util.getPreferences(getActivity());
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(LOG_TAG, "Preference changed: " + key);
        update();

        if (Constants.PREFERENCES_KEY_HIDE_MEDIA.equals(key)) {
            setHideMedia(sharedPreferences.getBoolean(key, false));
        } else if (Constants.PREFERENCES_KEY_MEDIA_BUTTONS.equals(key)) {
            setMediaButtonsEnabled(sharedPreferences.getBoolean(key, true));
        } else if (Constants.PREFERENCES_KEY_CACHE_LOCATION.equals(key)) {
            setCacheLocation(sharedPreferences.getString(key, ""));
        } else if (Constants.PREFERENCES_KEY_SEND_BLUETOOTH_NOTIFICATIONS.equals(key)) {
            setBluetoothPreferences(sharedPreferences.getBoolean(key, true));
        } else if (Constants.PREFERENCES_KEY_IMAGE_LOADER_CONCURRENCY.equals(key)) {
            setImageLoaderConcurrency(Integer.parseInt(sharedPreferences.getString(key, "5")));
        }
    }

    private void setupClearSearchPreference() {
        Preference clearSearchPreference = findPreference(Constants.PREFERENCES_KEY_CLEAR_SEARCH_HISTORY);

        if (clearSearchPreference != null) {
            clearSearchPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SearchRecentSuggestions suggestions =
                            new SearchRecentSuggestions(getActivity(),
                                    SearchSuggestionProvider.AUTHORITY,
                                    SearchSuggestionProvider.MODE);
                    suggestions.clearHistory();
                    Util.toast(getActivity(), R.string.settings_search_history_cleared);
                    return false;
                }
            });
        }
    }

    private void setupGaplessControlSettingsV14() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            PreferenceCategory playbackControlSettings =
                    (PreferenceCategory) findPreference(Constants.PREFERENCES_KEY_PLAYBACK_CONTROL_SETTINGS);
            CheckBoxPreference gaplessPlaybackEnabled =
                    (CheckBoxPreference) findPreference(Constants.PREFERENCES_KEY_GAPLESS_PLAYBACK);

            if (gaplessPlaybackEnabled != null) {
                gaplessPlaybackEnabled.setChecked(false);
                gaplessPlaybackEnabled.setEnabled(false);

                if (playbackControlSettings != null) {
                    playbackControlSettings.removePreference(gaplessPlaybackEnabled);
                }
            }
        }
    }

    private void setupServersCategory() {
        addServerPreference = new Preference(getActivity());
        addServerPreference.setKey(Constants.PREFERENCES_KEY_ADD_SERVER);
        addServerPreference.setPersistent(false);
        addServerPreference.setTitle(getResources().getString(R.string.settings_server_add_server));
        addServerPreference.setEnabled(activeServers < maxServerCount);

        serversCategory.addPreference(addServerPreference);

        for (int i = 1; i <= activeServers; i++) {
            final int instanceValue = i;

            serversCategory.addPreference(addServer(i));

            Preference testConnectionPreference = findPreference(Constants.PREFERENCES_KEY_TEST_CONNECTION + i);

            if (testConnectionPreference != null) {
                testConnectionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        testConnection(instanceValue);
                        return false;
                    }
                });
            }

            String instance = String.valueOf(i);
            serverSettings.put(instance, new ServerSettings(instance));
        }

        addServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (activeServers == maxServerCount) {
                    return false;
                }

                activeServers++;

                settings.edit()
                        .putInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, activeServers)
                        .apply();

                Preference addServerPreference = findPreference(Constants.PREFERENCES_KEY_ADD_SERVER);

                if (addServerPreference != null) {
                    serversCategory.removePreference(addServerPreference);
                }

                serversCategory.addPreference(addServer(activeServers));

                if (addServerPreference != null) {
                    serversCategory.addPreference(addServerPreference);
                    addServerPreference.setEnabled(activeServers < maxServerCount);
                }

                String instance = String.valueOf(activeServers);
                serverSettings.put(instance, new ServerSettings(instance));

                return true;
            }
        });
    }

    private PreferenceScreen addServer(final int instance) {
        final Context context = getActivity();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

        if (screen != null) {
            screen.setTitle(R.string.settings_server_unused);
            screen.setKey(Constants.PREFERENCES_KEY_SERVER + instance);
        }

        final EditTextPreference serverNamePreference = new EditTextPreference(context);
        serverNamePreference.setKey(Constants.PREFERENCES_KEY_SERVER_NAME + instance);
        serverNamePreference.setDefaultValue(getResources()
                .getString(R.string.settings_server_unused));
        serverNamePreference.setTitle(R.string.settings_server_name);

        if (serverNamePreference.getText() == null) {
            serverNamePreference.setText(getResources().getString(R.string.settings_server_unused));
        }

        serverNamePreference.setSummary(serverNamePreference.getText());

        final EditTextPreference serverUrlPreference = new EditTextPreference(context);
        serverUrlPreference.setKey(Constants.PREFERENCES_KEY_SERVER_URL + instance);
        serverUrlPreference.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        serverUrlPreference.setDefaultValue("http://yourhost");
        serverUrlPreference.setTitle(R.string.settings_server_address);

        if (serverUrlPreference.getText() == null) {
            serverUrlPreference.setText("http://yourhost");
        }

        serverUrlPreference.setSummary(serverUrlPreference.getText());

        if (screen != null) {
            screen.setSummary(serverUrlPreference.getText());
        }

        final EditTextPreference serverUsernamePreference = new EditTextPreference(context);
        serverUsernamePreference.setKey(Constants.PREFERENCES_KEY_USERNAME + instance);
        serverUsernamePreference.setTitle(R.string.settings_server_username);

        final EditTextPreference serverPasswordPreference = new EditTextPreference(context);
        serverPasswordPreference.setKey(Constants.PREFERENCES_KEY_PASSWORD + instance);
        serverPasswordPreference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        serverPasswordPreference.setSummary("***");
        serverPasswordPreference.setTitle(R.string.settings_server_password);

        final CheckBoxPreference serverEnabledPreference = new CheckBoxPreference(context);
        serverEnabledPreference.setDefaultValue(true);
        serverEnabledPreference.setKey(Constants.PREFERENCES_KEY_SERVER_ENABLED + instance);
        serverEnabledPreference.setTitle(R.string.equalizer_enabled);

        final CheckBoxPreference jukeboxEnabledPreference = new CheckBoxPreference(context);
        jukeboxEnabledPreference.setDefaultValue(false);
        jukeboxEnabledPreference.setKey(Constants.PREFERENCES_KEY_JUKEBOX_BY_DEFAULT + instance);
        jukeboxEnabledPreference.setTitle(R.string.jukebox_is_default);

        Preference serverRemoveServerPreference = new Preference(context);
        serverRemoveServerPreference.setKey(Constants.PREFERENCES_KEY_REMOVE_SERVER + instance);
        serverRemoveServerPreference.setPersistent(false);
        serverRemoveServerPreference.setTitle(R.string.settings_server_remove_server);

        serverRemoveServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (activeServers == 0) {
                    return false;
                }

                // Reset values to null so when we ask for them again they are new
                serverNamePreference.setText(null);
                serverUrlPreference.setText(null);
                serverUsernamePreference.setText(null);
                serverPasswordPreference.setText(null);
                serverEnabledPreference.setChecked(true);
                jukeboxEnabledPreference.setChecked(false);

                if (instance < activeServers) {

                    int activeServer = Util.getActiveServer(getActivity());
                    for (int i = instance; i <= activeServers; i++) {
                        Util.removeInstanceName(getActivity(), i, activeServer);
                    }
                }

                activeServers--;

                if (screen != null) {
                    serversCategory.removePreference(screen);
                    Dialog dialog = screen.getDialog();

                    if (dialog != null) {
                        dialog.dismiss();
                    }
                }

                settings.edit()
                        .putInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, activeServers)
                        .apply();

                addServerPreference.setEnabled(activeServers < maxServerCount);

                return true;
            }
        });

        Preference serverTestConnectionPreference = new Preference(getActivity());
        serverTestConnectionPreference.setKey(Constants.PREFERENCES_KEY_TEST_CONNECTION + instance);
        serverTestConnectionPreference.setPersistent(false);
        serverTestConnectionPreference.setTitle(R.string.settings_test_connection_title);

        serverTestConnectionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                testConnection(instance);
                return false;
            }
        });

        if (screen != null) {
            screen.addPreference(serverNamePreference);
            screen.addPreference(serverUrlPreference);
            screen.addPreference(serverUsernamePreference);
            screen.addPreference(serverPasswordPreference);
            screen.addPreference(serverEnabledPreference);
            screen.addPreference(jukeboxEnabledPreference);
            screen.addPreference(serverRemoveServerPreference);
            screen.addPreference(serverTestConnectionPreference);
        }

        return screen;
    }

    private void testConnection(final int instance) {
        ModalBackgroundTask<Boolean> task = new ModalBackgroundTask<Boolean>(getActivity(), false) {
            private int previousInstance;

            @Override
            protected Boolean doInBackground() throws Throwable {
                updateProgress(R.string.settings_testing_connection);

                final Context context = getActivity();
                previousInstance = Util.getActiveServer(context);
                testingConnection = true;
                Util.setActiveServer(context, instance);
                try {
                    MusicService musicService = MusicServiceFactory.getMusicService(context);
                    musicService.ping(context, this);
                    return musicService.isLicenseValid(context, null);
                } finally {
                    Util.setActiveServer(context, previousInstance);
                    testingConnection = false;
                }
            }

            @Override
            protected void done(Boolean licenseValid) {
                if (licenseValid) {
                    Util.toast(getActivity(), R.string.settings_testing_ok);
                } else {
                    Util.toast(getActivity(), R.string.settings_testing_unlicensed);
                }
            }

            @Override
            protected void cancel() {
                super.cancel();
                Util.setActiveServer(getActivity(), previousInstance);
            }

            @Override
            protected void error(Throwable error) {
                Log.w(LOG_TAG, error.toString(), error);
                new ErrorDialog(getActivity(), String.format("%s %s", getResources().getString(R.string.settings_connection_failure), getErrorMessage(error)), false);
            }
        };
        task.execute();
    }

    private class ServerSettings {
        private EditTextPreference serverName;
        private EditTextPreference serverUrl;
        private EditTextPreference username;
        private PreferenceScreen screen;

        private ServerSettings(String instance) {
            screen = (PreferenceScreen) findPreference("server" + instance);
            serverName = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_SERVER_NAME +
                    instance);
            serverUrl = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_SERVER_URL +
                    instance);
            username = (EditTextPreference) findPreference(Constants.PREFERENCES_KEY_USERNAME +
                    instance);

            serverUrl.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    try {
                        String url = (String) value;
                        new URL(url);
                        if (!url.equals(url.trim()) || url.contains("@")) {
                            throw new Exception();
                        }
                    } catch (Exception x) {
                        new ErrorDialog(getActivity(), R.string.settings_invalid_url, false);
                        return false;
                    }
                    return true;
                }
            });

            username.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    String username = (String) value;
                    if (username == null || !username.equals(username.trim())) {
                        new ErrorDialog(getActivity(), R.string.settings_invalid_username, false);
                        return false;
                    }
                    return true;
                }
            });
        }

        public void update() {
            serverName.setSummary(serverName.getText());
            serverUrl.setSummary(serverUrl.getText());
            username.setSummary(username.getText());
            screen.setSummary(serverUrl.getText());
            screen.setTitle(serverName.getText());
        }
    }

    private void update() {
        if (testingConnection) {
            return;
        }

        theme.setSummary(theme.getEntry());
        videoPlayer.setSummary(videoPlayer.getEntry());
        maxBitrateWifi.setSummary(maxBitrateWifi.getEntry());
        maxBitrateMobile.setSummary(maxBitrateMobile.getEntry());
        cacheSize.setSummary(cacheSize.getEntry());
        cacheLocation.setSummary(cacheLocation.getText());
        preloadCount.setSummary(preloadCount.getEntry());
        bufferLength.setSummary(bufferLength.getEntry());
        incrementTime.setSummary(incrementTime.getEntry());
        networkTimeout.setSummary(networkTimeout.getEntry());
        maxAlbums.setSummary(maxAlbums.getEntry());
        maxArtists.setSummary(maxArtists.getEntry());
        maxSongs.setSummary(maxSongs.getEntry());
        defaultAlbums.setSummary(defaultAlbums.getEntry());
        defaultArtists.setSummary(defaultArtists.getEntry());
        defaultSongs.setSummary(defaultSongs.getEntry());
        chatRefreshInterval.setSummary(chatRefreshInterval.getEntry());
        directoryCacheTime.setSummary(directoryCacheTime.getEntry());
        viewRefresh.setSummary(viewRefresh.getEntry());
        imageLoaderConcurrency.setSummary(imageLoaderConcurrency.getEntry());
        sharingDefaultExpiration.setSummary(sharingDefaultExpiration.getText());
        sharingDefaultDescription.setSummary(sharingDefaultDescription.getText());
        sharingDefaultGreeting.setSummary(sharingDefaultGreeting.getText());

        if (!mediaButtonsEnabled.isChecked()) {
            lockScreenEnabled.setChecked(false);
            lockScreenEnabled.setEnabled(false);
        }

        if (!sendBluetoothNotifications.isChecked()) {
            sendBluetoothAlbumArt.setChecked(false);
            sendBluetoothAlbumArt.setEnabled(false);
        }

        for (ServerSettings ss : serverSettings.values()) {
            ss.update();
        }
    }

    private static void setImageLoaderConcurrency(int concurrency) {
        SubsonicTabActivity instance = SubsonicTabActivity.getInstance();

        if (instance != null) {
            ImageLoader imageLoader = instance.getImageLoader();

            if (imageLoader != null) {
                imageLoader.stopImageLoader();
                imageLoader.setConcurrency(concurrency);
            }
        }
    }

    private void setHideMedia(boolean hide) {
        File nomediaDir = new File(FileUtil.getUltraSonicDirectory(), ".nomedia");
        if (hide && !nomediaDir.exists()) {
            if (!nomediaDir.mkdir()) {
                Log.w(LOG_TAG, "Failed to create " + nomediaDir);
            }
        } else if (nomediaDir.exists()) {
            if (!nomediaDir.delete()) {
                Log.w(LOG_TAG, "Failed to delete " + nomediaDir);
            }
        }
        Util.toast(getActivity(), R.string.settings_hide_media_toast, false);
    }

    private void setMediaButtonsEnabled(boolean enabled) {
        if (enabled) {
            lockScreenEnabled.setEnabled(true);
            Util.registerMediaButtonEventReceiver(getActivity());
        } else {
            lockScreenEnabled.setEnabled(false);
            Util.unregisterMediaButtonEventReceiver(getActivity());
        }
    }

    private void setBluetoothPreferences(boolean enabled) {
        if (enabled) {
            sendBluetoothAlbumArt.setEnabled(true);
        } else {
            sendBluetoothAlbumArt.setEnabled(false);
        }
    }

    private void setCacheLocation(String path) {
        File dir = new File(path);

        if (!FileUtil.ensureDirectoryExistsAndIsReadWritable(dir)) {
            Util.toast(getActivity(), R.string.settings_cache_location_error, false);

            // Reset it to the default.
            String defaultPath = FileUtil.getDefaultMusicDirectory().getPath();
            if (!defaultPath.equals(path)) {
                Util.getPreferences(getActivity()).edit()
                        .putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, defaultPath)
                        .apply();
                cacheLocation.setSummary(defaultPath);
                cacheLocation.setText(defaultPath);
            }

            // Clear download queue.
            DownloadService downloadService = DownloadServiceImpl.getInstance();
            downloadService.clear();
        }
    }
}
