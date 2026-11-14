package io.max.paperplay.core.update

import io.max.paperplay.core.integrations.HttpFetcher
import io.max.paperplay.core.integrations.HttpRequest
import io.max.paperplay.core.integrations.HttpResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REPO = "https://github.com/maxcodl/PaperPlay-compose/releases/download"

private fun release(tag: String, pre: Boolean, draft: Boolean = false, assets: String = "") =
    """{"tag_name":"$tag","draft":$draft,"prerelease":$pre,"body":"notes of $tag","html_url":"https://github.com/maxcodl/PaperPlay-compose/releases/tag/$tag","assets":[$assets]}"""

private fun asset(name: String, tag: String, size: Int = 10) = """{"name":"$name","size":$size,"browser_download_url":"$REPO/$tag/$name"}"""

private val feed = "[" + listOf(
    release("v1.4.0-beta.2", true), release("v1.3.1", false), release("v1.3.0", false), release("v1.5.0", false, draft = true),
    release("deps", true),
).joinToString(",") + "]"

class VersionTest {
    @Test fun parsesAndPrints() {
        assertEquals(Version(1, 3, 0), Version.parse("1.3.0"))
        assertEquals(Version(1, 3, 0, Stage.Beta, 7), Version.parse("v1.3.0-beta.7"))
        assertEquals("1.3.0-beta.7", Version(1, 3, 0, Stage.Beta, 7).toString())
        assertEquals(Version(1, 3, 0, Stage.Dev), Version.parse("1.3.0-dev"))
        assertNull(Version.parse("deps"))
        assertNull(Version.parse("1.3"))
    }

    @Test fun ordering() {
        val order = listOf("1.3.0-dev", "1.3.0-beta.2", "1.3.0-beta.10", "1.3.0", "1.3.1-beta.1", "1.3.1", "1.10.0").map { Version.parse(it)!! }
        assertEquals(order, order.shuffled().sorted())
    }
}

class ReleaseFeedTest {
    private val all = ReleaseFeed.parse(feed)

    @Test fun skipsDraftsAndTagsThatAreNotVersions() {
        assertEquals(listOf("v1.4.0-beta.2", "v1.3.1", "v1.3.0"), all.map { it.tag })
    }

    @Test fun stableNeverGetsAPrerelease() {
        assertEquals("v1.3.1", ReleaseFeed.newest(all, Version(1, 3, 0), beta = false, skipped = null)?.tag)
    }

    @Test fun betaChannelGetsTheNewestEvenIfItIsAPrerelease() {
        assertEquals("v1.4.0-beta.2", ReleaseFeed.newest(all, Version(1, 3, 0), beta = true, skipped = null)?.tag)
    }

    @Test fun onlyNewerThanInstalled() {
        assertNull(ReleaseFeed.newest(all, Version(1, 3, 1), beta = false, skipped = null))
        assertNull(ReleaseFeed.newest(all, Version(1, 4, 0, Stage.Beta, 2), beta = true, skipped = null))
        assertEquals("v1.4.0-beta.2", ReleaseFeed.newest(all, Version(1, 4, 0, Stage.Beta, 1), beta = true, skipped = null)?.tag)
    }

    @Test fun aBetaUpgradesToItsStableRelease() {
        assertEquals("v1.3.1", ReleaseFeed.newest(all, Version(1, 3, 1, Stage.Beta, 4), beta = false, skipped = null)?.tag)
    }

    @Test fun aSkippedReleaseIsNotOfferedButANewerOneIs() {
        assertEquals("v1.3.0", ReleaseFeed.newest(all, Version(1, 2, 0), beta = false, skipped = "v1.3.1")?.tag)
        assertNull(ReleaseFeed.newest(all, Version(1, 3, 0), beta = false, skipped = "v1.3.1"))
    }

    @Test fun picksTheAssetForThePlatform() {
        val r = ReleaseFeed.parse(
            "[" + release(
                "v1.3.0", false,
                assets = listOf("PaperPlay-1.3.0-android-release.apk", "PaperPlay-1.3.0-windows-x64-setup.exe", "PaperPlay-1.3.0-linux-x64.tar.gz", "SHA256SUMS.txt")
                    .joinToString(",") { asset(it, "v1.3.0") },
            ) + "]",
        ).single()
        assertEquals("PaperPlay-1.3.0-android-release.apk", ReleaseFeed.assetFor(r, UpdatePlatform.Android)?.name)
        assertEquals("PaperPlay-1.3.0-windows-x64-setup.exe", ReleaseFeed.assetFor(r, UpdatePlatform.Windows)?.name)
        assertNull(ReleaseFeed.assetFor(r, UpdatePlatform.Linux))
    }

