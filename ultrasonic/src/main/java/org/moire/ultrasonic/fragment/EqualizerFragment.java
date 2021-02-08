package org.moire.ultrasonic.fragment;

import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import org.jetbrains.annotations.NotNull;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.audiofx.EqualizerController;
import org.moire.ultrasonic.util.Util;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class EqualizerFragment extends Fragment {

    private static final int MENU_GROUP_PRESET = 100;

    private final Map<Short, SeekBar> bars = new HashMap<>();
    private EqualizerController equalizerController;
    private Equalizer equalizer;
    private LinearLayout equalizerLayout;
    private View presetButton;
    private  CheckBox enabledCheckBox;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.equalizer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FragmentTitle.Companion.setTitle(this, R.string.equalizer_label);
        equalizerLayout = view.findViewById(R.id.equalizer_layout);
        presetButton = view.findViewById(R.id.equalizer_preset);
        enabledCheckBox = view.findViewById(R.id.equalizer_enabled);

        EqualizerController.get().observe(getViewLifecycleOwner(), new Observer<EqualizerController>() {
            @Override
            public void onChanged(EqualizerController controller) {
                if (controller != null) {
                    Timber.d("EqualizerController Observer.onChanged received controller");
                    equalizerController = controller;
                    equalizer = controller.equalizer;
                    setup();
                } else {
                    Timber.d("EqualizerController Observer.onChanged has no controller");
                    equalizerController = null;
                    equalizer = null;
                }
            }
        });
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (equalizerController == null) return;
        equalizerController.saveSettings();
    }

    @Override
    public void onCreateContextMenu(@NotNull ContextMenu menu, @NotNull View view, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        if (equalizer == null) return;

        short currentPreset;
        try
        {
            currentPreset = equalizer.getCurrentPreset();
        }
        catch (Exception x)
        {
            currentPreset = -1;
        }

        for (short preset = 0; preset < equalizer.getNumberOfPresets(); preset++)
        {
            MenuItem menuItem = menu.add(MENU_GROUP_PRESET, preset, preset, equalizer.getPresetName(preset));
            if (preset == currentPreset)
            {
                menuItem.setChecked(true);
            }
        }
        menu.setGroupCheckable(MENU_GROUP_PRESET, true, true);
    }

    @Override
    public boolean onContextItemSelected(@NotNull MenuItem menuItem)
    {
        if (equalizer == null) return true;
        try
        {
            short preset = (short) menuItem.getItemId();
            equalizer.usePreset(preset);
            updateBars();
        }
        catch (Exception ex)
        {
            //TODO: Show a dialog
            Timber.i(ex, "An exception has occurred in EqualizerFragment onContextItemSelected");
        }

        return true;
    }

    private void setup()
    {
        initEqualizer();

        registerForContextMenu(presetButton);
        presetButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                presetButton.showContextMenu();
            }
        });

        enabledCheckBox.setChecked(equalizer.getEnabled());
        enabledCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b)
            {
                setEqualizerEnabled(b);
            }
        });
    }

    private void setEqualizerEnabled(boolean enabled)
    {
        if (equalizer == null) return;
        equalizer.setEnabled(enabled);
        updateBars();
    }

    private void updateBars()
    {
        if (equalizer == null) return;
        try
        {
            for (Map.Entry<Short, SeekBar> entry : bars.entrySet())
            {
                short band = entry.getKey();
                SeekBar bar = entry.getValue();
                bar.setEnabled(equalizer.getEnabled());
                short minEQLevel = equalizer.getBandLevelRange()[0];
                bar.setProgress(equalizer.getBandLevel(band) - minEQLevel);
            }
        }
        catch (Exception ex)
        {
            //TODO: Show a dialog
            Timber.i(ex, "An exception has occurred in EqualizerFragment updateBars");
        }
    }

    private void initEqualizer()
    {
        if (equalizer == null) return;

        try
        {
            short[] bandLevelRange = equalizer.getBandLevelRange();
            short numberOfBands = equalizer.getNumberOfBands();

            final short minEQLevel = bandLevelRange[0];
            final short maxEQLevel = bandLevelRange[1];

            for (short i = 0; i < numberOfBands; i++)
            {
                final short band = i;

                View bandBar = LayoutInflater.from(getContext()).inflate(R.layout.equalizer_bar, null);
                TextView freqTextView;

                if (bandBar != null)
                {
                    freqTextView = (TextView) bandBar.findViewById(R.id.equalizer_frequency);
                    final TextView levelTextView = (TextView) bandBar.findViewById(R.id.equalizer_level);
                    SeekBar bar = (SeekBar) bandBar.findViewById(R.id.equalizer_bar);

                    freqTextView.setText((equalizer.getCenterFreq(band) / 1000) + " Hz");

                    bars.put(band, bar);
                    bar.setMax(maxEQLevel - minEQLevel);
                    short level = equalizer.getBandLevel(band);
                    bar.setProgress(level - minEQLevel);
                    bar.setEnabled(equalizer.getEnabled());
                    updateLevelText(levelTextView, level);

                    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
                    {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
                        {
                            short level = (short) (progress + minEQLevel);
                            if (fromUser)
                            {
                                try
                                {
                                    equalizer.setBandLevel(band, level);
                                }
                                catch (Exception ex)
                                {
                                    //TODO: Show a dialog?
                                    Timber.i(ex, "An exception has occurred in Equalizer onProgressChanged");
                                }
                            }
                            updateLevelText(levelTextView, level);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar)
                        {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar)
                        {
                        }
                    });

                    equalizerLayout.addView(bandBar);
                }
            }
        }
        catch (Exception ex)
        {
            //TODO: Show a dialog?
            Timber.i(ex, "An exception has occurred while initializing Equalizer");
        }
    }

    private static void updateLevelText(TextView levelTextView, short level)
    {
        if (levelTextView != null)
        {
            levelTextView.setText(String.format("%s%d dB", level > 0 ? "+" : "", level / 100));
        }
    }
}
