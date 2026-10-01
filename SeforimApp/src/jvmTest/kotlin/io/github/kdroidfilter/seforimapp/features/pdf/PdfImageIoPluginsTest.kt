package io.github.kdroidfilter.seforimapp.features.pdf

import com.github.jaiimageio.jpeg2000.impl.J2KImageReaderSpi
import org.apache.pdfbox.jbig2.JBIG2ImageReaderSpi
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfImageIoPluginsTest {
    @Test
    fun `required pdf image readers are registered explicitly`() {
        PdfImageIoPlugins.ensureRegistered()

        val providerTypeNames =
            IIORegistry
                .getDefaultInstance()
                .getServiceProviders(ImageReaderSpi::class.java, true)
                .asSequence()
                .map { it.javaClass.name }
                .toSet()

        assertTrue(JBIG2ImageReaderSpi::class.java.name in providerTypeNames)
        assertTrue(J2KImageReaderSpi::class.java.name in providerTypeNames)
    }
}
