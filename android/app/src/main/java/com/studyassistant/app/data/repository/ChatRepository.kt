package com.studyassistant.app.data.repository

import com.studyassistant.app.data.api.StudyAssistantApi
import com.studyassistant.app.data.model.*
import com.studyassistant.app.util.SessionManager
import com.studyassistant.app.util.parseError

class ChatRepository(
    private val api: StudyAssistantApi,
    private val sessionManager: SessionManager
) {
    suspend fun getChatSessions(): Result<List<ChatSession>> {
        return try {
            val response = api.getChatSessions()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createChatSession(title: String): Result<ChatSession> {
        return try {
            val response = api.createChatSession(CreateSessionRequest(title))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChatSession(sessionId: String): Result<Unit> {
        return try {
            val response = api.deleteChatSession(sessionId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatHistory(sessionId: String): Result<List<ChatMessage>> {
        return try {
            val response = api.getChatHistory(sessionId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun query(request: QueryRequest): Result<QueryResponse> {
        return try {
            val response = api.query(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
