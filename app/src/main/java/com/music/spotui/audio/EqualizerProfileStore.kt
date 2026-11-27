package com.music.spotui.audio

import android.content.Context
import android.content.SharedPreferences
import com.music.spotui.data.preferences.setEqPreampDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomEqSlot(
    val id: String,
    val name: String,
    val bands: List<Float>, // 7 sliders in 0..100
    val preampDb: Float = 0f,
    val builtInPro: Boolean = false,
)

object EqualizerProfileStore {
    private const val PREF_NAME = "spotui_custom_eq_profiles"
    private const val KEY_CUSTOM_SLOTS = "custom_eq_slots_json"

    val proReferenceSlots: List<CustomEqSlot> = listOf(
        CustomEqSlot(
            id = "pro_harman",
            name = "Harman Target IEM",
            bands = listOf(64f, 60f, 48f, 54f, 66f, 60f, 56f),
            preampDb = -1.5f,
            builtInPro = true,
        ),
        CustomEqSlot(
            id = "pro_fps_footsteps",
            name = "FPS Footsteps Clarity",
            bands = listOf(38f, 44f, 52f, 68f, 78f, 74f, 64f),
            preampDb = -1.0f,
            builtInPro = true,
        ),
        CustomEqSlot(
            id = "pro_midnight_sub",
            name = "Midnight Sub-Bass",
            bands = listOf(84f, 78f, 50f, 56f, 54f, 46f, 44f),
            preampDb = -2.5f,
            builtInPro = true,
        ),
        CustomEqSlot(
            id = "pro_warm_vinyl",
            name = "Warm Vinyl Analog",
            bands = listOf(60f, 66f, 60f, 58f, 48f, 44f, 40f),
            preampDb = -0.5f,
            builtInPro = true,
        ),
    )

    private val _customSlots = MutableStateFlow<List<CustomEqSlot>>(emptyList())
    val customSlots: StateFlow<List<CustomEqSlot>> = _customSlots.asStateFlow()

    @Volatile
    private var hydrated = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hydrateIfNeeded(context: Context): List<CustomEqSlot> {
        if (hydrated) return _customSlots.value
        synchronized(this) {
            if (hydrated) return _customSlots.value
            val loaded = decode(prefs(context).getString(KEY_CUSTOM_SLOTS, null))
            _customSlots.value = loaded
            hydrated = true
            return loaded
        }
    }

    fun saveCustomSlot(
        context: Context,
        name: String,
        bands: List<Float>,
        preampDb: Float,
    ): CustomEqSlot {
        hydrateIfNeeded(context)
        val cleanName = name.trim().ifBlank { "Custom ${_customSlots.value.size + 1}" }
        val normalizedBands = if (bands.size == 7) {
            bands.map { it.coerceIn(0f, 100f) }
        } else {
            List(7) { idx -> bands.getOrElse(idx) { 50f }.coerceIn(0f, 100f) }
        }
        val slot = CustomEqSlot(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            bands = normalizedBands,
            preampDb = preampDb.coerceIn(-12f, 6f),
            builtInPro = false,
        )
        val updated = (_customSlots.value.filterNot { it.name.equals(cleanName, ignoreCase = true) } + slot)
            .takeLast(12)
        persist(context, updated)
        return slot
    }

    fun deleteCustomSlot(context: Context, id: String) {
        hydrateIfNeeded(context)
        val updated = _customSlots.value.filterNot { it.id == id }
        persist(context, updated)
    }

    fun applySlot(context: Context, slot: CustomEqSlot) {
        val arr = FloatArray(7) { idx -> slot.bands.getOrElse(idx) { 50f }.coerceIn(0f, 100f) }
        EqPresetCatalog.applyPreset(context, EqPresetCurve(slot.name, arr))
        setEqPreampDb(context, slot.preampDb)
    }

    fun exportSlotJson(slot: CustomEqSlot): String = JSONObject().apply {
        put("name", slot.name)
        put("preampDb", slot.preampDb)
        put("bands", JSONArray(slot.bands))
    }.toString()

    fun importSlotJson(context: Context, jsonRaw: String): CustomEqSlot? = runCatching {
        val obj = JSONObject(jsonRaw.trim())
        val name = obj.optString("name", "Imported Curve").ifBlank { "Imported Curve" }
        val preamp = obj.optDouble("preampDb", 0.0).toFloat()
        val arr = obj.optJSONArray("bands") ?: return null
        val bands = buildList {
            for (i in 0 until 7) {
                add(arr.optDouble(i, 50.0).toFloat().coerceIn(0f, 100f))
            }
        }
        saveCustomSlot(context, name, bands, preamp)
    }.getOrNull()

    private fun persist(context: Context, list: List<CustomEqSlot>) {
        val arr = JSONArray()
        list.forEach { slot ->
            arr.put(
                JSONObject().apply {
                    put("id", slot.id)
                    put("name", slot.name)
                    put("preampDb", slot.preampDb)
                    put("bands", JSONArray(slot.bands))
                },
            )
        }
        prefs(context).edit().putString(KEY_CUSTOM_SLOTS, arr.toString()).apply()
        _customSlots.value = list
    }

    private fun decode(raw: String?): List<CustomEqSlot> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    val bArr = obj.optJSONArray("bands") ?: continue
                    val bands = (0 until 7).map { idx -> bArr.optDouble(idx, 50.0).toFloat().coerceIn(0f, 100f) }
                    if (id.isNotBlank() && name.isNotBlank()) {
                        add(
                            CustomEqSlot(
                                id = id,
                                name = name,
                                bands = bands,
                                preampDb = obj.optDouble("preampDb", 0.0).toFloat().coerceIn(-12f, 6f),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
