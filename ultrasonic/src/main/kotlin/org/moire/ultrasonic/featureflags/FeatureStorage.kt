package org.moire.ultrasonic.featureflags

import android.content.Context

private const val SP_NAME = "feature_flags"

/**
 * Provides storage for current feature flag state.
 */
class FeatureStorage(
    context: Context
) {
    private val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    /**
     * Get [feature] current enabled state.
     */
    fun isFeatureEnabled(feature: Feature): Boolean {
        return sp.getBoolean(feature.name, feature.defaultValue)
    }

    /**
     * Update [feature] enabled state to [isEnabled].
     */
    fun changeFeatureFlag(
        feature: Feature,
        isEnabled: Boolean
    ) {
        sp.edit().putBoolean(feature.name, isEnabled).apply()
    }
}
