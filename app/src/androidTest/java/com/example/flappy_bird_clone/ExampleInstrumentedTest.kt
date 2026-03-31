package com.example.flappy_bird_clone

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun appContext_andResources_areConfigured() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.example.flappy_bird_clone", appContext.packageName)
        assertEquals("flappy_bird_clone", appContext.getString(R.string.app_name))

        val groundResId = appContext.resources.getIdentifier(
            "ground_sprite",
            "drawable",
            appContext.packageName,
        )
        assertTrue("ground_sprite drawable should exist", groundResId != 0)
    }
}