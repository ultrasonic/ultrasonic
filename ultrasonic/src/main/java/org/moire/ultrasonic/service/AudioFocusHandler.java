package org.moire.ultrasonic.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import timber.log.Timber;

import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Util;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

public class AudioFocusHandler
{
    private static boolean hasFocus;
    private static boolean pauseFocus;
    private static boolean lowerFocus;

    // TODO: This is a circular reference, try to remove it
    private Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);
    private Context context;

    public AudioFocusHandler(Context context)
    {
        this.context = context;
    }

    public void requestAudioFocus()
    {
        if (!hasFocus)
        {
            final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            hasFocus = true;
            audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener()
            {
                @Override
                public void onAudioFocusChange(int focusChange)
                {
                    MediaPlayerController mediaPlayerController = mediaPlayerControllerLazy.getValue();
                    if ((focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) && !mediaPlayerController.isJukeboxEnabled())
                    {
                        Timber.v("Lost Audio Focus");
                        if (mediaPlayerController.getPlayerState() == PlayerState.STARTED)
                        {
                            SharedPreferences preferences = Util.getPreferences(context);
                            int lossPref = Integer.parseInt(preferences.getString(Constants.PREFERENCES_KEY_TEMP_LOSS, "1"));
                            if (lossPref == 2 || (lossPref == 1 && focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
                            {
                                lowerFocus = true;
                                mediaPlayerController.setVolume(0.1f);
                            }
                            else if (lossPref == 0 || (lossPref == 1))
                            {
                                pauseFocus = true;
                                mediaPlayerController.pause();
                            }
                        }
                    }
                    else if (focusChange == AudioManager.AUDIOFOCUS_GAIN)
                    {
                        Timber.v("Regained Audio Focus");
                        if (pauseFocus)
                        {
                            pauseFocus = false;
                            mediaPlayerController.start();
                        }
                        else if (lowerFocus)
                        {
                            lowerFocus = false;
                            mediaPlayerController.setVolume(1.0f);
                        }
                    }
                    else if (focusChange == AudioManager.AUDIOFOCUS_LOSS && !mediaPlayerController.isJukeboxEnabled())
                    {
                        hasFocus = false;
                        mediaPlayerController.pause();
                        audioManager.abandonAudioFocus(this);
                        Timber.v("Abandoned Audio Focus");
                    }
                }
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            Timber.v("Got Audio Focus");
        }
    }
}
