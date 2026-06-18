package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

object PdfPrinterHelper {
    fun printAdapterToFile(
        adapter: PrintDocumentAdapter,
        outputFile: File,
        onComplete: (Boolean) -> Unit
    ) {
        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("id", "print", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val pfd = ParcelFileDescriptor.open(
            outputFile, 
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        )

        // Since PdfPrinterHelper compiles inside package 'android.print', it has full access 
        // to the package-private constructors of LayoutResultCallback and WriteResultCallback.
        // This is a robust standard framework subclassing technique.
        val layoutCallback = object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                val writeCallback = object : PrintDocumentAdapter.WriteResultCallback() {
                    override fun onWriteFinished(pages: Array<out PageRange>?) {
                        try {
                            pfd.close()
                            onComplete(true)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onComplete(false)
                        }
                    }

                    override fun onWriteFailed(error: CharSequence?) {
                        try { pfd.close() } catch (e: Exception) {}
                        onComplete(false)
                    }

                    override fun onWriteCancelled() {
                        try { pfd.close() } catch (e: Exception) {}
                        onComplete(false)
                    }
                }

                try {
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        pfd,
                        CancellationSignal(),
                        writeCallback
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    try { pfd.close() } catch (ex: Exception) {}
                    onComplete(false)
                }
            }

            override fun onLayoutFailed(error: CharSequence?) {
                try { pfd.close() } catch (e: Exception) {}
                onComplete(false)
            }

            override fun onLayoutCancelled() {
                try { pfd.close() } catch (e: Exception) {}
                onComplete(false)
            }
        }

        try {
            adapter.onLayout(
                null,
                printAttributes,
                null,
                layoutCallback,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try { pfd.close() } catch (ex: Exception) {}
            onComplete(false)
        }
    }
}