    @Test fun readsChecksumLists() {
        val hex = "a".repeat(64)
        val sums = ReleaseFeed.parseChecksums("$hex  one.apk\n${"B".repeat(64)} *two.exe\nnot a line\n")
        assertEquals(hex, sums["one.apk"])
        assertEquals("b".repeat(64), sums["two.exe"])
        assertEquals(2, sums.size)
    }

    @Test fun onlyThisProjectsFilesAreTrusted() {
        assertTrue(ReleaseFeed.isTrusted("$REPO/v1.3.0/x.apk"))
        assertTrue(ReleaseFeed.isTrusted("https://objects.githubusercontent.com/abc"))
        assertFalse(ReleaseFeed.isTrusted("https://github.com/someone/else/releases/download/v1/x.apk"))
        assertFalse(ReleaseFeed.isTrusted("http://github.com/maxcodl/PaperPlay-compose/x"))
    }
}

class UpdateServiceTest {
    private val hex = "c".repeat(64)
    private val apk = "PaperPlay-1.3.1-android-release.apk"
    private val json = "[" + release("v1.3.1", false, assets = listOf(asset(apk, "v1.3.1"), asset("SHA256SUMS.txt", "v1.3.1")).joinToString(",")) + "]"

    private class Fake(val feed: String, val sums: String) : HttpFetcher {
        var feedCalls = 0
        override suspend fun send(request: HttpRequest) =
            if (request.url.contains("api.github.com")) { feedCalls++; HttpResponse(200, emptyMap(), feed.encodeToByteArray()) }
            else HttpResponse(200, emptyMap(), sums.encodeToByteArray())
    }

    private class Installer(val sha: String) : UpdateInstaller {
        var deleted: String? = null
        var installed: String? = null
        override suspend fun download(url: String, name: String, onProgress: (Float) -> Unit): Downloaded { onProgress(1f); return Downloaded("/cache/$name", sha) }
        override suspend fun install(path: String): InstallBlocked? { installed = path; return null }
        override fun delete(path: String) { deleted = path }
    }

    private fun service(http: HttpFetcher, installer: UpdateInstaller?, store: MutableMap<String, String> = mutableMapOf(), now: () -> Long = { 10L * 24 * 3600_000 }) =
        UpdateService(http, installer, UpdatePrefs({ store[it] }, { k, v -> store[k] = v }), UpdatePlatform.Android, Version(1, 3, 0), now)

    @Test fun findsAnUpdateAndDownloadsItAfterCheckingTheChecksum() = runBlocking<Unit> {
        val inst = Installer(hex)
        val s = service(Fake(json, "$hex  $apk\n"), inst)
        s.check()
        assertIs<UpdateState.Available>(s.state.value)
        s.download()
        val ready = assertIs<UpdateState.Ready>(s.state.value)
        assertEquals("/cache/$apk", ready.path)
        assertNull(s.install())
        assertEquals("/cache/$apk", inst.installed)
    }

    @Test fun aWrongChecksumDeletesTheFileAndFails() = runBlocking<Unit> {
        val inst = Installer("d".repeat(64))
        val s = service(Fake(json, "$hex  $apk\n"), inst)
        s.check()
        s.download()
        assertIs<UpdateState.Failed>(s.state.value)
        assertEquals("/cache/$apk", inst.deleted)
        assertNull(inst.installed)
    }

    @Test fun upToDateWhenNothingIsNewer() = runBlocking<Unit> {
        val s = service(Fake("[]", ""), null)
        s.check()
        assertIs<UpdateState.UpToDate>(s.state.value)
    }

    @Test fun skippingHidesTheReleaseOnTheNextCheck() = runBlocking<Unit> {
        val store = mutableMapOf<String, String>()
        val s = service(Fake(json, ""), null, store)
        s.check()
        s.skip()
        s.check()
        assertIs<UpdateState.UpToDate>(s.state.value)
        assertEquals("v1.3.1", store["update_skipped"])
    }

    @Test fun theDailyCheckWaitsADay() = runBlocking<Unit> {
        var t = 10L * 24 * 3600_000
        val store = mutableMapOf<String, String>()
        val s = service(Fake("[]", ""), null, store) { t }
        assertTrue(s.autoCheckDue())
        s.check()
        assertFalse(s.autoCheckDue())
        t += 25L * 3600_000
        assertTrue(s.autoCheckDue())
        store["update_auto"] = "false"
        assertFalse(s.autoCheckDue())
    }
}
