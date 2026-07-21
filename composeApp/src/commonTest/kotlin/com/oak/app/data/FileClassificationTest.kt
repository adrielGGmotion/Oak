package com.oak.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileClassificationTest {

    @Test
    fun `classify image by mime type`() {
        assertEquals(FileCategory.IMAGE, classifyFile("image/png", null))
        assertEquals(FileCategory.IMAGE, classifyFile("image/jpeg", null))
        assertEquals(FileCategory.IMAGE, classifyFile("image/gif", null))
        assertEquals(FileCategory.IMAGE, classifyFile("image/webp", "foo.bin"))
    }

    @Test
    fun `classify pdf by mime type`() {
        assertEquals(FileCategory.PDF, classifyFile("application/pdf", null))
    }

    @Test
    fun `classify text by mime type`() {
        assertEquals(FileCategory.TEXT, classifyFile("text/plain", null))
        assertEquals(FileCategory.TEXT, classifyFile("text/html", null))
        assertEquals(FileCategory.TEXT, classifyFile("text/markdown", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/json", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/xml", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/javascript", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/x-yaml", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/yaml", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/x-sh", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/sql", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/graphql", null))
        assertEquals(FileCategory.TEXT, classifyFile("application/toml", null))
    }

    @Test
    fun `classify image by extension fallback`() {
        assertEquals(FileCategory.IMAGE, classifyFile(null, "photo.jpg"))
        assertEquals(FileCategory.IMAGE, classifyFile(null, "photo.jpeg"))
        assertEquals(FileCategory.IMAGE, classifyFile(null, "photo.png"))
        assertEquals(FileCategory.IMAGE, classifyFile(null, "photo.gif"))
        assertEquals(FileCategory.IMAGE, classifyFile(null, "photo.webp"))
        assertEquals(FileCategory.IMAGE, classifyFile("application/octet-stream", "photo.bmp"))
    }

    @Test
    fun `classify pdf by extension fallback`() {
        assertEquals(FileCategory.PDF, classifyFile(null, "doc.pdf"))
        assertEquals(FileCategory.PDF, classifyFile("application/octet-stream", "doc.pdf"))
    }

    @Test
    fun `classify text by extension fallback`() {
        assertEquals(FileCategory.TEXT, classifyFile(null, "readme.md"))
        assertEquals(FileCategory.TEXT, classifyFile(null, "main.kt"))
        assertEquals(FileCategory.TEXT, classifyFile(null, "index.html"))
        assertEquals(FileCategory.TEXT, classifyFile(null, "style.css"))
        assertEquals(FileCategory.TEXT, classifyFile(null, "app.js"))
        assertEquals(FileCategory.TEXT, classifyFile(null, "data.json"))
        assertEquals(FileCategory.TEXT, classifyFile(null, "build.gradle.kts"))
    }

    @Test
    fun `classify unsupported by default`() {
        assertEquals(FileCategory.UNSUPPORTED, classifyFile(null, null))
        assertEquals(FileCategory.UNSUPPORTED, classifyFile(null, "file.xyz"))
        assertEquals(FileCategory.UNSUPPORTED, classifyFile("application/octet-stream", null))
        assertEquals(FileCategory.UNSUPPORTED, classifyFile("audio/mpeg", "song.mp3"))
    }

    @Test
    fun `mime type takes precedence over extension`() {
        assertEquals(FileCategory.TEXT, classifyFile("text/plain", "image.png"))
        assertEquals(FileCategory.IMAGE, classifyFile("image/png", "file.txt"))
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertEquals(FileCategory.TEXT, classifyFile(null, "ReadMe.MD"))
        assertEquals(FileCategory.IMAGE, classifyFile(null, "Photo.JPG"))
        assertEquals(FileCategory.IMAGE, classifyFile(null, "Photo.PNG"))
    }

    @Test
    fun `supportedFileExtensions lists all known extensions`() {
        assertTrue(supportedFileExtensions.contains("jpg"))
        assertTrue(supportedFileExtensions.contains("kt"))
        assertTrue(supportedFileExtensions.contains("md"))
        assertTrue(supportedFileExtensions.contains("txt"))
        assertTrue(supportedFileExtensions.contains("svg"))
    }
}
