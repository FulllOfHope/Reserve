package org.json

/** Minimal test-only stand-in for Android's org.json (which is stubbed in local unit tests). */
class JSONException(message: String) : RuntimeException(message)
