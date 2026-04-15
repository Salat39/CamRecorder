package com.salat.preferences.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.salat.preferences.domain.DataStoreRepository
import com.salat.preferences.domain.entity.AnyPref
import com.salat.preferences.domain.entity.BoolPref
import com.salat.preferences.domain.entity.CameraDataStoreConfig
import com.salat.preferences.domain.entity.FloatPref
import com.salat.preferences.domain.entity.IntPref
import com.salat.preferences.domain.entity.LongPref
import com.salat.preferences.domain.entity.StringPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class DataStoreRepositoryImpl(private val context: Context) : DataStoreRepository {
    companion object {
        private const val CAMERA_ENABLED_KEY_PREFIX = "cam_enable_"

        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_data")
    }

    override val dataFlow = context.dataStore.data

    // -----------------------------------
    // Float
    // -----------------------------------

    override suspend fun save(pref: FloatPref, value: Float) {
        context.dataStore.edit { store -> store[floatPreferencesKey(pref.key)] = value }
    }

    override suspend fun load(pref: FloatPref): Float {
        return dataFlow.first()[floatPreferencesKey(pref.key)] ?: pref.default
    }

    override suspend fun exist(pref: FloatPref): Boolean {
        return dataFlow.first()[floatPreferencesKey(pref.key)] != null
    }

    override suspend fun remove(pref: FloatPref) {
        context.dataStore.edit { preferences -> preferences.remove(floatPreferencesKey(pref.key)) }
    }

    override fun getFloatPrefFlow(pref: FloatPref): Flow<Float> {
        var lastValue: Float? = null
        return dataFlow
            .map { preferences ->
                preferences[floatPreferencesKey(pref.key)] ?: pref.default
            }
            .filter { lastValue != it }
            .onEach { lastValue = it }
    }

    override fun getFloatPrefsFlow(vararg prefs: FloatPref): Flow<List<Float>> {
        val lastValues = mutableMapOf<String, Float?>()

        return dataFlow
            .map { preferences ->
                prefs.map { pref ->
                    val currentValue = preferences[floatPreferencesKey(pref.key)] ?: pref.default
                    lastValues[pref.key] = currentValue
                    currentValue
                }
            }
            .distinctUntilChanged()
    }

    // -----------------------------------
    // String
    // -----------------------------------

    override suspend fun save(pref: StringPref, value: String) {
        context.dataStore.edit { store -> store[stringPreferencesKey(pref.key)] = value }
    }

    override suspend fun load(pref: StringPref) = dataFlow.first()[stringPreferencesKey(pref.key)] ?: pref.default

    override suspend fun exist(pref: StringPref): Boolean = dataFlow.first()[stringPreferencesKey(pref.key)] != null

    override suspend fun remove(pref: StringPref) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(pref.key))
        }
    }

    override fun getStringPrefFlow(pref: StringPref): Flow<String> {
        var lastValue: String? = null
        return dataFlow
            .map { preferences ->
                preferences[stringPreferencesKey(pref.key)] ?: pref.default
            }
            .filter { lastValue != it }
            .onEach { lastValue = it }
    }

    override fun getStringPrefsFlow(vararg prefs: StringPref): Flow<List<String>> {
        val lastValues = mutableMapOf<String, String?>()

        return dataFlow
            .map { preferences ->
                // Create a list containing the current values of all provided BoolPref
                prefs.map { pref ->
                    val currentValue = preferences[stringPreferencesKey(pref.key)] ?: pref.default
                    lastValues[pref.key] = currentValue
                    currentValue
                }
            }
            .distinctUntilChanged() // Ensure the list is only emitted when any value changes
    }

    // -----------------------------------
    // Integer
    // -----------------------------------

    override suspend fun save(pref: IntPref, value: Int) {
        context.dataStore.edit { store -> store[intPreferencesKey(pref.key)] = value }
    }

    override suspend fun load(pref: IntPref) = dataFlow.first()[intPreferencesKey(pref.key)] ?: pref.default

    override suspend fun exist(pref: IntPref) = dataFlow.first()[intPreferencesKey(pref.key)] != null

    override suspend fun remove(pref: IntPref) {
        context.dataStore.edit { preferences ->
            preferences.remove(intPreferencesKey(pref.key))
        }
    }

    /**
     * Returns a Flow that emits the current value of the specified Integer preference
     * whenever it changes in the DataStore.
     *
     * @param pref The IntPref object representing the integer preference to monitor.
     * @return A Flow that emits the Integer value of the preference whenever it changes.
     */
    override fun getIntPrefFlow(pref: IntPref): Flow<Int> {
        var lastValue: Int? = null
        return dataFlow
            .map { preferences ->
                preferences[intPreferencesKey(pref.key)] ?: pref.default
            }
            .filter { lastValue != it }
            .onEach { lastValue = it }
    }

    // -----------------------------------
    // Long
    // -----------------------------------

    override suspend fun save(pref: LongPref, value: Long) {
        context.dataStore.edit { store -> store[longPreferencesKey(pref.key)] = value }
    }

    override suspend fun load(pref: LongPref) = dataFlow.first()[longPreferencesKey(pref.key)] ?: pref.default

    override suspend fun exist(pref: LongPref) = dataFlow.first()[longPreferencesKey(pref.key)] != null

    override suspend fun remove(pref: LongPref) {
        context.dataStore.edit { preferences ->
            preferences.remove(longPreferencesKey(pref.key))
        }
    }

    override fun getLongPrefFlow(pref: LongPref): Flow<Long> {
        var lastValue: Long? = null
        return dataFlow
            .map { preferences ->
                preferences[longPreferencesKey(pref.key)] ?: pref.default
            }
            .filter { lastValue != it }
            .onEach { lastValue = it }
    }

    override fun getLongPrefsFlow(vararg prefs: LongPref): Flow<List<Long>> {
        val lastValues = mutableMapOf<String, Long?>()

        return dataFlow
            .map { preferences ->
                prefs.map { pref ->
                    val currentValue = preferences[longPreferencesKey(pref.key)] ?: pref.default
                    lastValues[pref.key] = currentValue
                    currentValue
                }
            }
            .distinctUntilChanged()
    }

    // -----------------------------------
    // Boolean
    // -----------------------------------

    override suspend fun save(pref: BoolPref, value: Boolean) {
        context.dataStore.edit { store -> store[booleanPreferencesKey(pref.key)] = value }
    }

    override suspend fun load(pref: BoolPref) = dataFlow.first()[booleanPreferencesKey(pref.key)] ?: pref.default

    override suspend fun exist(pref: BoolPref) = dataFlow.first()[booleanPreferencesKey(pref.key)] != null

    override suspend fun remove(pref: BoolPref) {
        context.dataStore.edit { preferences ->
            preferences.remove(booleanPreferencesKey(pref.key))
        }
    }

    /**
     * Returns a Flow that emits the current value of the specified Boolean preference
     * whenever it changes in the DataStore.
     *
     * @param pref The BoolPref object representing the boolean preference to monitor.
     * @return A Flow that emits the Boolean value of the preference whenever it changes.
     */
    override fun getBooleanPrefFlow(pref: BoolPref): Flow<Boolean> {
        var lastValue: Boolean? = null
        return dataFlow
            .map { preferences ->
                preferences[booleanPreferencesKey(pref.key)] ?: pref.default
            }
            .filter { lastValue != it }
            .onEach { lastValue = it }
    }

    /**
     * Returns a Flow that emits a list of the current values of the specified Boolean preferences
     * whenever any of them changes in the DataStore.
     *
     * @param prefs A vararg of BoolPref objects representing the boolean preferences to monitor.
     * @return A Flow that emits a list of Boolean values of the preferences whenever any of them changes.
     */
    override fun getBooleanPrefsFlow(vararg prefs: BoolPref): Flow<List<Boolean>> {
        val lastValues = mutableMapOf<String, Boolean?>()

        return dataFlow
            .map { preferences ->
                // Create a list containing the current values of all provided BoolPref
                prefs.map { pref ->
                    val currentValue = preferences[booleanPreferencesKey(pref.key)] ?: pref.default
                    lastValues[pref.key] = currentValue
                    currentValue
                }
            }
            .distinctUntilChanged() // Ensure the list is only emitted when any value changes
    }

    override suspend fun clear() {
        context.dataStore.edit { preferences ->
            // Keys that should not be cleared
            val keysToKeep = emptySet<Preferences.Key<*>>()
            preferences.asMap().keys
                .filterNot { it in keysToKeep }
                .forEach { preferences.remove(it) }
        }
    }

    override fun getAnyPrefsFlow(vararg prefs: AnyPref): Flow<List<Any>> {
        val lastValues = mutableMapOf<String, Any?>()

        return dataFlow
            .map { preferences ->
                prefs.map { pref ->
                    when (pref) {
                        is BoolPref -> {
                            val currentValue = preferences[booleanPreferencesKey(pref.key)] ?: pref.default
                            lastValues[pref.key] = currentValue
                            currentValue
                        }

                        is IntPref -> {
                            val currentValue = preferences[intPreferencesKey(pref.key)] ?: pref.default
                            lastValues[pref.key] = currentValue
                            currentValue
                        }

                        is StringPref -> {
                            val currentValue = preferences[stringPreferencesKey(pref.key)] ?: pref.default
                            lastValues[pref.key] = currentValue
                            currentValue
                        }

                        is LongPref -> {
                            val currentValue = preferences[longPreferencesKey(pref.key)] ?: pref.default
                            lastValues[pref.key] = currentValue
                            currentValue
                        }

                        is FloatPref -> {
                            val currentValue = preferences[floatPreferencesKey(pref.key)] ?: pref.default
                            lastValues[pref.key] = currentValue
                            currentValue
                        }

                        else -> 0
                    }
                }
            }
            .distinctUntilChanged()
    }

    override fun getCameraRecordingConfigsFlow(
        cameraIds: List<String>,
        defaultFps: Int,
        minFps: Int,
        maxFps: Int,
        defaultOutputType: Int,
        defaultEnabled: Boolean,
    ): Flow<Map<String, CameraDataStoreConfig>> {
        if (cameraIds.isEmpty()) {
            return dataFlow
                .map { emptyMap<String, CameraDataStoreConfig>() }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

        return dataFlow
            .map { preferences ->
                cameraIds.associateWith { cameraId ->
                    CameraDataStoreConfig(
                        cameraId = cameraId,
                        enabled = preferences[booleanPreferencesKey(cameraEnabledKey(cameraId))] ?: defaultEnabled,
                        fps = (preferences[intPreferencesKey(cameraFpsKey(cameraId))] ?: defaultFps)
                            .coerceIn(minFps, maxFps),
                        outputType = preferences[intPreferencesKey(cameraOutputTypeKey(cameraId))] ?: defaultOutputType,
                    )
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override fun getCameraRecordingConfigsFlow(
        defaultConfigsByCameraId: Map<String, CameraDataStoreConfig>,
        minFps: Int,
        maxFps: Int,
    ): Flow<Map<String, CameraDataStoreConfig>> {
        if (defaultConfigsByCameraId.isEmpty()) {
            return dataFlow
                .map { emptyMap<String, CameraDataStoreConfig>() }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

        return dataFlow
            .map { preferences ->
                defaultConfigsByCameraId.mapValues { (cameraId, defaultConfig) ->
                    CameraDataStoreConfig(
                        cameraId = cameraId,
                        enabled = preferences[booleanPreferencesKey(cameraEnabledKey(cameraId))]
                            ?: defaultConfig.enabled,
                        fps = (preferences[intPreferencesKey(cameraFpsKey(cameraId))] ?: defaultConfig.fps)
                            .coerceIn(minFps, maxFps),
                        outputType = preferences[intPreferencesKey(cameraOutputTypeKey(cameraId))]
                            ?: defaultConfig.outputType,
                    )
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override fun getCameraEnabledValuesFlow(): Flow<List<Boolean>> {
        return dataFlow
            .map { preferences ->
                preferences.asMap()
                    .filterKeys { it.name.startsWith(CAMERA_ENABLED_KEY_PREFIX) }
                    .entries
                    .sortedBy { it.key.name }
                    .mapNotNull { (_, value) -> value as? Boolean }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    override suspend fun saveCameraRecordingConfig(config: CameraDataStoreConfig) {
        context.dataStore.edit { store ->
            store[booleanPreferencesKey(cameraEnabledKey(config.cameraId))] = config.enabled
            store[intPreferencesKey(cameraFpsKey(config.cameraId))] = config.fps
            store[intPreferencesKey(cameraOutputTypeKey(config.cameraId))] = config.outputType
        }
    }

    private fun cameraFpsKey(cameraId: String): String = "cam_fps_$cameraId"

    private fun cameraOutputTypeKey(cameraId: String): String = "cam_output_type_$cameraId"

    private fun cameraEnabledKey(cameraId: String): String = "${CAMERA_ENABLED_KEY_PREFIX}$cameraId"
}
