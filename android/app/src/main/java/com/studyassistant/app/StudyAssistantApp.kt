package com.studyassistant.app

import android.app.Application
import com.studyassistant.app.util.NetworkModule
import com.studyassistant.app.util.SessionManager

class StudyAssistantApp : Application() {
    lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        // Pass context so NetworkModule can read the base URL from strings.xml
        NetworkModule.initialize(sessionManager, this)
    }
}

