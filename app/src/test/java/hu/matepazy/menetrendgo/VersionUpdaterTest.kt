package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUpdaterTest {
    @Test
    fun selectsLatestVersionRegardlessOfApiOrder() {
        val latest = release("v1.5.4")
        val releases = listOf(release("v1.5.3"), latest, release("v1.5.2"))
        val result = check(releases, current = "1.5.2") as UpdateState.UpdateAvailable
        assertEquals(latest.tag_name, result.version)
        assertEquals(latest.body, result.notes)
        assertEquals(latest.assets.single().browser_download_url, result.downloadUrl)
        assertEquals(result, check(releases.reversed(), current = "1.5.2"))
    }

    @Test
    fun betaChannelPrefersStableForSameVersionAndBetaOverOlderStable() {
        val releases = listOf(release("v1.5.5-beta1", prerelease = true), release("v1.5.4"))
        assertEquals("v1.5.5-beta1", (check(releases, beta = true) as UpdateState.UpdateAvailable).version)
        assertEquals("v1.5.5", (check(releases + release("v1.5.5"), beta = true) as UpdateState.UpdateAvailable).version)
    }

    @Test
    fun betaChannelSelectsHighestBetaNumber() {
        val releases = listOf(2, 10, 1).map { release("v1.5.5-beta$it", prerelease = true) }
        assertEquals("v1.5.5-beta10", (check(releases, beta = true) as UpdateState.UpdateAvailable).version)
        assertEquals(UpdateState.Idle, check(releases, current = "1.5.5", beta = true))
        assertEquals(UpdateState.Idle, check(releases, current = "1.5.5-beta10", beta = true))
    }

    @Test
    fun releaseChannelUsesGitHubPrereleaseFlag() {
        val releases = listOf(release("v1.6.0", prerelease = true), release("v1.5.4"))
        assertEquals("v1.5.4", (check(releases) as UpdateState.UpdateAvailable).version)
        assertEquals(UpdateState.Idle, check(releases.take(1)))
        // The GitHub flag controls eligibility, even if the tag contains a beta suffix.
        assertEquals("v1.5.5-beta1", (check(listOf(release("v1.5.5-beta1"))) as UpdateState.UpdateAvailable).version)
    }

    @Test
    fun stableReleaseUpdatesInstalledBetaWithoutOfferingDowngrades() {
        assertEquals("v1.5.5", (check(listOf(release("v1.5.5")), current = "1.5.5-beta2") as UpdateState.UpdateAvailable).version)
        assertEquals(UpdateState.Idle, check(listOf(release("v1.5.4")), current = "1.5.5-beta2"))
        assertEquals(UpdateState.Idle, check(emptyList()))
        assertEquals(UpdateState.Idle, check(listOf(release("v1.5.4")), current = "1.5.4"))
    }

    @Test
    fun excludesDraftReleasesAndReportsMissingLatestApk() {
        assertEquals("v1.5.4", (check(listOf(release("v1.6.0").copy(draft = true), release("v1.5.4")), beta = true) as UpdateState.UpdateAvailable).version)
        assertTrue(check(listOf(release("v1.5.4").copy(assets = emptyList()), release("v1.5.3"))) is UpdateState.Error)
    }

    @Test
    fun prefersMenetrendGoApkAndRetainsLegacyFallback() {
        val target = release("v1.5.4")
        val otherApk = GitHubAsset("other.apk", "https://example.com/other.apk")
        val result = check(listOf(target.copy(assets = listOf(otherApk) + target.assets))) as UpdateState.UpdateAvailable
        assertEquals(target.assets.single().browser_download_url, result.downloadUrl)
        assertEquals(otherApk.browser_download_url, (check(listOf(target.copy(assets = listOf(otherApk)))) as UpdateState.UpdateAvailable).downloadUrl)
    }

    @Test
    fun managedVersionsIncludesEveryBetaAndOnlyLatestStableWithoutNewerFilter() {
        val result = VersionUpdater.managedVersions(listOf(
            release("v1.5.3"), release("v1.5.4"),
            release("v1.6.0-beta2", prerelease = true),
            release("v1.6.0-beta10", prerelease = true),
            release("v1.6.0-beta1", prerelease = true),
            release("v1.7.0-beta1", prerelease = true).copy(draft = true),
        ), currentVersion = "1.6.0-beta2")
        assertEquals(listOf("v1.5.4", "v1.6.0-beta10", "v1.6.0-beta2", "v1.6.0-beta1"), result.map { it.version })
        assertTrue(result.all { it.isVersionSwitch })
        assertTrue(!result.first().isBeta)
        assertTrue(result.drop(1).all { it.isBeta })
    }

    @Test
    fun betaWarningsUseBothReleaseFlagAndVersionSuffix() {
        assertTrue(VersionUpdater.releaseUpdate(release("v1.6.0", prerelease = true))!!.isBeta)
        assertTrue(VersionUpdater.releaseUpdate(release("v1.6.0-beta1"))!!.isBeta)
        assertTrue(!VersionUpdater.releaseUpdate(release("v1.6.0"))!!.isBeta)
        assertTrue(VersionUpdater.managedVersions(listOf(release("v1.5.4").copy(assets = emptyList())), "1.5.4").isEmpty())
    }

    @Test
    fun stableInstallExcludesSameVersionBetasAndOlderReleases() {
        val releases = listOf(
            release("v1.5.5"),
            release("v1.5.5-beta1", prerelease = true),
            release("v1.6.0-beta1", prerelease = true),
            release("v1.6.0-beta10"),
            release("v1.6.0"),
            release("v1.6.1-beta1", prerelease = true),
        )
        val expected = listOf("v1.6.0", "v1.6.1-beta1")
        assertEquals(expected, VersionUpdater.managedVersions(releases, "1.6.0").map { it.version })
        assertEquals(expected, VersionUpdater.managedVersions(releases.reversed(), "v1.6.0+build.15").map { it.version })
    }

    @Test
    fun stableInstallFiltersOlderReleasesEvenBeforeInstalledReleaseIsPublished() {
        val releases = listOf(
            release("v1.5.5"),
            release("v1.6.0-beta5", prerelease = true),
            release("v1.7.0-beta1", prerelease = true),
        )
        assertEquals(listOf("v1.7.0-beta1"), VersionUpdater.managedVersions(releases, "1.6.0").map { it.version })
        assertTrue(VersionUpdater.managedVersions(releases.take(2), "1.6.0").isEmpty())
    }

    private fun check(releases: List<GitHubRelease>, current: String = "1.5.2", beta: Boolean = false) =
        VersionUpdater.updateStateForReleases(releases, current, beta)

    private fun release(version: String, prerelease: Boolean = false) = GitHubRelease(
        tag_name = version,
        prerelease = prerelease,
        name = version,
        body = "Notes for $version",
        assets = listOf(GitHubAsset("menetrendgo-$version.apk", "https://example.com/$version.apk")),
    )
}
