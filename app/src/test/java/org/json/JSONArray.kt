package org.json

/** Minimal test-only stand-in for Android's org.json JSONArray. */
class JSONArray internal constructor(internal val values: List<Any?>) {

    constructor(source: String) : this(JsonReader(source).readArray())

    fun length(): Int = values.size

    fun optJSONObject(index: Int): JSONObject? = values.getOrNull(index) as? JSONObject

    fun optString(index: Int, fallback: String = ""): String = values.getOrNull(index) as? String ?: fallback
}
