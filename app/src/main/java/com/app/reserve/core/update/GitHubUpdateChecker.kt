package com.app.reserve.core.update

import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

/** The only release information Reserve keeps outside the ledger. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val releaseUrl: String
)

/**
 * The single network boundary in the app. Tests inject a fake so no test ever
 * opens a connection.
 */
fun interface ReleaseFetcher {
    fun fetch(url: String): String
}

/** One HTTPS GET, no SDKs, no telemetry, no identifiers. */
object JdkReleaseFetcher : ReleaseFetcher {
    private const val TIMEOUT_MS = 10_000

    override fun fetch(url: String): String {
        var connection: HttpsURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Reserve-Update-Checker")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                ""
            } else {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
            // Offline, blocked, rate-limited, bad JSON: all silent.
            ""
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}

/**
 * Reads the GitHub Releases "latest" endpoint and returns an [UpdateInfo] only
 * when the published tag is newer than [currentVersion]. All work is blocking
 * and must be called from an IO dispatcher; failures return null.
 */
class GitHubUpdateChecker(
    private val currentVersion: String,
    private val fetcher: ReleaseFetcher = JdkReleaseFetcher
) {

    fun checkForUpdate(): UpdateInfo? {
        val payload = runCatching { fetcher.fetch(RELEASES_API_URL) }.getOrNull() ?: return null
        val info = runCatching { parseRelease(payload) }.getOrNull() ?: return null
        return if (isNewer(info.versionName, currentVersion)) info else null
    }

    companion object {
        const val RELEASES_API_URL =
            "https://api.github.com/repos/FulllOfHope/Reserve/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/FulllOfHope/Reserve/releases/latest"

        // The update sheet must always leave room for the Update button, even
        // on a short screen. Release bodies link to full notes for detail.
        private const val MAX_NOTE_LINES = 4
        private const val MAX_NOTE_CHARS = 240
        private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\([^)]+\\)")

        /** Removes a leading v/V and any pre-release/build suffix. */
        private fun versionParts(version: String): Pair<List<Int>, String> {
            val trimmed = version.trim().removePrefix("v").removePrefix("V")
            val cut = trimmed.indexOfFirst { it == '-' || it == '+' }
            val core = if (cut >= 0) trimmed.substring(0, cut) else trimmed
            val suffix = if (cut >= 0) trimmed.substring(cut + 1) else ""
            val numbers = core.split('.').map { segment ->
                segment.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
            return numbers to suffix
        }

        /** Numeric segment comparison: 1.10.0 > 1.9.0, and 1.3 == 1.3.0. */
        fun compareVersions(left: String, right: String): Int {
            val (leftParts, leftSuffix) = versionParts(left)
            val (rightParts, rightSuffix) = versionParts(right)
            for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
                val l = leftParts.getOrNull(index) ?: 0
                val r = rightParts.getOrNull(index) ?: 0
                if (l != r) return l.compareTo(r)
            }
            return when {
                leftSuffix == rightSuffix -> 0
                leftSuffix.isEmpty() -> 1 // a stable release is newer than its beta
                rightSuffix.isEmpty() -> -1
                else -> leftSuffix.compareTo(rightSuffix)
            }
        }

        fun isNewer(candidate: String, current: String): Boolean =
            compareVersions(candidate, current) > 0

        /** Turns markdown headings, bullets and links into plain readable lines. */
        fun formatReleaseNotes(markdown: String): String {
            if (markdown.isBlank()) return "No release notes provided."
            val plain = markdown.lineSequence()
                .map { line ->
                    line.trim()
                        .replace(MARKDOWN_LINK, "$1")
                        .replace(Regex("^#{1,6}\\s*"), "")
                        .replace(Regex("^\\d+[.)]\\s+"), "")
                        .replace(Regex("^[-*+>]\\s+"), "")
                        .replace("**", "")
                        .replace("`", "")
                        .trim()
                }
                .filter { it.isNotEmpty() }
                .take(MAX_NOTE_LINES)
                .joinToString("\n")
                .ifEmpty { "No release notes provided." }
            return if (plain.length > MAX_NOTE_CHARS) {
                plain.take(MAX_NOTE_CHARS).trimEnd() + "…"
            } else {
                plain
            }
        }

        /** Parses the release payload. Returns null when it is not usable. */
        fun parseRelease(payload: String): UpdateInfo? {
            if (payload.isBlank()) return null
            val root = JSONObject(payload)
            val tag = root.optString("tag_name").trim()
            if (tag.isEmpty()) return null

            val version = tag.removePrefix("v").removePrefix("V")
            val releaseUrl = root.optString("html_url").ifBlank { RELEASES_PAGE_URL }
            val apkUrl = findApkUrl(root.optJSONArray("assets")) ?: releaseUrl

            return UpdateInfo(
                versionName = version,
                notes = formatReleaseNotes(root.optString("body")),
                apkUrl = apkUrl,
                releaseUrl = releaseUrl
            )
        }

        private fun findApkUrl(assets: JSONArray?): String? {
            if (assets == null) return null
            var fallback: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url").ifBlank { continue }
                if (name.startsWith("Reserve-v", ignoreCase = true)) return url
                if (fallback == null) fallback = url
            }
            return fallback
        }
    }
}

/** Scheduling and repeat-notification rules, kept pure for unit tests. */
object UpdatePolicy {
    const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    fun isCheckDue(lastCheckAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        lastCheckAt <= 0L || now - lastCheckAt >= CHECK_INTERVAL_MS

    /**
     * [lastCheckAt] is the last check time captured *before* the current check,
     * so a fresh check is not mistaken for a recently notified one.
     */
    fun shouldNotify(
        update: UpdateInfo?,
        dismissedVersion: String,
        notifiedVersion: String?,
        lastCheckAt: Long,
        now: Long,
        ignoreTimeGate: Boolean = false
    ): Boolean {
        if (update == null) return false
        if (update.versionName.equals(dismissedVersion.trim(), ignoreCase = true)) return false
        if (ignoreTimeGate) return true
        if (update.versionName.equals(notifiedVersion, ignoreCase = true)) return false
        return isCheckDue(lastCheckAt, now)
    }
}
