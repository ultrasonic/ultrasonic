package org.moire.ultrasonic.service

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class RxBus {
    companion object {

        var activeServerChangePublisher: PublishSubject<Int> =
            PublishSubject.create()
        var activeServerChangeObservable: Observable<Int> =
            activeServerChangePublisher.observeOn(AndroidSchedulers.mainThread())

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

        // Commands
        val dismissNowPlayingCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val dismissNowPlayingCommandObservable: Observable<Unit> =
            dismissNowPlayingCommandPublisher.observeOn(AndroidSchedulers.mainThread())
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
