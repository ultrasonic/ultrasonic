package org.moire.ultrasonic.featureflags

/**
 * Contains a list of new features/implementations that are not yet finished,
 * but possible to try it out.
 */
enum class Feature(
    val defaultValue: Boolean
) {
    /**
     * Enables new image downloader implementation.
     */
    NEW_IMAGE_DOWNLOADER(false),
    /**
     * Enables five star rating system.
     */
    FIVE_STAR_RATING(false)
}
