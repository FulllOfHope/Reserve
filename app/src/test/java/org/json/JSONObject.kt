package org.json

/** Minimal test-only stand-in for Android's org.json JSONObject. */
class JSONObject internal constructor(internal val values: Map<String, Any?>) {

    constructor(source: String) : this(JsonReader(source).readObject())

    fun optString(name: String): String = optString(name, "")

    fun optString(name: String, fallback: String): String = values[name] as? String ?: fallback

    fun getString(name: String): String = values[name] as? String ?: throw JSONException("Missing '$name'")

    fun optJSONArray(name: String): JSONArray? = values[name] as? JSONArray

    fun has(name: String): Boolean = values[name] != null
}
