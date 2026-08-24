package com.example

import com.example.math3d.UsdzParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsdzParserTest {

    @Test
    fun `test parsing USDA text with point3f and faceVertexIndices`() {
        val usdaContent = """
            #usda 1.0
            def Mesh "Pyramid" {
                point3f[] points = [(-1, 0, -1), (1, 0, -1), (1, 0, 1), (-1, 0, 1), (0, 1.5, 0)]
                int[] faceVertexCounts = [4, 3, 3, 3, 3]
                int[] faceVertexIndices = [0, 1, 2, 3, 0, 1, 4, 1, 2, 4, 2, 3, 4, 3, 0, 4]
            }
        """.trimIndent()

        val mesh = UsdzParser.parseUsdaText(usdaContent, "Test Pyramid")
        assertEquals("Test Pyramid", mesh.name)
        assertEquals(5, mesh.vertexCount)
        assertTrue(mesh.faceCount >= 5)
    }

    @Test
    fun `test parsing zipped USDZ stream containing USDA asset`() {
        val usdaContent = """
            #usda 1.0
            def Mesh "Cube" {
                point3f[] points = [
                    (-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                    (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)
                ]
                int[] faceVertexCounts = [3, 3, 3, 3]
                int[] faceVertexIndices = [0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7]
            }
        """.trimIndent()

        val byteOut = ByteArrayOutputStream()
        ZipOutputStream(byteOut).use { zos ->
            zos.putNextEntry(ZipEntry("model.usda"))
            zos.write(usdaContent.toByteArray())
            zos.closeEntry()
        }

        val mesh = UsdzParser.parseStream(byteOut.toByteArray().inputStream(), "cube_asset.usdz")
        assertEquals("cube_asset", mesh.name)
        assertEquals(8, mesh.vertexCount)
        assertEquals(4, mesh.faceCount)
    }
}
