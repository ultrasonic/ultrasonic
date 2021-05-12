package org.moire.ultrasonic.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.MusicDirectory;
import org.moire.ultrasonic.domain.PlayerState;
import org.moire.ultrasonic.service.DownloadFile;
import org.moire.ultrasonic.service.MediaPlayerController;
import org.moire.ultrasonic.subsonic.ImageLoaderProvider;
import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.NowPlayingEventDistributor;
import org.moire.ultrasonic.util.NowPlayingEventListener;
import org.moire.ultrasonic.util.Util;

import kotlin.Lazy;
import timber.log.Timber;

import static org.koin.java.KoinJavaComponent.inject;


/**
 * Contains the mini-now playing information box displayed at the bottom of the screen
 */
public class NowPlayingFragment extends Fragment {

    private static final int MIN_DISTANCE = 30;
    private float downX;
    private float downY;
    ImageView playButton;
    ImageView nowPlayingAlbumArtImage;
    TextView nowPlayingTrack;
    TextView nowPlayingArtist;

    private final Lazy<MediaPlayerController> mediaPlayerControllerLazy = inject(MediaPlayerController.class);
    private final Lazy<ImageLoaderProvider> imageLoader = inject(ImageLoaderProvider.class);
    private final Lazy<NowPlayingEventDistributor> nowPlayingEventDistributor = inject(NowPlayingEventDistributor.class);
    private NowPlayingEventListener nowPlayingEventListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Util.applyTheme(this.getContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.now_playing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {

        playButton = view.findViewById(R.id.now_playing_control_play);
        nowPlayingAlbumArtImage = view.findViewById(R.id.now_playing_image);
        nowPlayingTrack = view.findViewById(R.id.now_playing_trackname);
        nowPlayingArtist = view.findViewById(R.id.now_playing_artist);

        nowPlayingEventListener = new NowPlayingEventListener() {
            @Override
            public void onDismissNowPlaying() { }
            @Override
            public void onHideNowPlaying() { }
            @Override
            public void onShowNowPlaying() { update(); }
        };

        nowPlayingEventDistributor.getValue().subscribe(nowPlayingEventListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nowPlayingEventDistributor.getValue().unsubscribe(nowPlayingEventListener);
    }

    private void update() {
        try
        {
            PlayerState playerState = mediaPlayerControllerLazy.getValue().getPlayerState();
            if (playerState == PlayerState.PAUSED) {
                playButton.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.media_play));
            } else if (playerState == PlayerState.STARTED) {
                playButton.setImageDrawable(Util.getDrawableFromAttribute(getContext(), R.attr.media_pause));
            }

            DownloadFile file = mediaPlayerControllerLazy.getValue().getCurrentPlaying();
            if (file != null) {
                final MusicDirectory.Entry song = file.getSong();
                String title = song.getTitle();
                String artist = song.getArtist();

                imageLoader.getValue().getImageLoader().loadImage(nowPlayingAlbumArtImage, song, false, Util.getNotificationImageSize(getContext()), false, true);
                nowPlayingTrack.setText(title);
                nowPlayingArtist.setText(artist);

                nowPlayingAlbumArtImage.setOnClickListener(v -> {
                    Bundle bundle = new Bundle();

                    if (Util.getShouldUseId3Tags()) {
                        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, true);
                        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, song.getAlbumId());
                    } else {
                        bundle.putBoolean(Constants.INTENT_EXTRA_NAME_IS_ALBUM, false);
                        bundle.putString(Constants.INTENT_EXTRA_NAME_ID, song.getParent());
                    }

                    bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, song.getAlbum());
                    bundle.putString(Constants.INTENT_EXTRA_NAME_NAME, song.getAlbum());
                    Navigation.findNavController(getActivity(), R.id.nav_host_fragment).navigate(R.id.trackCollectionFragment, bundle);
                });
            }

            getView().setOnTouchListener((v, event) -> handleOnTouch(event));

            // This empty onClickListener is necessary for the onTouchListener to work
            getView().setOnClickListener(v -> {});

            playButton.setOnClickListener(v -> mediaPlayerControllerLazy.getValue().togglePlayPause());
        }
        catch (Exception x) {
            Timber.w(x, "Failed to get notification cover art");
        }
    }

    private boolean handleOnTouch(MotionEvent event) {
        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
            {
                downX = event.getX();
                downY = event.getY();
                return false;
            }
            case MotionEvent.ACTION_UP:
            {
                float upX = event.getX();
                float upY = event.getY();

                float deltaX = downX - upX;
                float deltaY = downY - upY;

                if (Math.abs(deltaX) > MIN_DISTANCE)
                {
                    // left or right
                    if (deltaX < 0)
                    {
                        mediaPlayerControllerLazy.getValue().previous();
                        return false;
                    }
                    if (deltaX > 0)
                    {
                        mediaPlayerControllerLazy.getValue().next();
                        return false;
                    }
                }
                else if (Math.abs(deltaY) > MIN_DISTANCE)
                {
                    if (deltaY < 0)
                    {
                        nowPlayingEventDistributor.getValue().raiseNowPlayingDismissedEvent();
                        return false;
                    }
                    if (deltaY > 0)
                    {
                        return false;
                    }
                }
                Navigation.findNavController(getActivity(), R.id.nav_host_fragment).navigate(R.id.playerFragment);
                return false;
            }
        }
        return false;
    }
}
