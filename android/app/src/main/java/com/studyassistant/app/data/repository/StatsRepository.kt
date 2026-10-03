package com.studyassistant.app.data.repository

import com.studyassistant.app.data.api.StudyAssistantApi
import com.studyassistant.app.data.model.FAQItem
import com.studyassistant.app.data.model.StatsResponse
import com.studyassistant.app.util.SessionManager
import com.studyassistant.app.util.parseError

class StatsRepository(
    private val api: StudyAssistantApi,
    private val sessionManager: SessionManager
) {
    suspend fun getStats(): Result<StatsResponse> {
        return try {
            val response = api.getStats()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFaqs(): Result<List<FAQItem>> {
        return try {
            val response = api.getFaqs()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
