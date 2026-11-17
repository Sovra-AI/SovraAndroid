package ai.sovra.remote.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Camera frames as CameraX delivers them: a Y plane, each row padded past the image width. */
class ScannerTest {
    private val link = javaClass.classLoader!!.getResource("fixtures/pairing-link.txt")!!.readText().trim()

    /** A grey frame with the QR code drawn in its middle, slightly dimmed like a photographed screen. */
    private fun frame(text: String?, width: Int = 1280, height: Int = 720, padding: Int = 64): Pair<ByteArray, Int> {
        val stride = width + padding
        val bytes = ByteArray(stride * height) { 120.toByte() }
        if (text != null) {
            val size = 600
            val code = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val left = (width - size) / 2
            val top = (height - size) / 2
            for (y in 0 until size) for (x in 0 until size) {
                bytes[(top + y) * stride + left + x] = (if (code[x, y]) 30 else 220).toByte()
            }
        }
        return bytes to stride
    }

    @Test
    fun readsTheDesktopsPairingLinkFromACameraFrame() {
        val (bytes, stride) = frame(link)
        assertEquals(link, readPairingCode(bytes, stride, 1280, 720))
    }

    @Test
    fun ignoresCodesThatAreNotSovraPairingLinks() {
        val (bytes, stride) = frame("https://example.com/menu")
        assertNull(readPairingCode(bytes, stride, 1280, 720))
    }

    @Test
    fun ignoresFramesWithoutACode() {
        val (bytes, stride) = frame(null)
        assertNull(readPairingCode(bytes, stride, 1280, 720))
    }
}
