package com.studyassistant.app.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Signup : Screen("signup")
    
    // Bottom Nav Screens
    object Home : Screen("home")
    object Chat : Screen("chat")
    object Documents : Screen("documents")
    
    // Secondary Screens
    object Faqs : Screen("faqs")
    object ChatDetail : Screen("chat_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "chat_detail/$sessionId"
    }
}
