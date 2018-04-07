package org.moire.ultrasonic.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import org.moire.ultrasonic.BuildConfig;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.cache.PermanentFileStorage;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.ErrorDialog;
import org.moire.ultrasonic.util.ModalBackgroundTask;
import org.moire.ultrasonic.util.Util;

import java.net.URL;

/**
 * Settings for Subsonic server.
 */
public class ServerSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {
    private static final String LOG_TAG = ServerSettingsFragment.class.getSimpleName();
    private static final String ARG_SERVER_ID = "serverId";

    private EditTextPreference serverNamePref;
    private EditTextPreference serverUrlPref;
    private EditTextPreference serverUsernamePref;
    private EditTextPreference serverPasswordPref;
    private CheckBoxPreference equalizerPref;
    private CheckBoxPreference jukeboxPref;
    private CheckBoxPreference allowSelfSignedCertificatePref;
    private CheckBoxPreference enableLdapUserSupportPref;
    private Preference removeServerPref;
    private Preference testConnectionPref;

    private int serverId;
    private SharedPreferences sharedPreferences;

    public static ServerSettingsFragment newInstance(final int serverId) {
        final ServerSettingsFragment fragment = new ServerSettingsFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_SERVER_ID, serverId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serverId = getArguments().getInt(ARG_SERVER_ID);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        addPreferencesFromResource(R.xml.server_settings);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        serverNamePref = (EditTextPreference) findPreference(getString(R.string.settings_server_name));
        serverUrlPref = (EditTextPreference) findPreference(getString(R.string.settings_server_address));
        serverUsernamePref = (EditTextPreference) findPreference(getString(R.string.settings_server_username));
        serverPasswordPref = (EditTextPreference) findPreference(getString(R.string.settings_server_password));
        equalizerPref = (CheckBoxPreference) findPreference(getString(R.string.equalizer_enabled));
        jukeboxPref = (CheckBoxPreference) findPreference(getString(R.string.jukebox_is_default));
        removeServerPref = findPreference(getString(R.string.settings_server_remove_server));
        testConnectionPref = findPreference(getString(R.string.settings_test_connection_title));
        allowSelfSignedCertificatePref = (CheckBoxPreference) findPreference(
                getString(R.string.settings_allow_self_signed_certificate));
        enableLdapUserSupportPref = (CheckBoxPreference) findPreference(
                getString(R.string.settings_enable_ldap_user_support)
        );

        setupPreferencesValues();
        setupPreferencesListeners();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == serverNamePref) {
            sharedPreferences.edit()
                    .putString(Constants.PREFERENCES_KEY_SERVER_NAME + serverId, (String) newValue)
                    .apply();
            updateName();
            return true;
        } else if (preference == serverUrlPref) {
            final String url = (String) newValue;
            try {
                new URL(url);
                if (!url.equals(url.trim()) || url.contains("@")) {
                    throw new Exception();
                }
            } catch (Exception x) {
                new ErrorDialog(getActivity(), R.string.settings_invalid_url, false);
                return false;
            }

            sharedPreferences.edit()
                    .putString(Constants.PREFERENCES_KEY_SERVER_URL + serverId, url)
                    .apply();
            updateUrl();
            return true;
        } else if (preference == serverUsernamePref) {
            String username = (String) newValue;
            if (username == null || !username.equals(username.trim())) {
                new ErrorDialog(getActivity(), R.string.settings_invalid_username, false);
                return false;
            }

            sharedPreferences.edit()
                    .putString(Constants.PREFERENCES_KEY_USERNAME + serverId, username)
                    .apply();
            updateUsername();
            return true;
        } else if (preference == serverPasswordPref) {
            sharedPreferences.edit()
                    .putString(Constants.PREFERENCES_KEY_PASSWORD + serverId, (String) newValue)
                    .apply();
            updatePassword();
            return true;
        } else if (preference == equalizerPref) {
            sharedPreferences.edit()
                    .putBoolean(Constants.PREFERENCES_KEY_SERVER_ENABLED + serverId, (Boolean) newValue)
                    .apply();
            return true;
        } else if (preference == jukeboxPref) {
            sharedPreferences.edit()
                    .putBoolean(Constants.PREFERENCES_KEY_JUKEBOX_BY_DEFAULT + serverId, (Boolean) newValue)
                    .apply();
            return true;
        } else if (preference == allowSelfSignedCertificatePref) {
            sharedPreferences.edit()
                    .putBoolean(Constants.PREFERENCES_KEY_ALLOW_SELF_SIGNED_CERTIFICATE + serverId, (Boolean) newValue)
                    .apply();
            return true;
        } else if (preference == enableLdapUserSupportPref) {
            sharedPreferences.edit()
                    .putBoolean(Constants.PREFERENCES_KEY_LDAP_SUPPORT + serverId, (Boolean) newValue)
                    .apply();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == removeServerPref) {
            removeServer();
            return true;
        } else if (preference == testConnectionPref) {
            testConnection();
            return true;
        }
        return false;
    }

    private void setupPreferencesValues() {
        updateName();
        updateUrl();
        updateUsername();
        updatePassword();

        if (!sharedPreferences.contains(Constants.PREFERENCES_KEY_SERVER_ENABLED + serverId)) {
            sharedPreferences.edit()
                    .putBoolean(Constants.PREFERENCES_KEY_SERVER_ENABLED + serverId, true)
                    .apply();
        }
        equalizerPref.setChecked(sharedPreferences
                .getBoolean(Constants.PREFERENCES_KEY_SERVER_ENABLED + serverId, true));

        jukeboxPref.setChecked(sharedPreferences
                .getBoolean(Constants.PREFERENCES_KEY_JUKEBOX_BY_DEFAULT + serverId, false));

        allowSelfSignedCertificatePref.setChecked(sharedPreferences
                .getBoolean(Constants.PREFERENCES_KEY_ALLOW_SELF_SIGNED_CERTIFICATE + serverId, false));

        enableLdapUserSupportPref.setChecked(sharedPreferences
                .getBoolean(Constants.PREFERENCES_KEY_LDAP_SUPPORT + serverId, false));
    }

    private void updatePassword() {
        serverPasswordPref.setText(sharedPreferences
                .getString(Constants.PREFERENCES_KEY_PASSWORD + serverId,
                        ""));
    }

    private void updateUsername() {
        serverUsernamePref.setText(sharedPreferences
                .getString(Constants.PREFERENCES_KEY_USERNAME + serverId,
                        ""));
    }

    private void updateUrl() {
        final String serverUrl = sharedPreferences
                .getString(Constants.PREFERENCES_KEY_SERVER_URL + serverId,
                        "http://");
        serverUrlPref.setText(serverUrl);
        serverUrlPref.setSummary(serverUrl);
    }

    private void updateName() {
        final String serverName = sharedPreferences
                .getString(Constants.PREFERENCES_KEY_SERVER_NAME + serverId,
                        "");
        serverNamePref.setText(serverName);
        serverNamePref.setSummary(serverName);
    }

    private void setupPreferencesListeners() {
        serverNamePref.setOnPreferenceChangeListener(this);
        serverUrlPref.setOnPreferenceChangeListener(this);
        serverUsernamePref.setOnPreferenceChangeListener(this);
        serverPasswordPref.setOnPreferenceChangeListener(this);
        equalizerPref.setOnPreferenceChangeListener(this);
        jukeboxPref.setOnPreferenceChangeListener(this);
        allowSelfSignedCertificatePref.setOnPreferenceChangeListener(this);
        enableLdapUserSupportPref.setOnPreferenceChangeListener(this);

        removeServerPref.setOnPreferenceClickListener(this);
        testConnectionPref.setOnPreferenceClickListener(this);
    }

    private void testConnection() {
        ModalBackgroundTask<Boolean> task = new ModalBackgroundTask<Boolean>(getActivity(), false) {
            private int previousInstance;

            @Override
            protected Boolean doInBackground() throws Throwable {
                updateProgress(R.string.settings_testing_connection);

                final Context context = getActivity();
                previousInstance = Util.getActiveServer(context);
                Util.setActiveServer(context, serverId);
                try {
                    MusicService musicService = MusicServiceFactory.getMusicService(context);
                    musicService.ping(context, this);
                    return musicService.isLicenseValid(context, null);
                } finally {
                    Util.setActiveServer(context, previousInstance);
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

    private void removeServer() {
        int activeServers = sharedPreferences
                .getInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, 0);

        // Clear permanent storage
        final String storageServerId = MusicServiceFactory.getServerId(sharedPreferences, serverId);
        final PermanentFileStorage fileStorage = new PermanentFileStorage(
                MusicServiceFactory.getDirectories(getActivity()),
                storageServerId,
                BuildConfig.DEBUG
        );
        fileStorage.clearAll();

        // Reset values to null so when we ask for them again they are new
        sharedPreferences.edit()
                .remove(Constants.PREFERENCES_KEY_SERVER_NAME + serverId)
                .remove(Constants.PREFERENCES_KEY_SERVER_URL + serverId)
                .remove(Constants.PREFERENCES_KEY_USERNAME + serverId)
                .remove(Constants.PREFERENCES_KEY_PASSWORD + serverId)
                .remove(Constants.PREFERENCES_KEY_SERVER_ENABLED + serverId)
                .remove(Constants.PREFERENCES_KEY_JUKEBOX_BY_DEFAULT + serverId)
                .remove(Constants.PREFERENCES_KEY_ALLOW_SELF_SIGNED_CERTIFICATE + serverId)
                .apply();

        if (serverId < activeServers) {
            int activeServer = Util.getActiveServer(getActivity());
            for (int i = serverId; i <= activeServers; i++) {
                Util.removeInstanceName(getActivity(), i, activeServer);
            }
        }

        activeServers--;

        sharedPreferences.edit()
                .putInt(Constants.PREFERENCES_KEY_ACTIVE_SERVERS, activeServers)
                .apply();

        getActivity().finish();
    }
}
