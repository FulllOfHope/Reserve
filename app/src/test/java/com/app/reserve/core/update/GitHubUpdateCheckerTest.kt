package com.app.reserve.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    private class FakeFetcher(
        private val response: String,
        private val fails: Boolean = false
    ) : ReleaseFetcher {
        var requestedUrl: String? = null
            private set

        override fun fetch(url: String): String {
            requestedUrl = url
            if (fails) throw IllegalStateException("offline")
            return response
        }
    }

    private val releaseJson = """
        {
          "tag_name": "v1.4.0",
          "body": "## What's new\n- Fixed ledger reconciliation after a migration\n- **Quieter** notifications\n\n[Full changelog](https://example.com/changelog)",
          "html_url": "https://github.com/FulllOfHope/Reserve/releases/tag/v1.4.0",
          "assets": [
            {
              "name": "Reserve-debug.apk",
              "browser_download_url": "https://example.com/debug.apk"
            },
            {
              "name": "Reserve-v1.4.0.apk",
              "browser_download_url": "https://github.com/FulllOfHope/Reserve/releases/download/v1.4.0/Reserve-v1.4.0.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `version comparison is numeric and tolerant of v prefixes`() {
        assertTrue(GitHubUpdateChecker.compareVersions("1.3.0", "1.4.0") < 0)
        assertTrue(GitHubUpdateChecker.compareVersions("1.10.0", "1.9.0") > 0)
        assertEquals(0, GitHubUpdateChecker.compareVersions("v1.4.0", "1.4.0"))
        assertEquals(0, GitHubUpdateChecker.compareVersions("1.4.0", "1.4"))
        assertEquals(0, GitHubUpdateChecker.compareVersions("1.4.0", "1.4.0"))
        assertTrue(GitHubUpdateChecker.compareVersions("1.4.0-beta", "1.4.0") < 0)
        assertTrue(GitHubUpdateChecker.isNewer("v1.4.0", "1.3.0"))
    }

    @Test
    fun `parses release payload with markdown notes and preferred apk asset`() {
        val info = GitHubUpdateChecker.parseRelease(releaseJson)
        assertNotNull(info)
        assertEquals("1.4.0", info!!.versionName)
        assertEquals(
            "https://github.com/FulllOfHope/Reserve/releases/tag/v1.4.0",
            info.releaseUrl
        )
        assertEquals(
            "https://github.com/FulllOfHope/Reserve/releases/download/v1.4.0/Reserve-v1.4.0.apk",
            info.apkUrl
        )

        val lines = info.notes.lines()
        assertTrue(lines.contains("What's new"))
        assertTrue(lines.contains("Fixed ledger reconciliation after a migration"))
        assertTrue(lines.contains("Quieter notifications"))
        assertTrue(lines.contains("Full changelog"))
        assertTrue(lines.none { it.startsWith("#") || it.startsWith("- ") || it.contains("**") })
    }

    @Test
    fun `release notes are capped so the update button stays visible`() {
        val longNotes = (1..20).joinToString("\n") { "Line $it ${"x".repeat(40)}" }
        val notes = GitHubUpdateChecker.formatReleaseNotes(longNotes)
        assertEquals(4, notes.lines().size)
        assertTrue(notes.length <= 240)
    }

    @Test
    fun `checker uses only the releases endpoint and stays silent on failure`() {
        val fetcher = FakeFetcher(releaseJson)
        val info = GitHubUpdateChecker("1.3.0", fetcher).checkForUpdate()
        assertNotNull(info)
        assertEquals(GitHubUpdateChecker.RELEASES_API_URL, fetcher.requestedUrl)
        assertEquals(
            "https://api.github.com/repos/FulllOfHope/Reserve/releases/latest",
            fetcher.requestedUrl
        )

        assertNull(GitHubUpdateChecker("1.3.0", FakeFetcher("", fails = true)).checkForUpdate())
        assertNull(GitHubUpdateChecker("1.3.0", FakeFetcher("not json")).checkForUpdate())
    }

    @Test
    fun `equal or older tags are not reported as updates`() {
        assertNull(GitHubUpdateChecker("1.4.0", FakeFetcher(releaseJson)).checkForUpdate())
        assertNull(GitHubUpdateChecker("1.10.0", FakeFetcher(releaseJson)).checkForUpdate())
    }

    @Test
    fun `shouldNotify respects version dismissal dedupe and six hour gate`() {
        val now = 1_800_000_000_000L
        val update = UpdateInfo("1.4.0", "notes", "apk", "release")

        assertTrue(
            UpdatePolicy.shouldNotify(
                update = update,
                dismissedVersion = "",
                notifiedVersion = null,
                lastCheckAt = 0L,
                now = now
            )
        )
        assertTrue(
            UpdatePolicy.shouldNotify(
                update = update,
                dismissedVersion = "",
                notifiedVersion = "1.3.0",
                lastCheckAt = 0L,
                now = now
            )
        )
        assertFalse(
            UpdatePolicy.shouldNotify(
                update = update,
                dismissedVersion = "1.4.0",
                notifiedVersion = null,
                lastCheckAt = 0L,
                now = now
            )
        )
        assertFalse(
            UpdatePolicy.shouldNotify(
                update = update,
                dismissedVersion = "",
                notifiedVersion = "1.4.0",
                lastCheckAt = 0L,
                now = now
            )
        )
        assertFalse(
            UpdatePolicy.shouldNotify(
                update = update,
                dismissedVersion = "",
                notifiedVersion = null,
                lastCheckAt = now - 60 * 60 * 1000L,
                now = now
            )
        )
        assertTrue(
            UpdatePolicy.shouldNotify(
                update = update,
                dismissedVersion = "",
                notifiedVersion = null,
                lastCheckAt = now - UpdatePolicy.CHECK_INTERVAL_MS - 1L,
                now = now
            )
        )
        assertTrue(
            UpdatePolicy.shouldNotify(
                update = update,
                dismissedVersion = "",
                notifiedVersion = "1.4.0",
                lastCheckAt = now,
                now = now,
                ignoreTimeGate = true
            )
        )
        assertFalse(
            UpdatePolicy.shouldNotify(
                update = null,
                dismissedVersion = "",
                notifiedVersion = null,
                lastCheckAt = 0L,
                now = now
            )
        )
        assertTrue(UpdatePolicy.isCheckDue(0L, now))
        assertFalse(UpdatePolicy.isCheckDue(now - 60 * 60 * 1000L, now))
    }
}
