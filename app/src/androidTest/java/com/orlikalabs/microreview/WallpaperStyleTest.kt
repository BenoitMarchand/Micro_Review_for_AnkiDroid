package com.orlikalabs.microreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The background picture import, on the widget's picture file (saved before each test and put back after). */
@RunWith(AndroidJUnit4::class)
class WallpaperStyleTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val target = WallpaperStyle.Target.WIDGET
    private val picture = File(context.cacheDir, "exif_test.jpg")
    private var savedBackground: ByteArray? = null

    @Before
    fun setUp() {
        savedBackground = WallpaperStyle.backgroundFile(context, target).takeIf { it.exists() }?.readBytes()
    }

    @After
    fun tearDown() {
        picture.delete()
        val file = WallpaperStyle.backgroundFile(context, target)
        savedBackground?.let { file.writeBytes(it) } ?: file.delete()
    }

    @Test
    fun importBackground_turnsAPhotoStoredSidewaysUpright() {
        // A landscape picture, red on its left half, tagged "rotate 90°": it's a portrait photo, red at the top.
        writePicture(width = 400, height = 200, ExifInterface.ORIENTATION_ROTATE_90)

        assertTrue(WallpaperStyle.importBackground(context, Uri.fromFile(picture), target))

        val imported = WallpaperStyle.loadBackground(context, target)!!
        assertEquals(200, imported.width)
        assertEquals(400, imported.height)
        assertEquals(Color.RED, nearest(imported.getPixel(100, 50)))
        assertEquals(Color.BLUE, nearest(imported.getPixel(100, 350)))
    }

    @Test
    fun importBackground_keepsAPictureWithoutOrientation() {
        writePicture(width = 400, height = 200, orientation = null)

        assertTrue(WallpaperStyle.importBackground(context, Uri.fromFile(picture), target))

        val imported = WallpaperStyle.loadBackground(context, target)!!
        assertEquals(400, imported.width)
        assertEquals(200, imported.height)
        assertEquals(Color.RED, nearest(imported.getPixel(50, 100)))
    }

    /** A JPEG red on its left half and blue on its right half, with the EXIF [orientation] tag if not null. */
    private fun writePicture(width: Int, height: Int, orientation: Int?) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) for (y in 0 until height) bitmap.setPixel(x, y, if (x < width / 2) Color.RED else Color.BLUE)
        picture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        if (orientation != null) {
            ExifInterface(picture).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()) }.saveAttributes()
        }
    }

    /** Red or blue, whichever the JPEG pixel is closer to. */
    private fun nearest(pixel: Int) = if (Color.red(pixel) > Color.blue(pixel)) Color.RED else Color.BLUE
}
