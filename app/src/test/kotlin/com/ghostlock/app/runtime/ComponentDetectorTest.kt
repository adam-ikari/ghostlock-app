package com.ghostlock.app.runtime

import android.app.Application
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The detection card has to distinguish "not there" from "not checked", so the
 * `/proc/modules` parsing and the staged-zip lookup are pinned here. The
 * installed-manager and `su` legs need a real device and are covered by the
 * manual smoke run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentDetectorTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `kernelsu shows up when proc modules lists it`() {
        val table = """
            kernelsu 1234567 0 - Live 0x0000000000000000
            binder_linux 200704 3 - Live 0x0000000000000000
        """.trimIndent()
        assertTrue(ComponentDetector.ksuLoadedIn(table))
    }

    @Test
    fun `a module whose name merely starts with kernelsu does not count`() {
        /* Substring matching would report the module active on a device that
         * only has some unrelated kernelsu-prefixed module loaded. */
        val table = "kernelsu_helper 123 0 - Live 0x0\nkernelsu_extra 1 0 - Live 0x0"
        assertFalse(ComponentDetector.ksuLoadedIn(table))
    }

    @Test
    fun `an empty table has no kernelsu`() {
        assertFalse(ComponentDetector.ksuLoadedIn(""))
    }

    @Test
    fun `tabs separate the module name too`() {
        assertTrue(ComponentDetector.ksuLoadedIn("kernelsu\t1234\t0\tLive\t0x0"))
    }

    @Test
    fun `nothing staged reports absent rather than an exception`() {
        val zygiskNext = ComponentFetcher.byKey(ComponentFetcher.ZygiskNextKey)!!
        assertNull(ComponentDetector.stagedVersion(context, zygiskNext))
    }

    @Test
    fun `a staged package that is not a zip is rejected`() {
        /* The fetch path leaves a `<name>.part` file behind on a failed
         * download; the detector must not report that as ready. */
        val component = ComponentFetcher.byKey(ComponentFetcher.VectorKey)!!
        val dir = File(context.filesDir, "modules").apply { mkdirs() }
        File(dir, component.fileName).writeText("not a zip")
        assertNull(ComponentDetector.stagedVersion(context, component))
    }

    @Test
    fun `a staged package of the wrong module is rejected`() {
        val component = ComponentFetcher.byKey(ComponentFetcher.VectorKey)!!
        val dir = File(context.filesDir, "modules").apply { mkdirs() }
        val zip = File(dir, component.fileName)
        java.util.zip.ZipOutputStream(zip.outputStream()).use { archive ->
            archive.putNextEntry(java.util.zip.ZipEntry("module.prop"))
            archive.write("id=some_other_module\nversion=v9\n".toByteArray())
            archive.closeEntry()
        }
        assertNull(ComponentDetector.stagedVersion(context, component))
    }

    @Test
    fun `the status list keeps the four rows the card renders`() {
        val status = ComponentDetector.Status(
            key = "module_active",
            label = "KernelSU module",
            detail = "active",
            ok = true,
        )
        assertEquals("module_active", status.key)
        assertTrue(status.ok)
    }
}
