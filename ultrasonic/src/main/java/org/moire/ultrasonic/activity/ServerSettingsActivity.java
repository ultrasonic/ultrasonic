package org.moire.ultrasonic.activity;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.fragment.ServerSettingsFragment;
import org.moire.ultrasonic.util.Util;

public class ServerSettingsActivity extends AppCompatActivity {
    public static final String ARG_SERVER_ID = "argServerId";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);

        final Bundle extras = getIntent().getExtras();
        if (!extras.containsKey(ARG_SERVER_ID)) {
            finish();
            return;
        }

        if (savedInstanceState == null) {
            configureActionBar();

            final int serverId = extras.getInt(ARG_SERVER_ID);
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, ServerSettingsFragment.newInstance(serverId))
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyTheme() {
        String theme = Util.getTheme(this);

        if ("dark".equalsIgnoreCase(theme) || "fullscreen".equalsIgnoreCase(theme)) {
            setTheme(R.style.UltraSonicTheme);
        } else if ("light".equalsIgnoreCase(theme) || "fullscreenlight".equalsIgnoreCase(theme)) {
            setTheme(R.style.UltraSonicTheme_Light);
        }
    }

    private void configureActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
}
