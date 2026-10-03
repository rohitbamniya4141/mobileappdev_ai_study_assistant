package com.studyassistant.app.util

import android.content.Context
import com.studyassistant.app.R
import com.studyassistant.app.data.api.StudyAssistantApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * Emits Unit whenever the server returns 401 Unauthorized.
     * The NavGraph observes this flow to redirect the user to the Login screen
     * and clear their stored token.
     */
    private val _authErrorFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authErrorFlow = _authErrorFlow.asSharedFlow()

    private var retrofit: Retrofit? = null

    /**
     * Must be called once from [com.studyassistant.app.StudyAssistantApp.onCreate].
     *
     * @param sessionManager used by the auth interceptor to attach the Bearer token.
     * @param context used to read the base URL from strings.xml so it can be changed
     *   without recompiling Kotlin code.
     */
    fun initialize(sessionManager: SessionManager, context: Context) {
        val baseUrl = context.getString(R.string.api_base_url)

        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { sessionManager.getToken() }
            val requestBuilder = chain.request().newBuilder()

            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            val response = chain.proceed(requestBuilder.build())

            if (response.code == 401) {
                // Signal all observers (NavGraph) to redirect to Login
                _authErrorFlow.tryEmit(Unit)
            }

            response
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // RAG responses can take a while
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** The configured [StudyAssistantApi] instance. Throws if [initialize] was not called. */
    val api: StudyAssistantApi
        get() = retrofit?.create(StudyAssistantApi::class.java)
            ?: throw IllegalStateException("NetworkModule.initialize() must be called before using the API.")
}
