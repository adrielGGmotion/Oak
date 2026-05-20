package com.oak.app

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class OakDocumentsProvider : DocumentsProvider() {

    private val baseDir: File
        get() = File(Environment.getExternalStorageDirectory(), "Oak")

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_ROOT_PROJECTION
        val c = MatrixCursor(cols)

        val root = baseDir
        val modelDir = File(root, "models")
        val modelCount = if (modelDir.isDirectory)
            modelDir.listFiles()?.count { it.isFile && !it.name.startsWith('.') } ?: 0
        else 0
        val totalItems = root.listFiles()?.count { !it.name.startsWith('.') } ?: 0

        val summary = buildString {
            append("$totalItems folders")
            if (modelCount > 0) append(", $modelCount models")
        }

        c.addRow(
            arrayOf(
                ROOT_ID,
                root.absolutePath,
                "Oak",
                summary,
                FLAG_ROOT,
                root.totalSpace,
            )
        )
        return c
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val c = MatrixCursor(cols)
        val parent = File(parentDocumentId)
        if (!parent.isDirectory || !isUnderOak(parent)) return c

        parent.listFiles()
            ?.filter { !it.name.startsWith('.') }
            ?.sortedBy { it.name }
            ?.forEach { c.addRow(documentRow(it)) }

        return c
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val c = MatrixCursor(cols)
        val file = File(documentId)
        if (file.exists() && isUnderOak(file)) {
            c.addRow(documentRow(file))
        }
        return c
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor? {
        val file = File(documentId)
        if (!file.isFile || !file.canRead() || !isUnderOak(file)) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getDocumentType(documentId: String): String? {
        val file = File(documentId)
        if (!file.exists() || !isUnderOak(file)) return null
        return if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
        else getMimeType(file.name)
    }

    override fun getDocumentMetadata(documentId: String): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val file = File(documentId)
        if (!file.exists() || !isUnderOak(file)) return null
        return Bundle().apply {
            putLong("android:lastModified", file.lastModified())
            if (file.isFile) {
                putLong("android:size", file.length())
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return File(documentId).absolutePath.startsWith(
            File(parentDocumentId).absolutePath + File.separator
        )
    }

    private fun documentRow(file: File): Array<Any?> = arrayOf(
        file.absolutePath,
        file.name,
        if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else getMimeType(file.name),
        if (file.isFile) file.length() else null,
        file.lastModified(),
        if (file.isFile) DocumentsContract.Document.FLAG_SUPPORTS_METADATA else 0,
    )

    private fun getMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "")
        if (ext.isBlank()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: "application/octet-stream"
    }

    private fun isUnderOak(file: File): Boolean {
        return file.absolutePath == baseDir.absolutePath ||
            file.absolutePath.startsWith(baseDir.absolutePath + File.separator)
    }

    companion object {
        private const val ROOT_ID = "Oak"
        private const val FLAG_ROOT = DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
            DocumentsContract.Root.FLAG_SUPPORTS_RECENTS

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
