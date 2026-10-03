package com.studyassistant.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.studyassistant.app.data.api.StudyAssistantApi
import com.studyassistant.app.data.model.Document
import com.studyassistant.app.util.SessionManager
import com.studyassistant.app.util.parseError
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

/**
 * Allowed file types for document upload.
 *
 * We validate using BOTH the MIME type and the file extension because
 * Android document providers (Google Drive, Downloads, etc.) sometimes
 * return null or an unexpected MIME type for perfectly valid PDFs.
 *
 * Validation logic:
 *   1. Read MIME type from ContentResolver (may be null).
 *   2. Read display name / extension from OpenableColumns.
 *   3. Accept the file if EITHER the MIME type OR the extension matches.
 *   4. Reject anything that is neither PDF nor DOCX.
 */
private val PDF_MIME_TYPES = setOf(
    "application/pdf",
    "application/x-pdf",
    "application/acrobat",
    "application/vnd.pdf",
    "text/pdf",                          // rare but seen in the wild
    "application/octet-stream",          // generic binary — resolved via extension
)

private val DOCX_MIME_TYPES = setOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",                // some providers use this for .docx too
    "application/octet-stream",          // generic binary — resolved via extension
    "application/zip",                   // DOCX is a ZIP; some providers expose it this way
)

/** Returns the correct OkHttp media type string for the detected file type. */
private fun mediaTypeFor(fileType: String): String = when (fileType) {
    "pdf" -> "application/pdf"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    else -> "application/octet-stream"
}

/**
 * Resolves the display name and MIME type of [uri] from the ContentResolver.
 * Returns a pair of (displayName, mimeType) — either may be null if the provider
 * does not supply them.
 */
private fun Context.resolveFileInfo(uri: Uri): Pair<String?, String?> {
    // Try the MIME type from ContentResolver first.
    val providerMime: String? = contentResolver.getType(uri)

    // Resolve the display name from OpenableColumns.
    var displayName: String? = null
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) displayName = cursor.getString(idx)
            }
        }

    // If the provider didn't give a MIME type, try to derive it from the extension.
    val resolvedMime: String? = providerMime
        ?: displayName?.substringAfterLast('.', "")
            ?.lowercase()
            ?.let { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }

    return Pair(displayName, resolvedMime)
}

/**
 * Determines whether [uri] is a supported PDF or DOCX and returns its file type
 * string ("pdf" or "docx"), or null if the file is not supported.
 *
 * Validation uses MIME type OR extension — whichever is available — so that files
 * from providers that return null or generic MIME types are still accepted.
 */
private fun Context.detectFileType(uri: Uri): Triple<String?, String?, String?> {
    val (displayName, mimeType) = resolveFileInfo(uri)
    val extension = displayName?.substringAfterLast('.', "")?.lowercase()

    val fileType: String? = when {
        // Extension takes priority — reliable even when MIME is null/generic
        extension == "pdf"  -> "pdf"
        extension == "docx" -> "docx"
        // No extension — fall back to unambiguous MIME types only
        mimeType == "application/pdf" || mimeType == "application/x-pdf" -> "pdf"
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        else -> null
    }

    return Triple(displayName, mimeType, fileType)
}

class DocumentRepository(
    private val api: StudyAssistantApi,
    private val sessionManager: SessionManager,
    private val context: Context
) {
    suspend fun getDocuments(): Result<List<Document>> {
        return try {
            val response = api.getDocuments()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadDocument(uri: Uri): Result<Document> {
        return try {
            // ── Step 1: Detect file type robustly ────────────────────────────
            val (displayName, _, fileType) = context.detectFileType(uri)

            if (fileType == null) {
                return Result.failure(
                    Exception("Only PDF (.pdf) and Word (.docx) files can be uploaded.")
                )
            }

            val fileName = when {
                !displayName.isNullOrBlank() -> displayName
                fileType == "pdf" -> "upload.pdf"
                else -> "upload.docx"
            }

            // ── Step 2: Copy URI content to a temp file ───────────────────────
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot read the selected file. Please try again."))

            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$fileName")
            try {
                FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }

                // ── Step 3: Build the multipart part with the correct Content-Type ──
                // Sending the right MIME type is critical: the backend checks
                // file.content_type. We always use the canonical MIME, never
                // application/octet-stream for PDFs, so the backend accepts it.
                val contentType = mediaTypeFor(fileType).toMediaTypeOrNull()
                val requestBody = tempFile.asRequestBody(contentType)
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)

                // ── Step 4: Upload ────────────────────────────────────────────
                val response = api.uploadDocument(part)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception(response.parseError()))
                }
            } finally {
                // Always clean up the temp file
                tempFile.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(docId: String): Result<Unit> {
        return try {
            val response = api.deleteDocument(docId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
