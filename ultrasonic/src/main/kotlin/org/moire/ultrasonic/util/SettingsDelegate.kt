package org.moire.ultrasonic.util

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.moire.ultrasonic.app.UApp

/**
 * Yet another implementation of Shared Preferences using Delegated Properties
 *
 * Check out https://medium.com/@FrostRocketInc/delegated-shared-preferences-in-kotlin-45b82d6e52d0
 * for a detailed walkthrough.
 *
 * @author Matthew Groves
 */

abstract class SettingsDelegate<T> : ReadWriteProperty<Any, T> {
    protected val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(UApp.applicationContext())
    }
}

class StringSetting(private val key: String, private val defaultValue: String = "") :
    SettingsDelegate<String>() {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sharedPreferences.getString(key, defaultValue)!!

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) =
        sharedPreferences.edit { putString(key, value) }
}

class IntSetting(private val key: String, private val defaultValue: Int = 0) :
    SettingsDelegate<Int>() {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sharedPreferences.getInt(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) =
        sharedPreferences.edit { putInt(key, value) }
}

class StringIntSetting(private val key: String, private val defaultValue: String = "0") :
    SettingsDelegate<Int>() {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sharedPreferences.getString(key, defaultValue)!!.toInt()

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) =
        sharedPreferences.edit { putString(key, value.toString()) }
}

class LongSetting(private val key: String, private val defaultValue: Long = 0.toLong()) :
    SettingsDelegate<Long>() {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sharedPreferences.getLong(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) =
        sharedPreferences.edit { putLong(key, value) }
}

class FloatSetting(
    private val key: String,
    private val defaultValue: Float = 0.toFloat()
) : SettingsDelegate<Float>() {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sharedPreferences.getFloat(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Float) =
        sharedPreferences.edit { putFloat(key, value) }
}

class BooleanSetting(private val key: String, private val defaultValue: Boolean = false) :
    SettingsDelegate<Boolean>() {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sharedPreferences.getBoolean(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) =
        sharedPreferences.edit { putBoolean(key, value) }
}
