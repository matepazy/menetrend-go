package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FleetPhotoCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun keepsOnlyTwelvePhotosAndEvictsLeastRecentlyViewedAcrossRecreation() {
        val directory = temporary.newFolder()
        val cache = FleetPhotoCache(directory)
        repeat(12) { index ->
            cache.write("photo-$index", byteArrayOf(index.toByte()))
            directory.listFiles()!!.forEach { it.setLastModified(1000L + index) }
        }
        assertNotNull(cache.read("photo-0"))
        cache.write("new-photo", byteArrayOf(99))
        assertEquals(12, directory.listFiles()!!.size)
        assertNotNull(FleetPhotoCache(directory).read("photo-0"))
        assertNotNull(FleetPhotoCache(directory).read("new-photo"))
        assertEquals(11, (0 until 12).count { cache.read("photo-$it") != null })
    }

    @Test fun totalByteBudgetWinsEvenBeforePhotoCountLimit() {
        val directory = temporary.newFolder()
        val cache = FleetPhotoCache(directory)
        repeat(10) { cache.write("photo-$it", ByteArray(FleetPhotoCache.MAX_PHOTO_BYTES)) }
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= FleetPhotoCache.MAX_BYTES)
        assertEquals(6, directory.listFiles()!!.size)
        assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun rejectsOversizedEntriesAndAllowsRecoveringFromMissingCache() {
        val cache = FleetPhotoCache(temporary.newFolder())
        assertNull(cache.read("missing"))
        assertThrows(IllegalArgumentException::class.java) {
            cache.write("oversized", ByteArray(FleetPhotoCache.MAX_PHOTO_BYTES + 1))
        }
        cache.write("valid", byteArrayOf(7))
        cache.remove("valid")
        assertNull(cache.read("valid"))
    }

    @Test fun acceptsOnlyDirectHttpsCommonsImages() {
        assertTrue(isFleetPhotoUrl("https://upload.wikimedia.org/wikipedia/commons/a/ab/Test.jpg"))
        assertTrue(isFleetPhotoUrl("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/Test.jpg/960px-Test.jpg"))
        listOf("http://upload.wikimedia.org/wikipedia/commons/a.jpg", "https://example.com/a.jpg",
            "https://upload.wikimedia.org.evil.test/wikipedia/commons/a.jpg",
            "https://user@upload.wikimedia.org/wikipedia/commons/a.jpg", "file:///tmp/a.jpg",
            "https://upload.wikimedia.org:8080/wikipedia/commons/a.jpg", "https://upload.wikimedia.org/other/a.jpg")
            .forEach { assertFalse(it, isFleetPhotoUrl(it)) }
    }
}
