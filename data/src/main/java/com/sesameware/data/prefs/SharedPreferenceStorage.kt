package com.sesameware.data.prefs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import com.google.gson.Gson
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface PreferenceStorage {
    var authToken: String?
    var pushToken: String?
    var pushTokenRegistered: String?
    var sentName: SentName?
    var phone: String?
    var notificationData: NotificationData
    var notifySoundUri: String?
    var addressOptions: AddressOptions
    var whereIsContractWarningSeen: Boolean
    var onboardingCompleted: Boolean
    var xDmApiRefresh: Boolean
    var providerId: String?
    var providerBaseUrl: String?
    var showCamerasOnMap: Boolean
}

/**
 * [PreferenceStorage] impl backed by [android.content.SharedPreferences].
 */
class SharedPreferenceStorage constructor(
    context: Context
) : PreferenceStorage {

    private var apiRefresh = false

    companion object {
        const val PREFS_NAME = "smart_yard"
        const val PREF_ONBOARDING = "pref_onboarding"
        const val PREF_AUTH_TOKEN = "PREF_AUTH_TOKEN"
        const val PREF_PUSH_TOKEN = "PREF_PUSH_TOKEN"
        const val PREF_PUSH_TOKEN_REGISTERED = "PREF_PUSH_TOKEN_REGISTERED"
        const val PREF_SENT_NAME = "PREF_SENT_NAME"
        const val PREF_PHONE = "PREF_PHONE"
        const val PREF_NOTIFICATION_DATA = "PREF_NOTIFICATION_DATA"
        const val PREF_NOTIFY_SOUND_URI = "PREF_NOTIFY_SOUND_URI"
        const val PREF_ADDRESS_OPTIONS = "PREF_ADDRESS_OPTIONS_2"
        const val PREF_WHERE_IS_CONTRACT_WARNING_SEEN = "PREF_WHERE_IS_CONTRACT_WARNING_SEEN"
        const val PREF_PROVIDER_ID = "PREF_PROVIDER_ID"
        const val PREF_PROVIDER_BASE_URL = "PREF_PROVIDER_BASE_URL"
        const val PREF_SHOW_CAMERAS_ON_MAP = "PREF_SHOW_CAMERAS_ON_MAP"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    override var onboardingCompleted by BooleanPreference(prefs, PREF_ONBOARDING, false)
    override var authToken by StringPreference(prefs, PREF_AUTH_TOKEN, null)
    override var pushToken by StringPreference(prefs, PREF_PUSH_TOKEN, null)
    override var pushTokenRegistered by StringPreference(prefs, PREF_PUSH_TOKEN_REGISTERED, null)
    override var sentName by SerializablePreferenceNullable(
        prefs,
        PREF_SENT_NAME,
        null,
        SentName::class.java
    )
    override var phone by StringPreference(prefs, PREF_PHONE, null)
    override var notificationData by SerializablePreference(
        prefs,
        PREF_NOTIFICATION_DATA,
        NotificationData(),
        NotificationData::class.java
    )
    override var notifySoundUri by StringPreference(prefs, PREF_NOTIFY_SOUND_URI, null)
    override var addressOptions by SerializablePreference(
        prefs,
        PREF_ADDRESS_OPTIONS,
        AddressOptions(),
        AddressOptions::class.java
    )
    override var whereIsContractWarningSeen by BooleanPreference(prefs, PREF_WHERE_IS_CONTRACT_WARNING_SEEN, false)
    override var xDmApiRefresh: Boolean
        get() = apiRefresh
        set(value) { apiRefresh = value }
    override var providerId by StringPreference(prefs, PREF_PROVIDER_ID, null)
    override var providerBaseUrl by StringPreference(prefs, PREF_PROVIDER_BASE_URL, null)
    override var showCamerasOnMap by BooleanPreference(prefs, PREF_SHOW_CAMERAS_ON_MAP, true)
}

class SerializablePreferenceNullable<T>(
    private val preferences: SharedPreferences,
    private val name: String,
    private val defaultValue: T?,
    private val tClass: Class<T>
) : ReadWriteProperty<Any, T?> {

    @WorkerThread
    override fun getValue(thisRef: Any, property: KProperty<*>): T? {
        val gson = Gson()
        val string = preferences.getString(name, gson.toJson(defaultValue))
        return gson.fromJson(string, tClass)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
        val gson = Gson()
        preferences.edit { putString(name, gson.toJson(value)) }
    }
}
class SerializablePreference<T>(
    private val preferences: SharedPreferences,
    private val name: String,
    private val defaultValue: T,
    private val tClass: Class<T>
) : ReadWriteProperty<Any, T> {

    @WorkerThread
    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        val gson = Gson()
        val string = preferences.getString(name, gson.toJson(defaultValue))
        return gson.fromJson(string, tClass)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        val gson = Gson()
        preferences.edit { putString(name, gson.toJson(value)) }
    }
}

class BooleanPreference(
    private val preferences: SharedPreferences,
    private val name: String,
    private val defaultValue: Boolean
) : ReadWriteProperty<Any, Boolean> {

    @WorkerThread
    override fun getValue(thisRef: Any, property: KProperty<*>): Boolean {
        return preferences.getBoolean(name, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
        preferences.edit { putBoolean(name, value) }
    }
}

class StringPreference(
    private val preferences: SharedPreferences,
    private val name: String,
    private val defaultValue: String?
) : ReadWriteProperty<Any, String?> {

    @WorkerThread
    override fun getValue(thisRef: Any, property: KProperty<*>): String? {
        return preferences.getString(name, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String?) {
        preferences.edit { putString(name, value) }
    }
}

class IntPreference(
    private val preferences: SharedPreferences,
    private val name: String,
    private val defaultValue: Int
) : ReadWriteProperty<Any, Int> {

    @WorkerThread
    override fun getValue(thisRef: Any, property: KProperty<*>): Int {
        return preferences.getInt(name, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) {
        preferences.edit { putInt(name, value) }
    }
}
