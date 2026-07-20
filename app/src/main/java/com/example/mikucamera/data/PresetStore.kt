package com.example.mikucamera.data

import android.content.Context
import com.example.mikucamera.model.WatermarkPreset
import org.json.JSONArray

class PresetStore(context: Context) {
    private val preferences = context.getSharedPreferences("watermark_presets", Context.MODE_PRIVATE)

    fun loadAll(): List<WatermarkPreset> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PRESETS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                add(WatermarkPreset.fromJson(array.getJSONObject(index)))
            }
        }
    }.getOrDefault(emptyList())

    fun save(preset: WatermarkPreset) {
        val presets = loadAll().toMutableList()
        val index = presets.indexOfFirst { it.id == preset.id }
        if (index >= 0) presets[index] = preset.copy() else presets.add(preset.copy())
        write(presets)
    }

    fun delete(id: String) = write(loadAll().filterNot { it.id == id })

    private fun write(presets: List<WatermarkPreset>) {
        val array = JSONArray()
        presets.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    private companion object {
        const val KEY_PRESETS = "presets"
    }
}
