package com.novelreader.app.data

import com.novelreader.app.core.FontFaceSpec
import org.json.JSONArray
import org.json.JSONObject

/** An imported TTF/OTF stored in app-private storage (filesDir/fonts/<id>.<ext>). */
data class CustomFont(
    val id: String,
    /** Shown in the UI, e.g. "Vazirmatn Bold". */
    val displayName: String,
    val family: String,
    val extension: String,
    val weight: Int,
    val italic: Boolean,
    val variableMin: Int?,
    val variableMax: Int?,
    val coversPersian: Boolean,
    val coversLatin: Boolean,
    val sizeBytes: Long,
    val sha256: String,
    /** Original file name, for display only. */
    val originalName: String,
) {
    val relativePath: String get() = "fonts/$id.$extension"

    fun toSpec() = FontFaceSpec(
        id = id,
        extension = extension,
        weight = weight,
        italic = italic,
        variableWeightRange = if (variableMin != null && variableMax != null) variableMin..variableMax else null,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", displayName).put("family", family).put("ext", extension)
        .put("weight", weight).put("italic", italic)
        .put("vmin", variableMin ?: JSONObject.NULL).put("vmax", variableMax ?: JSONObject.NULL)
        .put("fa", coversPersian).put("lat", coversLatin)
        .put("size", sizeBytes).put("sha", sha256).put("orig", originalName)

    companion object {
        fun fromJson(o: JSONObject): CustomFont? = try {
            CustomFont(
                id = o.getString("id"),
                displayName = o.getString("name"),
                family = o.optString("family", o.getString("name")),
                extension = o.getString("ext"),
                weight = o.optInt("weight", 400),
                italic = o.optBoolean("italic", false),
                variableMin = if (o.isNull("vmin")) null else o.getInt("vmin"),
                variableMax = if (o.isNull("vmax")) null else o.getInt("vmax"),
                coversPersian = o.optBoolean("fa", false),
                coversLatin = o.optBoolean("lat", true),
                sizeBytes = o.optLong("size", 0),
                sha256 = o.optString("sha", ""),
                originalName = o.optString("orig", ""),
            )
        } catch (_: Exception) {
            null
        }

        fun listToJson(list: List<CustomFont>): String = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<CustomFont> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val a = JSONArray(json)
                (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
