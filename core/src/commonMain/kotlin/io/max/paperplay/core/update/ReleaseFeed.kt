package io.max.paperplay.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

enum class UpdatePlatform { Android, Windows, Linux }

class ReleaseAsset(val name: String, val url: String, val size: Long)

class ReleaseInfo(
    val tag: String,
    val version: Version,
    val prerelease: Boolean,
    val notes: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
)

/** Reads GitHub's list of releases and picks the one to offer. */
object ReleaseFeed {
    const val CHECKSUMS = "SHA256SUMS.txt"

    /** Releases whose tag is a version; drafts and tags such as `deps` are left out. */
    fun parse(json: String): List<ReleaseInfo> {
        val list: JsonArray = Json.parseToJsonElement(json).jsonArray
        return list.mapNotNull { e ->
            val o = e.jsonObject
            if (o["draft"]?.jsonPrimitive?.boolean == true) return@mapNotNull null
            val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val version = Version.parse(tag) ?: return@mapNotNull null
            ReleaseInfo(
                tag, version, o["prerelease"]?.jsonPrimitive?.boolean == true,
                o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                o["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                o["assets"]?.jsonArray.orEmpty().map { a ->
                    val ao = a.jsonObject
                    ReleaseAsset(
                        ao.getValue("name").jsonPrimitive.content, ao.getValue("browser_download_url").jsonPrimitive.content,
                        ao["size"]?.jsonPrimitive?.long ?: 0L,
                    )
                },
            )
        }
    }

    /**
     * The newest release that is newer than [installed]. A pre-release counts only when [beta] is on, and the release the
     * person chose to skip ([skipped], a tag) never does.
     */
    fun newest(releases: List<ReleaseInfo>, installed: Version, beta: Boolean, skipped: String?): ReleaseInfo? =
        releases.filter { (beta || !it.prerelease) && it.tag != skipped && it.version > installed }.maxByOrNull { it.version }

    /** The file to download for this platform; null where there is no installer (Linux), so the release page is offered. */
    fun assetFor(release: ReleaseInfo, platform: UpdatePlatform): ReleaseAsset? {
        val suffix = when (platform) {
            UpdatePlatform.Android -> "-android-release.apk"
            UpdatePlatform.Windows -> "-windows-x64-setup.exe"
            UpdatePlatform.Linux -> return null
        }
        return release.assets.firstOrNull { it.name.endsWith(suffix) }
    }

    /** `sha256sum` output: one "<hex>  <file name>" per line. */
    fun parseChecksums(text: String): Map<String, String> = text.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size == 2 && parts[0].length == 64) parts[1].trimStart('*') to parts[0].lowercase() else null
    }.toMap()

    /** Downloads may only come from this repository's releases. */
    fun isTrusted(url: String) =
        url.startsWith("https://github.com/maxcodl/PaperPlay-compose/") || url.startsWith("https://objects.githubusercontent.com/")
}
