package org.moire.ultrasonic.service

import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.observables.ConnectableObservable
import timber.log.Timber

object RxBus {
    var mediaSessionTokenPublisher: PublishSubject<MediaSessionCompat.Token> =
        PublishSubject.create()
    val mediaSessionTokenObservable: Observable<MediaSessionCompat.Token> =
        mediaSessionTokenPublisher.observeOn(AndroidSchedulers.mainThread())
            .replay(1)
            .autoConnect()
            .doOnEach { Timber.d("RxBus mediaSessionTokenPublisher onEach $it")}

    val mediaButtonEventPublisher: PublishSubject<KeyEvent> =
        PublishSubject.create()
    val mediaButtonEventObservable: Observable<KeyEvent> =
        mediaButtonEventPublisher.observeOn(AndroidSchedulers.mainThread())
            .doOnEach { Timber.d("RxBus mediaButtonEventPublisher onEach $it")}

    val themeChangedEventPublisher: PublishSubject<Unit> =
        PublishSubject.create()
    val themeChangedEventObservable: Observable<Unit> =
        themeChangedEventPublisher.observeOn(AndroidSchedulers.mainThread())
            .doOnEach { Timber.d("RxBus themeChangedEventPublisher onEach $it")}

    val dismissNowPlayingCommandPublisher: PublishSubject<Unit> =
        PublishSubject.create()
    val dismissNowPlayingCommandObservable: Observable<Unit> =
        dismissNowPlayingCommandPublisher.observeOn(AndroidSchedulers.mainThread())
            .doOnEach { Timber.d("RxBus dismissNowPlayingCommandPublisher onEach $it")}

    fun releaseMediaSessionToken() { mediaSessionTokenPublisher = PublishSubject.create() }

}


