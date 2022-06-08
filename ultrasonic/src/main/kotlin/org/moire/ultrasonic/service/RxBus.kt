package org.moire.ultrasonic.service

import android.os.Looper
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class RxBus {

    companion object {

        private fun mainThread() = AndroidSchedulers.from(Looper.getMainLooper())

        var activeServerChangePublisher: PublishSubject<Int> =
            PublishSubject.create()
        var activeServerChangeObservable: Observable<Int> =
            activeServerChangePublisher.observeOn(mainThread())

        val themeChangedEventPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val themeChangedEventObservable: Observable<Unit> =
            themeChangedEventPublisher.observeOn(mainThread())

        val musicFolderChangedEventPublisher: PublishSubject<String> =
            PublishSubject.create()
        val musicFolderChangedEventObservable: Observable<String> =
            musicFolderChangedEventPublisher.observeOn(mainThread())

        val playerStatePublisher: PublishSubject<StateWithTrack> =
            PublishSubject.create()
        val playerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
        val throttledPlayerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
                .throttleLatest(300, TimeUnit.MILLISECONDS)

        val playlistPublisher: PublishSubject<List<DownloadFile>> =
            PublishSubject.create()
        val playlistObservable: Observable<List<DownloadFile>> =
            playlistPublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
        val throttledPlaylistObservable: Observable<List<DownloadFile>> =
            playlistPublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
                .throttleLatest(300, TimeUnit.MILLISECONDS)

        // Commands
        val dismissNowPlayingCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val dismissNowPlayingCommandObservable: Observable<Unit> =
            dismissNowPlayingCommandPublisher.observeOn(mainThread())

        val shutdownCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val shutdownCommandObservable: Observable<Unit> =
            shutdownCommandPublisher.observeOn(mainThread())

        val stopCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val stopCommandObservable: Observable<Unit> =
            stopCommandPublisher.observeOn(mainThread())

    }

    data class StateWithTrack(
        val track: DownloadFile?,
        val index: Int = -1,
        val isPlaying: Boolean = false,
        val state: Int
    )
}

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    this.add(disposable)
}
