package com.gracefultools.astrid.platform

import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.request.Options
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Avatars, agent photos and list images are https URLs. Coil 3 fetches those only when a network
 * module is on the classpath; without one every `AsyncImage` of a URL fails, quietly, to its
 * placeholder or initials (AATD-92).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ImageLoadingTest {
    @Test
    fun `AATD-92 the image loader can fetch an https avatar`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val loader = ImageLoader(context)
        val options = Options(context)
        val data = loader.components.map("https://astrid.cc/images/avatar.png", options)
        assertNotNull("no fetcher for an https URL: is a Coil network module a dependency?", loader.components.newFetcher(data, options, loader))
    }
}
