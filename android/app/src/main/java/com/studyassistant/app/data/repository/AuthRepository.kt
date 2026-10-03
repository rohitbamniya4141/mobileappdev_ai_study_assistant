package com.studyassistant.app.data.repository

import com.studyassistant.app.data.api.StudyAssistantApi
import com.studyassistant.app.data.model.LoginRequest
import com.studyassistant.app.data.model.RegisterRequest
import com.studyassistant.app.data.model.UserResponse
import com.studyassistant.app.util.SessionManager
import com.studyassistant.app.util.parseError

class AuthRepository(
    private val api: StudyAssistantApi,
    private val sessionManager: SessionManager
) {
    suspend fun login(request: LoginRequest): Result<UserResponse> {
        return try {
            val response = api.login(request)
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                sessionManager.saveSession(tokenResponse.access_token, tokenResponse.user)
                Result.success(tokenResponse.user)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(request: RegisterRequest): Result<UserResponse> {
        return try {
            val response = api.register(request)
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                sessionManager.saveSession(tokenResponse.access_token, tokenResponse.user)
                Result.success(tokenResponse.user)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMe(): Result<UserResponse> {
        return try {
            val response = api.getMe()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.parseError()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        sessionManager.clearSession()
    }
}
