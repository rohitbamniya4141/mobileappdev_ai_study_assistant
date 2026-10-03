package com.studyassistant.app.data.api

import com.studyassistant.app.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface StudyAssistantApi {
    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<TokenResponse>

    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @GET("/api/auth/me")
    suspend fun getMe(): Response<UserResponse>

    @GET("/api/documents")
    suspend fun getDocuments(): Response<List<Document>>

    @Multipart
    @POST("/api/upload-document")
    suspend fun uploadDocument(@Part file: MultipartBody.Part): Response<Document>

    @DELETE("/api/documents/{doc_id}")
    suspend fun deleteDocument(@Path("doc_id") docId: String): Response<GenericMessageResponse>

    @GET("/api/chat-sessions")
    suspend fun getChatSessions(): Response<List<ChatSession>>

    @POST("/api/chat-sessions")
    suspend fun createChatSession(@Body request: CreateSessionRequest): Response<ChatSession>

    @DELETE("/api/chat-sessions/{session_id}")
    suspend fun deleteChatSession(@Path("session_id") sessionId: String): Response<GenericMessageResponse>

    @GET("/api/chat-history/{session_id}")
    suspend fun getChatHistory(@Path("session_id") sessionId: String): Response<List<ChatMessage>>

    @POST("/api/query")
    suspend fun query(@Body request: QueryRequest): Response<QueryResponse>

    @GET("/api/faqs")
    suspend fun getFaqs(): Response<List<FAQItem>>

    @GET("/api/stats")
    suspend fun getStats(): Response<StatsResponse>

    @GET("/health")
    suspend fun getHealth(): Response<HealthResponse>
}
