package com.example.xray_gui_kotlin.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.xray_gui_kotlin.model.Profile
import com.example.xray_gui_kotlin.model.RoutingPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "xray_profiles")

data class ProfileSnapshot(
    val profiles: List<Profile>,
    val selectedProfileId: String?,
    val globalRoutingPreset: RoutingPreset = RoutingPreset.CN_DIRECT,
)

class ProfileStore(private val context: Context) {
    private val snapshotKey: Preferences.Key<String> = stringPreferencesKey("profile.snapshot.v1")

    fun observe(): Flow<ProfileSnapshot> {
        return context.dataStore.data.map { preferences ->
            decodeSnapshot(preferences[snapshotKey])
        }
    }

    suspend fun save(snapshot: ProfileSnapshot) {
        context.dataStore.edit { preferences ->
            preferences[snapshotKey] = encodeSnapshot(snapshot)
        }
    }

    private fun encodeSnapshot(snapshot: ProfileSnapshot): String {
        val json = JSONObject()
        json.put("selectedProfileId", snapshot.selectedProfileId)
        json.put("globalRoutingPreset", snapshot.globalRoutingPreset.name)
        val list = JSONArray()
        snapshot.profiles.forEach { profile ->
            list.put(JSONObject(profile.toMap()))
        }
        json.put("profiles", list)
        return json.toString()
    }

    private fun decodeSnapshot(raw: String?): ProfileSnapshot {
        if (raw.isNullOrBlank()) {
            return ProfileSnapshot(
                profiles = emptyList(),
                selectedProfileId = null,
                globalRoutingPreset = RoutingPreset.CN_DIRECT,
            )
        }

        return runCatching {
            val root = JSONObject(raw)
            val profilesArray = root.optJSONArray("profiles") ?: JSONArray()
            val profiles = mutableListOf<Profile>()
            for (index in 0 until profilesArray.length()) {
                val item = profilesArray.optJSONObject(index) ?: continue
                val map = item.toMap()
                val profile = Profile.fromMap(map)
                if (profile.id.isNotBlank() && profile.node.address.isNotBlank() && profile.node.id.isNotBlank()) {
                    profiles += profile
                }
            }
            val selected = root.optString("selectedProfileId").ifBlank { null }
            val selectedProfileId = if (profiles.any { it.id == selected }) selected else profiles.firstOrNull()?.id
            val presetName = root.optString("globalRoutingPreset")
            val globalRoutingPreset = RoutingPreset.entries.firstOrNull { it.name == presetName }
                ?: profiles.firstOrNull()?.routingPreset
                ?: RoutingPreset.CN_DIRECT
            ProfileSnapshot(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                globalRoutingPreset = globalRoutingPreset,
            )
        }.getOrElse {
            ProfileSnapshot(
                profiles = emptyList(),
                selectedProfileId = null,
                globalRoutingPreset = RoutingPreset.CN_DIRECT,
            )
        }
    }
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = when (val value = opt(key)) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return result
}

private fun JSONArray.toList(): List<Any?> {
    val result = mutableListOf<Any?>()
    for (index in 0 until length()) {
        result += when (val value = opt(index)) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return result
}
