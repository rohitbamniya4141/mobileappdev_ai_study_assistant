package com.studyassistant.app.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.studyassistant.app.util.NetworkModule
import com.studyassistant.app.util.SessionManager
import kotlinx.coroutines.delay

/**
 * Splash screen that:
 * 1. Shows an animated logo while checking authentication status.
 * 2. If a token exists, calls GET /api/auth/me to validate it.
 *    - Valid token → [onNavigateToHome]
 *    - Invalid/expired token → clears stored token → [onNavigateToLogin]
 * 3. If no token exists → [onNavigateToLogin].
 */
@Composable
fun SplashScreen(
    sessionManager: SessionManager,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        // Animate in
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, delayMillis = 100)
        )
        delay(600)

        // Check authentication
        val token = sessionManager.getToken()
        if (token == null) {
            onNavigateToLogin()
            return@LaunchedEffect
        }

        // Validate the stored token by calling /api/auth/me
        val isValid = try {
            val response = NetworkModule.api.getMe()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }

        if (isValid) {
            onNavigateToHome()
        } else {
            // Token is expired or invalid — clear it and redirect
            sessionManager.clearSession()
            onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = "Study Assistant Logo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale.value)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Study Assistant",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
