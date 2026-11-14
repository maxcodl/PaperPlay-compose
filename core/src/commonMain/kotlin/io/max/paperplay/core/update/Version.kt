package io.max.paperplay.core.update

enum class Stage { Dev, Beta, Stable }

/** 1.3.0, 1.3.0-beta.7 or 1.3.0-dev. A beta is older than its release, and a dev build older than a beta. */
data class Version(val major: Int, val minor: Int, val patch: Int, val stage: Stage = Stage.Stable, val beta: Int = 0) : Comparable<Version> {
    override fun compareTo(other: Version) =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch, Version::stage, Version::beta)

    override fun toString() = "$major.$minor.$patch" + when (stage) {
        Stage.Stable -> ""
        Stage.Beta -> "-beta.$beta"
        Stage.Dev -> "-dev"
    }

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+)|(-dev))?$""")

        /** Null when [text] is not a version (a tag such as "deps"). */
        fun parse(text: String): Version? {
            val m = pattern.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = m.groupValues.drop(1).take(3).map { it.toInt() }
            return when {
                m.groupValues[4].isNotEmpty() -> Version(major, minor, patch, Stage.Beta, m.groupValues[4].toInt())
                m.groupValues[5].isNotEmpty() -> Version(major, minor, patch, Stage.Dev)
                else -> Version(major, minor, patch)
            }
        }
    }
}
