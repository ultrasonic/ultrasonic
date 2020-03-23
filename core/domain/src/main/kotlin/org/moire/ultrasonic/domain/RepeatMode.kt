package org.moire.ultrasonic.domain

enum class RepeatMode {
    OFF {
        override operator fun next(): RepeatMode = ALL
    },
    ALL {
        override operator fun next(): RepeatMode = SINGLE
    },
    SINGLE {
        override operator fun next(): RepeatMode = OFF
    };

    abstract operator fun next(): RepeatMode
}
