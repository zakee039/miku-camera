package com.example.mikucamera.data

import android.content.Context
import com.example.mikucamera.model.WatermarkPreset
import org.json.JSONArray

class PresetStore(context: Context) {
    private val preferences = context.getSharedPreferences("watermark_presets", Context.MODE_PRIVATE)

    /**
     * Adds a bundled preset exactly once per installation. A fresh install
     * selects it; upgrades preserve the user's current selection. The migration
     * marker also respects a later user deletion instead of recreating it.
     */
    fun installBundledPresetIfNeeded(preset: WatermarkPreset): WatermarkPreset? {
        if (preferences.getBoolean(KEY_BUNDLED_MIKU_INSTALLED, false)) return loadSelected()
        val hadPresets = loadAll().isNotEmpty()
        val selectedBefore = preferences.getString(KEY_SELECTED_PRESET_ID, null)
        val success = save(preset)
        if (!success) return loadSelected()
        val editor = preferences.edit().putBoolean(KEY_BUNDLED_MIKU_INSTALLED, true)
        if (!hadPresets && selectedBefore == null) {
            editor.putString(KEY_SELECTED_PRESET_ID, preset.id)
        }
        editor.commit()
        return loadSelected()
    }

    fun loadAll(): List<WatermarkPreset> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PRESETS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                add(WatermarkPreset.fromJson(array.getJSONObject(index)))
            }
        }
    }.getOrDefault(emptyList())

    fun save(preset: WatermarkPreset): Boolean {
        val presets = loadAll().toMutableList()
        val index = presets.indexOfFirst { it.id == preset.id }
        if (index >= 0) presets[index] = preset.copy() else presets.add(preset.copy())
        return write(presets)
    }

    fun loadSelected(): WatermarkPreset? {
        val selectedId = preferences.getString(KEY_SELECTED_PRESET_ID, null) ?: return null
        return loadAll().firstOrNull { it.id == selectedId }.also { preset ->
            if (preset == null) clearSelection()
        }
    }

    fun select(id: String) {
        preferences.edit().putString(KEY_SELECTED_PRESET_ID, id).apply()
    }

    fun clearSelection() {
        preferences.edit().remove(KEY_SELECTED_PRESET_ID).apply()
    }

    fun delete(id: String) {
        write(loadAll().filterNot { it.id == id })
        if (preferences.getString(KEY_SELECTED_PRESET_ID, null) == id) clearSelection()
    }

    private fun write(presets: List<WatermarkPreset>): Boolean {
        val array = JSONArray()
        presets.forEach { array.put(it.toJson()) }
        // Preset data is small; commit synchronously so the save button can
        // verify that the name and layout are durable before leaving editor mode.
        return preferences.edit().putString(KEY_PRESETS, array.toString()).commit()
    }

    private companion object {
        const val KEY_PRESETS = "presets"
        const val KEY_SELECTED_PRESET_ID = "selected_preset_id"
        const val KEY_BUNDLED_MIKU_INSTALLED = "bundled_miku_v1_installed"
    }
}
