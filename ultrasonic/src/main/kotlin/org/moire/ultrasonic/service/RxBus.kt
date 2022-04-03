package org.moire.ultrasonic.service

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import org.moire.ultrasonic.domain.PlayerState

class RxBus {
    companion object {
        var mediaSessionTokenPublisher: PublishSubject<MediaSessionCompat.Token> =
            PublishSubject.create()
        val mediaSessionTokenObservable: Observable<MediaSessionCompat.Token> =
            mediaSessionTokenPublisher.observeOn(AndroidSchedulers.mainThread())
                .replay(1)
                .autoConnect(0)

        val themeChangedEventPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val themeChangedEventObservable: Observable<Unit> =
            themeChangedEventPublisher.observeOn(AndroidSchedulers.mainThread())

        val musicFolderChangedEventPublisher: PublishSubject<String> =
            PublishSubject.create()
        val musicFolderChangedEventObservable: Observable<String> =
            musicFolderChangedEventPublisher.observeOn(AndroidSchedulers.mainThread())

        val playerStatePublisher: PublishSubject<StateWithTrack> =
            PublishSubject.create()
        val playerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher.observeOn(AndroidSchedulers.mainThread())
                .replay(1)
                .autoConnect(0)

        val playlistPublisher: PublishSubject<List<DownloadFile>> =
            PublishSubject.create()
        val playlistObservable: Observable<List<DownloadFile>> =
            playlistPublisher.observeOn(AndroidSchedulers.mainThread())
                .replay(1)
                .autoConnect(0)

        val playbackPositionPublisher: PublishSubject<Int> =
            PublishSubject.create()
        val playbackPositionObservable: Observable<Int> =
            playbackPositionPublisher.observeOn(AndroidSchedulers.mainThread())
                .throttleFirst(1, TimeUnit.SECONDS)
                .replay(1)
                .autoConnect(0)

        // Commands
        val dismissNowPlayingCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val dismissNowPlayingCommandObservable: Observable<Unit> =
            dismissNowPlayingCommandPublisher.observeOn(AndroidSchedulers.mainThread())

        val playFromMediaIdCommandPublisher: PublishSubject<Pair<String?, Bundle?>> =
            PublishSubject.create()
        val playFromMediaIdCommandObservable: Observable<Pair<String?, Bundle?>> =
            playFromMediaIdCommandPublisher.observeOn(AndroidSchedulers.mainThread())

        val playFromSearchCommandPublisher: PublishSubject<Pair<String?, Bundle?>> =
            PublishSubject.create()
        val playFromSearchCommandObservable: Observable<Pair<String?, Bundle?>> =
            playFromSearchCommandPublisher.observeOn(AndroidSchedulers.mainThread())

        val skipToQueueItemCommandPublisher: PublishSubject<Long> =
            PublishSubject.create()
        val skipToQueueItemCommandObservable: Observable<Long> =
            skipToQueueItemCommandPublisher.observeOn(AndroidSchedulers.mainThread())

        fun releaseMediaSessionToken() {
            mediaSessionTokenPublisher = PublishSubject.create()
        }
    }

    data class StateWithTrack(val state: PlayerState, val track: DownloadFile?, val index: Int = -1)
}

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    this.add(disposable)
}
