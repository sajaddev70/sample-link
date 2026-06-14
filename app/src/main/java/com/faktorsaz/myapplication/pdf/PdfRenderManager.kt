package com.faktorsaz.myapplication.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfRenderManager(private val file: File) {
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val mutex = Mutex()

    init {
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        parcelFileDescriptor?.let {
            pdfRenderer = PdfRenderer(it)
        }
    }

    val pageCount: Int
        get() = pdfRenderer?.pageCount ?: 0

    suspend fun renderPage(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                pdfRenderer?.let { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

                    renderer.openPage(pageIndex).use { page ->
                        // High resolution bitmap for better quality
                        val bitmap = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun close() {
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
    }
}
