package com.studyassistant.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.studyassistant.app.data.model.ApiError
import retrofit2.Response

fun Context.getFileName(uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun <T> Response<T>.parseError(): String {
    val errorBody = errorBody()?.string()
    return try {
        if (!errorBody.isNullOrEmpty()) {
            val apiError = Gson().fromJson(errorBody, ApiError::class.java)
            apiError.detail
        } else {
            "Unknown error occurred"
        }
    } catch (e: Exception) {
        "Unknown error occurred"
    }
}
